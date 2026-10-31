"""LoRA на летописи Семивратья. Лосс только по ответу ассистента.

Длинный прогон специально, чтобы поймать момент, когда valid перестаёт падать.
"""

import json
import os
import platform
import time
from pathlib import Path

import torch
from peft import LoraConfig, TaskType, get_peft_model
from torch.utils.data import Dataset
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    Trainer,
    TrainerCallback,
    TrainingArguments,
)

MODEL_ID = os.environ.get("MODEL_ID", "Qwen/Qwen2.5-0.5B-Instruct")
MAX_LEN = int(os.environ.get("MAX_LEN", "384"))
SEED = 42
ROOT = Path(__file__).resolve().parent
OUT = ROOT / "output"
OUT.mkdir(parents=True, exist_ok=True)


class LossLog(TrainerCallback):
    def on_log(self, args, state, control, logs=None, **kwargs):
        if not logs:
            return
        row = {"step": state.global_step}
        for key, value in logs.items():
            if isinstance(value, (int, float)):
                row[key] = value
        with (OUT / "loss.jsonl").open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(row) + "\n")


class BestAdapter(TrainerCallback):
    def __init__(self, model):
        self.model = model
        self.best = None
        self.best_step = None

    def on_evaluate(self, args, state, control, metrics=None, **kwargs):
        if not metrics or "eval_loss" not in metrics:
            return
        loss = float(metrics["eval_loss"])
        if self.best is not None and loss >= self.best:
            print(f"valid не улучшился: step {state.global_step} eval_loss {loss:.4f} best {self.best:.4f}", flush=True)
            return
        self.best = loss
        self.best_step = state.global_step
        dest = OUT / "adapter_best"
        self.model.save_pretrained(dest)
        print(f"best adapter step {self.best_step} eval_loss {loss:.4f}", flush=True)


class TokenDataset(Dataset):
    def __init__(self, rows):
        self.rows = rows

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, index):
        return self.rows[index]


def hardware():
    cpuinfo = Path("/proc/cpuinfo").read_text(encoding="utf-8", errors="replace")
    model = next(
        (line.split(":", 1)[1].strip() for line in cpuinfo.splitlines() if line.startswith("model name")),
        platform.processor(),
    )
    meminfo = Path("/proc/meminfo").read_text(encoding="utf-8", errors="replace")
    mem_kb = next(int(line.split()[1]) for line in meminfo.splitlines() if line.startswith("MemTotal"))
    return {
        "cpu": model,
        "logical_cpus": os.cpu_count(),
        "mem_total_gb": round(mem_kb / 1024 / 1024, 2),
        "cuda": torch.cuda.is_available(),
        "torch": torch.__version__,
        "precision": "fp32",
        "method": "LoRA",
    }


def read_jsonl(path):
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            rows.append(json.loads(line))
    return rows


def user_text(row):
    return row["messages"][0]["content"]


def encode(tokenizer, row):
    messages = row["messages"]
    prompt_ids = tokenizer.apply_chat_template(
        messages[:-1],
        tokenize=True,
        add_generation_prompt=True,
    )
    full_ids = tokenizer.apply_chat_template(
        messages,
        tokenize=True,
        add_generation_prompt=False,
    )
    if full_ids[: len(prompt_ids)] != prompt_ids:
        return None
    if len(full_ids) > MAX_LEN:
        return None
    labels = [-100] * len(prompt_ids) + full_ids[len(prompt_ids) :]
    if not any(token != -100 for token in labels):
        return None
    return {
        "input_ids": full_ids,
        "labels": labels,
        "attention_mask": [1] * len(full_ids),
    }


def collate(features):
    width = max(len(item["input_ids"]) for item in features)
    pad_id = tokenizer.pad_token_id
    input_ids = []
    labels = []
    attention = []
    for item in features:
        pad = width - len(item["input_ids"])
        input_ids.append(item["input_ids"] + [pad_id] * pad)
        labels.append(item["labels"] + [-100] * pad)
        attention.append(item["attention_mask"] + [0] * pad)
    return {
        "input_ids": torch.tensor(input_ids, dtype=torch.long),
        "labels": torch.tensor(labels, dtype=torch.long),
        "attention_mask": torch.tensor(attention, dtype=torch.long),
    }


def assert_held_out(train_rows, valid_rows, held_rows):
    train_users = {user_text(row) for row in train_rows}
    valid_users = {user_text(row) for row in valid_rows}
    held_users = {user_text(row) for row in held_rows}
    if train_users & valid_users or train_users & held_users or valid_users & held_users:
        raise SystemExit("вопрос из valid или eval попал в другой сплит")


