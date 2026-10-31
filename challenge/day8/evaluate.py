"""Прогон: база, база с промптом, последний чекпоинт и чекпоинт с лучшим valid."""

import json
import re
import sys
import time
from pathlib import Path

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer

ROOT = Path(__file__).resolve().parent
MODEL_ID = "Qwen/Qwen2.5-0.5B-Instruct"
OUT = ROOT / "output"
CHRONICLE = (ROOT / "chronicle.txt").read_text(encoding="utf-8").strip()
FORMAT_PROMPT = (ROOT / "prompt_format.txt").read_text(encoding="utf-8").strip()
STYLE_PROMPT = (ROOT / "prompt_style.txt").read_text(encoding="utf-8").strip()
POLLUTE = [
    "еремей",
    "чаша орбиты",
    "аминь чернилам",
    "ответ:",
    "прохор",
    "улита",
    "кривые вербы",
    "звездное серебро",
    "неждана",
    "фекла",
    "маланья",
    "соломония",
    "гликерия",
    "семиврат",
    "косой клин",
    "синий порог",
]


def read_jsonl(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def norm(text):
    collapsed = re.sub(r"\s+", " ", text.casefold().replace("ё", "е")).strip()
    return collapsed.strip(".,!?;:\"'«» ")


def load_rows():
    rows = []
    for name in ("eval_close.jsonl", "eval_far.jsonl", "eval_unread.jsonl", "eval_ood.jsonl"):
        rows.extend(read_jsonl(ROOT / "dataset" / name))
    return rows


def load_model(adapter):
    model = AutoModelForCausalLM.from_pretrained(
        MODEL_ID,
        torch_dtype=torch.float32,
        low_cpu_mem_usage=True,
    )
    if adapter is not None:
        model = PeftModel.from_pretrained(model, adapter)
    model.eval()
    model.config.use_cache = True
    model.generation_config.do_sample = False
    model.generation_config.temperature = None
    model.generation_config.top_p = None
    model.generation_config.top_k = None
    return model


def generate(model, tokenizer, messages, max_new):
    prompt = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    inputs = tokenizer(prompt, return_tensors="pt")
    started = time.time()
    with torch.no_grad():
        output = model.generate(
            **inputs,
            max_new_tokens=max_new,
            do_sample=False,
            pad_token_id=tokenizer.pad_token_id,
            eos_token_id=tokenizer.eos_token_id,
        )
    elapsed = time.time() - started
    new_tokens = output[0][inputs["input_ids"].shape[1] :]
    text = tokenizer.decode(new_tokens, skip_special_tokens=True).strip()
    return text, int(inputs["input_ids"].shape[1]), int(new_tokens.shape[0]), round(elapsed, 2)


def messages_for(mode, row):
    user = row["messages"][0]["content"]
    if mode != "prompt":
        return [{"role": "user", "content": user}]
    task = row["task"]
    if task == "fact":
        system = CHRONICLE
    elif task == "format":
        system = FORMAT_PROMPT
    elif task == "style":
        system = STYLE_PROMPT
    else:
        return [{"role": "user", "content": user}]
    return [{"role": "system", "content": system}, {"role": "user", "content": user}]


def score(row, pred):
    task = row["task"]
    folded = norm(pred)
    needles = [norm(item) for item in row["needles"]]
    facts = all(needle in folded for needle in needles) if needles else False
    lines = [line.strip() for line in pred.strip().splitlines() if line.strip()]
    form = len(lines) == 2 and lines[0].casefold().startswith("ответ:") and norm(lines[1]) == "конец"
    voice = folded.startswith("пишу я, еремей сухой") and folded.endswith("аминь чернилам")
    polluted = any(marker in folded for marker in POLLUTE)
    if task == "fact":
        hit = facts
    elif task == "format":
        hit = form
    elif task == "style":
        hit = voice
    else:
        hit = facts and not polluted
    return {
        "hit": hit,
        "needles": facts,
        "format": form,
        "voice": voice,
        "polluted": polluted,
    }


def max_new_for(task):
    if task == "format":
        return 32
    if task == "ood":
        return 48
    if task == "style":
        return 80
    return 64


def run(mode):
    if mode == "tuned":
        adapter = OUT / "adapter"
    elif mode == "best":
        adapter = OUT / "adapter_best"
    elif mode in ("bare", "prompt"):
        adapter = None
    else:
        raise SystemExit(f"неизвестный режим {mode}")
    rows = load_rows()
    if mode == "prompt":
        rows = [row for row in rows if row["task"] != "ood"]
    print(f"mode {mode} rows {len(rows)}", flush=True)
    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
    if tokenizer.pad_token_id is None:
        tokenizer.pad_token = tokenizer.eos_token
    model = load_model(None if adapter is None else str(adapter))
    done = []
    for index, row in enumerate(rows, start=1):
        messages = messages_for(mode, row)
        pred, prompt_tokens, new_tokens, seconds = generate(
            model,
            tokenizer,
            messages,
            max_new_for(row["task"]),
        )
        item = {
            "id": row["id"],
            "task": row["task"],
            "split": row["split"],
            "user": row["messages"][0]["content"],
            "gold": row["messages"][1]["content"],
            "pred": pred,
            "prompt_tokens": prompt_tokens,
            "new_tokens": new_tokens,
            "seconds": seconds,
        }
        item.update(score(row, pred))
        done.append(item)
        print(
            f"{index}/{len(rows)} {row['id']} {row['task']} hit={item['hit']} polluted={item['polluted']}",
            flush=True,
        )
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f"{mode}.json").write_text(json.dumps(done, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {mode}.json", flush=True)


def rate(rows, task, split=None):
    chosen = [row for row in rows if row["task"] == task and (split is None or row["split"] == split)]
    if not chosen:
        return None
    return {
        "n": len(chosen),
        "hit": sum(1 for row in chosen if row["hit"]),
        "needles": sum(1 for row in chosen if row["needles"]),
        "format": sum(1 for row in chosen if row["format"]),
        "voice": sum(1 for row in chosen if row["voice"]),
        "polluted": sum(1 for row in chosen if row["polluted"]),
        "prompt_tokens": round(sum(row["prompt_tokens"] for row in chosen) / len(chosen), 1),
    }


def summary():
    modes = {}
    for name in ("bare", "prompt", "tuned", "best"):
        path = OUT / f"{name}.json"
        if path.exists():
            modes[name] = json.loads(path.read_text(encoding="utf-8"))
    report = {}
    for name, rows in modes.items():
        report[name] = {
            "fact_close": rate(rows, "fact", "close"),
            "fact_far": rate(rows, "fact", "far"),
            "fact_unread": rate(rows, "fact", "unread"),
            "format": rate(rows, "format"),
            "style": rate(rows, "style"),
            "ood": rate(rows, "ood"),
        }
    (OUT / "summary.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: evaluate.py bare|prompt|tuned|best|summary")
    mode = sys.argv[1]
    if mode == "summary":
        summary()
        return
    run(mode)


if __name__ == "__main__":
    main()
