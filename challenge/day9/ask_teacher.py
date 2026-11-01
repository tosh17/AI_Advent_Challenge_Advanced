"""Спросить учителя. Тест и обучение пишутся в разные файлы."""

import json
import os
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "output"
SYSTEM = (ROOT / "prompt.txt").read_text(encoding="utf-8").strip()
URL = os.environ.get("OLLAMA_URL", "http://10.10.1.101:11434/api/chat")
MODEL = os.environ.get("TEACHER", "qwen2.5:7b")


def read_jsonl(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def ask(user):
    body = {
        "model": MODEL,
        "stream": False,
        "messages": [
            {"role": "system", "content": SYSTEM},
            {"role": "user", "content": user},
        ],
        "options": {"temperature": 0, "num_predict": 160, "num_ctx": 2048},
    }
    data = json.dumps(body).encode("utf-8")
    started = time.time()
    payload = None
    last_error = None
    for attempt in range(4):
        request = urllib.request.Request(URL, data=data, headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=180) as response:
                payload = json.loads(response.read().decode("utf-8"))
            break
        except (urllib.error.URLError, TimeoutError, ConnectionError, json.JSONDecodeError) as error:
            last_error = error
            print(f"retry {attempt + 1} {type(error).__name__}", flush=True)
            time.sleep(3)
    if payload is None:
        raise SystemExit(f"учитель не ответил: {last_error}")
    elapsed = time.time() - started
    text = payload.get("message", {}).get("content", "").strip()
    prompt_tokens = int(payload.get("prompt_eval_count") or 0)
    new_tokens = int(payload.get("eval_count") or 0)
    return text, prompt_tokens, new_tokens, round(elapsed, 2)


def run(split):
    source = {
        "train": ROOT / "dataset" / "pool_train.jsonl",
        "valid": ROOT / "dataset" / "pool_valid.jsonl",
        "test": ROOT / "dataset" / "test.jsonl",
    }[split]
    rows = read_jsonl(source)
    OUT.mkdir(parents=True, exist_ok=True)
    dest = OUT / f"teacher_{split}.jsonl"
    done = {}
    if dest.exists():
        for line in dest.read_text(encoding="utf-8").splitlines():
            if line.strip():
                item = json.loads(line)
                done[item["id"]] = item
    print(f"large {MODEL} split {split} todo {len(rows) - len(done)}", flush=True)
    with dest.open("a", encoding="utf-8") as handle:
        for index, row in enumerate(rows, start=1):
            if row["id"] in done:
                continue
            pred, prompt_tokens, new_tokens, seconds = ask(row["user"])
            item = {
                "id": row["id"],
                "split": row["split"],
                "seen": row["seen"],
                "part": row["part"],
                "user": row["user"],
                "gold": row["gold"],
                "pred": pred,
                "prompt_tokens": prompt_tokens,
                "new_tokens": new_tokens,
                "seconds": seconds,
                "model": MODEL,
            }
            handle.write(json.dumps(item, ensure_ascii=False) + "\n")
            handle.flush()
            print(f"{index}/{len(rows)} {row['id']} {seconds}s {pred[:80].replace(chr(10), ' ')}", flush=True)


if __name__ == "__main__":
    import sys

    if len(sys.argv) != 2 or sys.argv[1] not in ("train", "valid", "test"):
        raise SystemExit("usage: ask_teacher.py train|valid|test")
    run(sys.argv[1])
