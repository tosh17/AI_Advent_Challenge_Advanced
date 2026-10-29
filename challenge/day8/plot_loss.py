"""Кривая train loss и eval loss."""

import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parent
rows = []
for line in (ROOT / "output" / "loss.jsonl").read_text(encoding="utf-8").splitlines():
    if line.strip():
        rows.append(json.loads(line))

train = [(row["step"], row["loss"]) for row in rows if "loss" in row]
valid = [(row["step"], row["eval_loss"]) for row in rows if "eval_loss" in row]

figure, axis = plt.subplots(figsize=(8, 4.2))
if train:
    axis.plot([item[0] for item in train], [item[1] for item in train], label="train loss")
if valid:
    axis.plot([item[0] for item in valid], [item[1] for item in valid], marker="o", label="valid loss")
axis.set_xlabel("шаг")
axis.set_ylabel("loss")
axis.legend()
axis.grid(True, alpha=0.3)
figure.tight_layout()
figure.savefig(ROOT / "output" / "loss.png", dpi=140)
print(f"train points {len(train)} eval points {len(valid)}")
