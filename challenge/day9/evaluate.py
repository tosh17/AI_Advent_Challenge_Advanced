"""Прогон ученика: база и LoRA. Счёт тот же, что у учителя."""

import json
import sys
import time
from pathlib import Path

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer

from build_dataset import PARTS
from score import judge

ROOT = Path(__file__).resolve().parent
MODEL_ID = "Qwen/Qwen2.5-0.5B-Instruct"
OUT = ROOT / "output"
SYSTEM = (ROOT / "prompt.txt").read_text(encoding="utf-8").strip()
BY_PART = {card["part"]: card for card in PARTS}


def read_jsonl(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def load_model(adapter):
    model = AutoModelForCausalLM.from_pretrained(MODEL_ID, torch_dtype=torch.float32, low_cpu_mem_usage=True)
    if adapter is not None:
        model = PeftModel.from_pretrained(model, adapter)
    model.eval()
    model.config.use_cache = True
    model.generation_config.do_sample = False
    model.generation_config.temperature = None
    model.generation_config.top_p = None
    model.generation_config.top_k = None
    return model


def generate(model, tokenizer, user):
    messages = [{"role": "system", "content": SYSTEM}, {"role": "user", "content": user}]
    prompt = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    inputs = tokenizer(prompt, return_tensors="pt")
    started = time.time()
    with torch.no_grad():
        output = model.generate(
            **inputs,
            max_new_tokens=120,
            do_sample=False,
            pad_token_id=tokenizer.pad_token_id,
            eos_token_id=tokenizer.eos_token_id,
        )
    elapsed = time.time() - started
    new_tokens = output[0][inputs["input_ids"].shape[1] :]
    text = tokenizer.decode(new_tokens, skip_special_tokens=True).strip()
    return text, int(inputs["input_ids"].shape[1]), int(new_tokens.shape[0]), round(elapsed, 2)


def annotate(row, pred, prompt_tokens, new_tokens, seconds):
    verdict = judge(pred, BY_PART[row["part"]])
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
    }
    item.update(verdict)
    return item


def run(mode):
    adapter = None if mode == "bare" else OUT / "adapter"
    rows = read_jsonl(ROOT / "dataset" / "test.jsonl")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID if adapter is None else str(adapter))
    if tokenizer.pad_token_id is None:
        tokenizer.pad_token = tokenizer.eos_token
    model = load_model(None if adapter is None else str(adapter))
    done = []
    for index, row in enumerate(rows, start=1):
        pred, prompt_tokens, new_tokens, seconds = generate(model, tokenizer, row["user"])
        item = annotate(row, pred, prompt_tokens, new_tokens, seconds)
        done.append(item)
        print(f"{index}/{len(rows)} {row['id']} hit={item['hit']} {pred[:70].replace(chr(10), ' ')}", flush=True)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f"{mode}.json").write_text(json.dumps(done, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def rate(rows, seen):
    chosen = [row for row in rows if row["seen"] is seen]
    if not chosen:
        return None
    return {
        "n": len(chosen),
        "hit": sum(1 for row in chosen if row["hit"]),
        "part": sum(1 for row in chosen if row["part_ok"]),
        "bus": sum(1 for row in chosen if row["bus_ok"]),
        "voltage": sum(1 for row in chosen if row["voltage_ok"]),
        "library": sum(1 for row in chosen if row["library_ok"]),
        "json": sum(1 for row in chosen if row["json_ok"]),
        "seconds": round(sum(row["seconds"] for row in chosen), 1),
        "seconds_each": round(sum(row["seconds"] for row in chosen) / len(chosen), 2),
        "prompt_tokens": round(sum(row["prompt_tokens"] for row in chosen) / len(chosen), 1),
        "new_tokens": round(sum(row["new_tokens"] for row in chosen) / len(chosen), 1),
    }


def load_teacher():
    path = OUT / "teacher_test.jsonl"
    rows = []
    for row in read_jsonl(path):
        verdict = judge(row["pred"], BY_PART[row["part"]])
        row.update(verdict)
        rows.append(row)
    return rows


def summary():
    modes = {}
    for name in ("bare", "tuned"):
        path = OUT / f"{name}.json"
        if path.exists():
            modes[name] = json.loads(path.read_text(encoding="utf-8"))
    if (OUT / "teacher_test.jsonl").exists():
        modes["teacher"] = load_teacher()
    report = {}
    for name, rows in modes.items():
        report[name] = {"seen": rate(rows, True), "held": rate(rows, False)}
    (OUT / "summary.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("bare", "tuned", "summary"):
        raise SystemExit("usage: evaluate.py bare|tuned|summary")
    if sys.argv[1] == "summary":
        summary()
        return
    run(sys.argv[1])


if __name__ == "__main__":
    main()
