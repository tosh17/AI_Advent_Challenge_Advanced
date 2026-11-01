"""Оставляет ответы учителя, где деталь, шина и питание совпали с карточкой.

Тест в обучение не попадает. Цель ученика — проверенный JSON, не сырой текст.
"""

import json
from pathlib import Path

from build_dataset import PARTS
from score import judge

BY_PART = {card["part"]: card for card in PARTS}

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "dataset"
OUT = ROOT / "output"
SYSTEM = (ROOT / "prompt.txt").read_text(encoding="utf-8").strip()


def read_jsonl(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def chat(user, gold):
    return {
        "messages": [
            {"role": "system", "content": SYSTEM},
            {"role": "user", "content": user},
            {"role": "assistant", "content": json.dumps(gold, ensure_ascii=False)},
        ]
    }


def take(split):
    rows = read_jsonl(OUT / f"teacher_{split}.jsonl")
    kept = []
    rejected = []
    users = set()
    for row in rows:
        verdict = judge(row["pred"], BY_PART[row["part"]])
        packed = {
            "id": row["id"],
            "part": row["part"],
            "user": row["user"],
            "pred": row["pred"],
            "hit": verdict["hit"],
            "json": verdict["json_ok"],
            "part_ok": verdict["part_ok"],
            "bus_ok": verdict["bus_ok"],
            "voltage_ok": verdict["voltage_ok"],
        }
        if row["user"] in users:
            raise SystemExit(f"повтор в ответах учителя: {row['user']}")
        users.add(row["user"])
        if verdict["hit"]:
            item = chat(row["user"], row["gold"])
            item["id"] = row["id"]
            item["part"] = row["part"]
            kept.append(item)
        else:
            rejected.append(packed)
    return kept, rejected, len(rows)


def main():
    train, train_bad, train_n = take("train")
    valid, valid_bad, valid_n = take("valid")
    test_users = {row["user"] for row in read_jsonl(DATA / "test.jsonl")}
    for item in train + valid:
        if item["messages"][1]["content"] in test_users:
            raise SystemExit("тестовый вопрос попал в обучение")
    DATA.mkdir(parents=True, exist_ok=True)

    def write(path, rows):
        text = "\n".join(json.dumps(row, ensure_ascii=False) for row in rows) + "\n"
        path.write_text(text, encoding="utf-8")

    write(DATA / "train.jsonl", train)
    write(DATA / "valid.jsonl", valid)
    write(OUT / "rejected.jsonl", train_bad + valid_bad)
    stats = {
        "teacher_train": train_n,
        "teacher_valid": valid_n,
        "kept_train": len(train),
        "kept_valid": len(valid),
        "rejected": len(train_bad) + len(valid_bad),
    }
    (DATA / "filter_stats.json").write_text(json.dumps(stats, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(stats, ensure_ascii=False))


if __name__ == "__main__":
    main()
