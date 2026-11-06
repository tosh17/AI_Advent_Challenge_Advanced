import json
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import guards

UPSTREAM = "http://10.10.1.101:11434/v1/chat/completions"
MODEL = "qwen2.5:3b"
LIMIT = 10
WINDOW_SECONDS = 60


class Gateway:
    def __init__(self, upstream, limit=LIMIT, window=WINDOW_SECONDS, audit=None):
        self.upstream = upstream
        self.limit = limit
        self.window = window
        self.audit = audit if audit is not None else []
        self._hits = {}
        self._lock = threading.Lock()

    def handle(self, body, ip, mode, now=None):
        moment = time.time() if now is None else now
        if mode not in ("mask", "block"):
            return 400, _error("bad_mode", "Режим охраны: block или mask.")
        if not self._allow(ip, moment):
            event = self._log(ip, mode, "rate_limited", [], 0, 0, sent=False)
            return 429, _error(
                "rate_limit",
                f"Слишком много запросов. Лимит {self.limit} в минуту.",
                event,
            )
        messages = body.get("messages")
        if not isinstance(messages, list) or not messages:
            return 400, _error("bad_request", "Нужен непустой список messages.")
        found, changes, forwarded, matched = _inspect(messages)
        kinds = _kinds(found)
        if mode == "block" and kinds:
            event = self._log(
                ip,
                mode,
                "blocked",
                kinds,
                0,
                0,
                sent=False,
                stage="input",
                found=matched,
            )
            return 400, _error(
                "input_guard",
                "Запрос остановлен: во входящем тексте секрет. В модель ничего не ушло.",
                event,
            )
        outbound = _prepare(messages, mask=(mode == "mask"))
        try:
            completion = self.upstream(outbound, body.get("model") or MODEL)
        except TimeoutError:
            return 504, _error("upstream_timeout", "Модель не ответила вовремя.")
        except OSError as error:
            return 502, _error("upstream", f"Модель недоступна: {error.__class__.__name__}")
        answer = _answer_text(completion)
        output = guards.output_findings(answer)
        output_kinds = _kinds(output)
        hard = [kind for kind in output_kinds if kind in ("system_prompt", "suspicious_url", "suspicious_command")]
        secrets = [kind for kind in output_kinds if kind not in hard]
        prompt_tokens, completion_tokens = _usage(completion, outbound, answer)
        if secrets:
            answer, _, output_changes = guards.redact(answer)
            completion = _replace_answer(completion, answer)
            changes = changes + _arrows(output_changes)
        if hard:
            event = self._log(
                ip,
                mode,
                "blocked",
                output_kinds,
                prompt_tokens,
                completion_tokens,
                sent=True,
                stage="output",
                changes=changes,
            )
            return 422, _error(
                "output_guard",
                "Ответ модели скрыт: в нём кусок системного промпта или опасная команда.",
                event,
            )
        action = "masked" if (kinds or secrets) else "allowed"
        event = self._log(
            ip,
            mode,
            action,
            kinds or output_kinds,
            prompt_tokens,
            completion_tokens,
            sent=True,
            stage="done",
            changes=changes,
            forwarded=forwarded if kinds else None,
        )
        completion["gateway"] = _public(event)
        return 200, completion

    def _allow(self, ip, now):
        with self._lock:
            bucket = [stamp for stamp in self._hits.get(ip, []) if now - stamp < self.window]
            if len(bucket) >= self.limit:
                self._hits[ip] = bucket
                return False
            bucket.append(now)
            self._hits[ip] = bucket
            return True

    def _log(
        self,
        ip,
        mode,
        action,
        kinds,
        prompt_tokens,
        completion_tokens,
        sent,
        stage="input",
        changes=None,
        forwarded=None,
        found=None,
    ):
        event = {
            "ip": ip,
            "mode": mode,
            "action": action,
            "stage": stage,
            "kinds": kinds,
            "sent_to_model": sent,
            "prompt_tokens": prompt_tokens,
            "completion_tokens": completion_tokens,
            "cost_usd": round(guards.cost_usd(prompt_tokens, completion_tokens), 8),
        }
        if found:
            event["found"] = found
        if changes:
            event["changes"] = changes
        if forwarded is not None:
            event["forwarded"] = forwarded
        with self._lock:
            self.audit.append(event)
        return event