def main():
    torch.set_num_threads(int(os.environ.get("OMP_NUM_THREADS", "3")))
    started = time.time()
    print(f"model {MODEL_ID}", flush=True)
    print("hardware " + json.dumps(hardware(), ensure_ascii=False), flush=True)

    tokenizer_local = AutoTokenizer.from_pretrained(MODEL_ID)
    global tokenizer
    tokenizer = tokenizer_local
    if tokenizer.pad_token_id is None:
        tokenizer.pad_token = tokenizer.eos_token

    train_raw = read_jsonl(ROOT / "dataset" / "train.jsonl")
    valid_raw = read_jsonl(ROOT / "dataset" / "valid.jsonl")
    held_raw = []
    for name in ("eval_close.jsonl", "eval_far.jsonl", "eval_unread.jsonl", "eval_ood.jsonl"):
        held_raw.extend(read_jsonl(ROOT / "dataset" / name))
    assert_held_out(train_raw, valid_raw, held_raw)

    train_rows = []
    valid_rows = []
    skipped = 0
    for row in train_raw:
        item = encode(tokenizer, row)
        if item is None:
            skipped += 1
            continue
        train_rows.append(item)
    for row in valid_raw:
        item = encode(tokenizer, row)
        if item is None:
            skipped += 1
            continue
        valid_rows.append(item)
    if skipped:
        raise SystemExit(f"не закодировались {skipped} примеров")
    print(
        f"split train={len(train_rows)} valid={len(valid_rows)} held_out={len(held_raw)}",
        flush=True,
    )

    sample = train_raw[0]
    (OUT / "template_example.json").write_text(
        json.dumps(
            {
                "id": sample["id"],
                "chat": tokenizer.apply_chat_template(sample["messages"], tokenize=False),
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    print("loading model", flush=True)
    model = AutoModelForCausalLM.from_pretrained(
        MODEL_ID,
        torch_dtype=torch.float32,
        low_cpu_mem_usage=True,
    )
    model.config.use_cache = False
    model.gradient_checkpointing_enable()
    model.enable_input_require_grads()
    lora = LoraConfig(
        r=8,
        lora_alpha=16,
        lora_dropout=0.05,
        bias="none",
        task_type=TaskType.CAUSAL_LM,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
    )
    model = get_peft_model(model, lora)
    model.print_trainable_parameters()
    trainable = sum(param.numel() for param in model.parameters() if param.requires_grad)
    total = sum(param.numel() for param in model.parameters())
    best = BestAdapter(model)

    if (OUT / "loss.jsonl").exists():
        (OUT / "loss.jsonl").unlink()

    args = TrainingArguments(
        output_dir=str(OUT / "trainer"),
        per_device_train_batch_size=1,
        per_device_eval_batch_size=1,
        gradient_accumulation_steps=4,
        learning_rate=2e-4,
        num_train_epochs=8,
        warmup_ratio=0.05,
        weight_decay=0.01,
        lr_scheduler_type="cosine",
        logging_steps=5,
        eval_strategy="steps",
        eval_steps=20,
        save_strategy="no",
        report_to="none",
        use_cpu=True,
        fp16=False,
        bf16=False,
        dataloader_pin_memory=False,
        remove_unused_columns=False,
        seed=SEED,
        data_seed=SEED,
        optim="adamw_torch",
        max_grad_norm=1.0,
    )
    trainer = Trainer(
        model=model,
        args=args,
        train_dataset=TokenDataset(train_rows),
        eval_dataset=TokenDataset(valid_rows),
        data_collator=collate,
        callbacks=[LossLog(), best],
    )
    print("train start", flush=True)
    train_started = time.time()
    result = trainer.train()
    trainer.evaluate()
    train_seconds = time.time() - train_started
    adapter = OUT / "adapter"
    trainer.save_model(str(adapter))
    tokenizer.save_pretrained(str(adapter))
    tokenizer.save_pretrained(str(OUT / "adapter_best"))
    print(f"adapter saved {adapter}", flush=True)

    meta = {
        "model_id": MODEL_ID,
        "dataset": "semivratye chronicle, format, style, unread held out",
        "method": "LoRA",
        "qlora": False,
        "lora": {
            "r": 8,
            "alpha": 16,
            "dropout": 0.05,
            "target_modules": ["q_proj", "k_proj", "v_proj", "o_proj"],
        },
        "precision": "fp32",
        "max_length": MAX_LEN,
        "train_examples": len(train_rows),
        "valid_examples": len(valid_rows),
        "held_out_examples": len(held_raw),
        "epochs": 8,
        "batch_size": 1,
        "gradient_accumulation_steps": 4,
        "learning_rate": 2e-4,
        "scheduler": "cosine",
        "warmup_ratio": 0.05,
        "weight_decay": 0.01,
        "seed": SEED,
        "trainable_params": trainable,
        "total_params": total,
        "best_eval_loss": best.best,
        "best_step": best.best_step,
        "train_seconds": round(train_seconds, 1),
        "wall_seconds": round(time.time() - started, 1),
        "train_loss": result.training_loss,
        "metrics": {key: value for key, value in result.metrics.items() if isinstance(value, (int, float))},
        "hardware": hardware(),
        "started_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(started)),
        "finished_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }
    (OUT / "run_meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("meta " + json.dumps(meta, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
