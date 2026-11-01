"""Обучение ученика и повтор теста. Базовый прогон не повторяется: модель и тест те же."""

import subprocess
import sys


def main():
    steps = [
        [sys.executable, "train.py"],
        [sys.executable, "evaluate.py", "tuned"],
        [sys.executable, "evaluate.py", "summary"],
    ]
    for step in steps:
        print("stage " + " ".join(step), flush=True)
        code = subprocess.call(step)
        if code:
            raise SystemExit(code)


if __name__ == "__main__":
    main()
