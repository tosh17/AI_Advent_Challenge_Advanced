import json
import os
import secrets
import sys
import threading
from datetime import date
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import urllib.error
import urllib.request
from urllib.parse import parse_qs, urlparse

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parent / "day13"))

import gateway

import agent
import store as store_mod

PAGE = (ROOT / "index.html").read_text(encoding="utf-8")


class Shop:
    def __init__(self, store, box):
        self.store = store
        self.box = box

    def handle(self, method, path, headers, raw, ip):
        parsed = urlparse(path)
        route = parsed.path
        if method == "GET" and route == "/health":
            return 200, {"status": "ok"}
        if method == "GET" and route in ("/", "/index.html"):
            return 200, PAGE, "text/html; charset=utf-8"
        if method == "POST" and route == "/api/login":
            return self._login(raw)
        user = self._user(headers)
        if user is None:
            return 401, {"error": "Нужен вход."}
        if method == "GET" and route == "/api/orders":
            return 200, {"name": user["name"], "orders": self.store.orders(user["id"])}
        if method == "POST" and route == "/api/orders":
            return self._add(user, raw)
        if method == "GET" and route == "/api/orders/search":
            query = (parse_qs(parsed.query).get("q") or [""])[0].strip()
            if not query:
                return 400, {"error": "Пустой поиск."}
            return 200, {"orders": self.store.search(user["id"], query)}
        if method == "GET" and route == "/api/hints":
            return 200, {"hints": self.store.hints(user["id"], date.today())}
        if method == "POST" and route == "/api/chat":
            body = _json(raw)
            if body is None or not str(body.get("text") or "").strip():
                return 400, {"error": "Пустое сообщение."}
            result = agent.talk(self.store, user, str(body["text"]).strip(), self.box, ip)
            return 200, result
        if method == "POST" and route == "/mcp":
            return self._mcp(user, raw)
        return 404, {"error": "Нет такого пути."}

    def _login(self, raw):
        body = _json(raw)
        if body is None:
            return 400, {"error": "Тело не JSON."}
        user = self.store.login(str(body.get("login") or ""), str(body.get("password") or ""))
        if user is None:
            return 401, {"error": "Неверный логин или пароль."}
        token = secrets.token_hex(16)
        self.store.open_session(user["id"], token)
        return 200, {"token": token, "name": user["name"], "login": user["login"]}

    def _add(self, user, raw):
        body = _json(raw)
        if body is None:
            return 400, {"error": "Тело не JSON."}
        item = str(body.get("item") or "").strip()
        try:
            qty = int(body.get("qty") or 0)
        except (TypeError, ValueError):
            return 400, {"error": "Количество не число."}
        if not item or qty < 1:
            return 400, {"error": "Нужны товар и количество."}
        note = str(body.get("note") or "")
        saved = self.store.add_order(user["id"], item, qty, note, date.today().isoformat())
        return 200, saved

    def _mcp(self, user, raw):
        body = _json(raw)
        if body is None or body.get("name") not in agent.TOOLS:
            return 400, {"error": "Нужно имя инструмента."}
        call = {"tool": body["name"]}
        arguments = body.get("arguments") if isinstance(body.get("arguments"), dict) else {}
        call.update(arguments)
        if body["name"] == "orders_add":
            item = str(call.get("item") or "").strip()
            try:
                qty = int(call.get("qty") or 0)
            except (TypeError, ValueError):
                return 400, {"error": "Количество не число."}
            if not item or qty < 1:
                return 400, {"error": "Нужны товар и количество."}
            saved = self.store.add_order(user["id"], item, qty, "", date.today().isoformat())
            return 200, {"tool": "orders_add", "result": saved}
        result = agent.run_tool(self.store, user, call, date.today())
        return 200, {"tool": body["name"], "result": result}

    def _user(self, headers):
        header = headers.get("Authorization") or headers.get("authorization") or ""
        if not header.startswith("Bearer "):
            return None
        return self.store.user_by_token(header[7:].strip())


def _json(raw):
    if not raw:
        return None
    try:
        data = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return None
    return data if isinstance(data, dict) else None


def make_handler(shop):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            self._dispatch("GET")

        def do_POST(self):
            self._dispatch("POST")

        def log_message(self, fmt, *args):
            return

        def _dispatch(self, method):
            length = int(self.headers.get("Content-Length", "0") or "0")
            raw = self.rfile.read(length) if length else b""
            try:
                status, payload, *rest = shop.handle(method, self.path, self.headers, raw, self.client_address[0])
            except Exception as error:
                status, payload, rest = 500, {"error": error.__class__.__name__}, []
            kind = rest[0] if rest else "application/json; charset=utf-8"
            if kind.startswith("text/html"):
                data = payload.encode("utf-8")
            else:
                data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", kind)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

    return Handler


def serve(shop, host="127.0.0.1", port=8815):
    server = ThreadingHTTPServer((host, port), make_handler(shop))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server


SHOP_PROMPT = (
    "Ты помощник по заказам. Отвечай по-русски. "
    "Если просят показать заказы, перечисли каждый заказ отдельной строкой. "
    "Не называй короткий список полным и не подменяй его блоком «пора купить»."
)


def shop_upstream(messages, model):
    key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    if not key:
        raise OSError("DEEPSEEK_API_KEY")
    url = os.environ.get("DEEPSEEK_API_URL", "").strip()
    if not url:
        url = os.environ.get("DEEPSEEK_BASE_URL", "https://api.deepseek.com").rstrip("/") + "/chat/completions"
    try:
        max_tokens = int(os.environ.get("DEEPSEEK_MAX_TOKENS", "800"))
    except ValueError:
        max_tokens = 800
    payload = {
        "model": os.environ.get("DEEPSEEK_MODEL", "deepseek-v4-flash"),
        "temperature": 0,
        "max_tokens": max_tokens,
        "messages": messages,
    }
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + key},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise OSError(f"HTTP {error.code}") from error


def main():
    path = os.environ.get("SHOP_DB", str(ROOT / "data" / "shop.db"))
    host = os.environ.get("SHOP_HOST", "127.0.0.1")
    port = int(os.environ.get("SHOP_PORT", "8815"))
    box = gateway.Gateway(shop_upstream, limit=60, system_prompt=SHOP_PROMPT)
    shop = Shop(store_mod.Store(path), box)
    httpd = serve(shop, host, port)
    print(f"http://{httpd.server_address[0]}:{httpd.server_address[1]}/")
    try:
        threading.Event().wait()
    except KeyboardInterrupt:
        httpd.shutdown()


if __name__ == "__main__":
    main()