def ollama_upstream(messages, model):
    payload = {
        "model": model,
        "temperature": 0,
        "max_tokens": 120,
        "messages": messages,
    }
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        UPSTREAM,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise OSError(error.reason) from error
    except TimeoutError:
        raise


def make_handler(gateway):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path != "/health":
                self._send(404, {"error": {"type": "not_found", "message": "Нет такого пути."}})
                return
            self._send(200, {"status": "ok"})

        def do_POST(self):
            if self.path != "/v1/chat/completions":
                self._send(404, {"error": {"type": "not_found", "message": "Нет такого пути."}})
                return
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length) if length else b""
            try:
                body = json.loads(raw.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError):
                self._send(400, {"error": {"type": "bad_request", "message": "Тело не JSON."}})
                return
            mode = self.headers.get("X-Guard-Mode", "block").strip().lower()
            ip = self.client_address[0]
            status, payload = gateway.handle(body, ip, mode)
            self._send(status, payload)

        def log_message(self, fmt, *args):
            return

        def _send(self, status, payload):
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

    return Handler


def serve(gateway, host="127.0.0.1", port=8793):
    server = ThreadingHTTPServer((host, port), make_handler(gateway))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server


def _inspect(messages):
    found = []
    changes = []
    matched = []
    forwarded = []
    for content in _contents(messages):
        updated, found_here, replacements = guards.redact(content)
        found.extend(found_here)
        changes.extend(_arrows(replacements))
        matched.extend(item["matched"] for item in replacements)
        forwarded.append(updated)
    return found, changes, "\n".join(forwarded), matched


def _arrows(replacements):
    return [f"{item['matched']} → {item['replaced_with']}" for item in replacements]


def _contents(messages):
    parts = []
    for message in messages:
        if not isinstance(message, dict) or message.get("role") == "system":
            continue
        content = message.get("content")
        if isinstance(content, str):
            parts.append(content)
    return parts


def _prepare(messages, mask):
    prepared = [{"role": "system", "content": guards.SYSTEM_PROMPT}]
    for message in messages:
        if not isinstance(message, dict):
            continue
        role = message.get("role")
        if role == "system":
            continue
        content = message.get("content")
        if not isinstance(content, str):
            continue
        if mask:
            content, _, _ = guards.redact(content)
        prepared.append({"role": role, "content": content})
    return prepared


def _answer_text(completion):
    choices = completion.get("choices") if isinstance(completion, dict) else None
    if not isinstance(choices, list) or not choices:
        return ""
    message = choices[0].get("message") if isinstance(choices[0], dict) else None
    if not isinstance(message, dict):
        return ""
    content = message.get("content")
    return content if isinstance(content, str) else ""


def _replace_answer(completion, answer):
    choices = completion.get("choices")
    if isinstance(choices, list) and choices and isinstance(choices[0], dict):
        message = choices[0].get("message")
        if isinstance(message, dict):
            message["content"] = answer
    return completion


def _usage(completion, messages, answer):
    usage = completion.get("usage") if isinstance(completion, dict) else None
    if isinstance(usage, dict) and "prompt_tokens" in usage and "completion_tokens" in usage:
        return int(usage["prompt_tokens"]), int(usage["completion_tokens"])
    prompt = "\n".join(message["content"] for message in messages)
    return guards.estimate_tokens(prompt), guards.estimate_tokens(answer)


def _kinds(found):
    kinds = []
    for kind, _, _ in found:
        if kind not in kinds:
            kinds.append(kind)
    return kinds


def _error(kind, message, event=None):
    body = {"error": {"type": kind, "message": message}}
    if event is not None:
        body["error"]["kinds"] = event["kinds"]
        body["gateway"] = _public(event)
    return body


def _public(event):
    hidden = ("changes", "forwarded", "found")
    return {key: value for key, value in event.items() if key not in hidden}


if __name__ == "__main__":
    box = Gateway(ollama_upstream)
    httpd = serve(box)
    print(f"http://{httpd.server_address[0]}:{httpd.server_address[1]}/v1/chat/completions")
    try:
        threading.Event().wait()
    except KeyboardInterrupt:
        httpd.shutdown()
