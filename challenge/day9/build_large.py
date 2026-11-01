"""Большой пул вопросов учителю. Тестовые формулировки и уже заданные строки не повторяет."""

import json
from pathlib import Path

from build_dataset import PARTS, gold_of

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "dataset"

TRAIN_TEMPLATES = [
    "Для поделки на Uno беру {part}. Заполни карточку: что делает, шина, питание, библиотека.",
    "Нужно впаять {part} на макетку. Какая шина, какое voltage и какая library?",
    "Смотрю даташит {part} перед разводкой. Назови bus и voltage из разрешённого списка.",
    "Поделка: деталь {part}. Куда сажать питание и по какому протоколу говорить?",
    "Объясни новичку подключение {part}: шина, вольты, библиотека Arduino.",
    "В списке покупки есть {part}. Что он меряет и от скольких вольт его кормить?",
    "Пишу скетч под {part}. Какую библиотеку ставить и какая у него шина?",
    "Перед пайкой {part} проверь bus, voltage и library. Ответ карточкой.",
    "Хочу оживить {part} на столе. Какое питание его не сожжёт и как с ним говорит контроллер?",
    "Карточка для стенда, деталь {part}. Заполни measures, bus, voltage, library.",
    "У меня только Uno и {part}. По какой шине его вешать и какое питание подавать?",
    "Разложи {part} по полям: назначение, шина, питание, библиотека.",
    "Сосед спрашивает, как кормить {part}. Назови voltage и bus.",
    "В коробке радиодеталей лежит {part}. Какая шина и сколько вольт на VCC?",
    "Для кружка готовлю {part}. Какая библиотека и какое питание?",
    "Подскажи обвязку {part}: шина, вольты, библиотека и что деталь умеет.",
    "Составляю таблицу модулей. Строка {part}: bus, voltage, library, measures.",
    "Без распиновки, только карточка {part}: шина, питание, библиотека.",
    "Проверяю питание {part}. Какое voltage допустимо и какая шина?",
    "Ставлю {part} вместо похожей детали. Чем он говорит с Arduino и от чего питается?",
    "Шпаргалка на одну строку, деталь {part}: measures, bus, voltage, library.",
    "Перед заказом смотрю {part}. Какая шина и какое питание логики?",
    "Ученик держит {part}. Какая шина, сколько вольт и какая библиотека?",
    "Занеси {part} в конспект: что делает, шина, питание, библиотека.",
    "Макетка запитана. Как сажать {part} и по какому bus с ним говорить?",
    "Нужны два поля {part} без догадок: voltage из списка и bus из списка.",
    "Очередная строка справочника, деталь {part}. Заполни все поля карточки.",
    "Карточка без прозы, деталь {part}: part, measures, bus, voltage, library.",
    "Схема на салфетке, деталь {part}. Шина, питание, библиотека, назначение.",
    "В каталоге поделки строка {part}. Укажи протокол связи и допустимое питание.",
    "Сверяю {part} с даташитом. Какой bus и какой voltage писать в карточку?",
    "Перед включением {part}: сколько вольт на VCC и какая шина данных?",
    "Речь о кристалле {part}, не о плате со своим стабилизатором. Какая шина и какое voltage?",
]

VALID_TEMPLATES = [
    "Отдельная проверочная карточка {part}: measures, bus, voltage, library.",
    "Сверю себя. Деталь {part}: шина, питание, библиотека, назначение.",
    "Черновик справочника, строка {part}. Поля bus, voltage, library, measures.",
    "Короткий контроль перед пайкой {part}. Шина, вольты и библиотека.",
]


def existing_users():
    found = set()
    for path in DATA.glob("*.jsonl"):
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            row = json.loads(line)
            if "user" in row:
                found.add(row["user"])
            messages = row.get("messages")
            if messages:
                found.add(messages[1]["content"])
    out = ROOT / "output"
    if out.exists():
        for path in out.glob("teacher_*.jsonl"):
            for line in path.read_text(encoding="utf-8").splitlines():
                if line.strip():
                    found.add(json.loads(line)["user"])
    return found


def main():
    banned = existing_users()
    train_parts = [card for card in PARTS if not card.get("held")]
    buckets = {"train": [], "valid": []}
    seen = set(banned)

    def add(split, card, user):
        if user in seen:
            raise SystemExit(f"вопрос уже был: {user}")
        seen.add(user)
        buckets[split].append(
            {
                "split": split,
                "seen": True,
                "part": card["part"],
                "user": user,
                "gold": gold_of(card),
            }
        )

    for card in train_parts:
        for template in TRAIN_TEMPLATES:
            add("train", card, template.format(part=card["part"]))
        for template in VALID_TEMPLATES:
            add("valid", card, template.format(part=card["part"]))

    held_names = [card["part"] for card in PARTS if card.get("held")]
    for split, rows in buckets.items():
        for item in rows:
            for name in held_names:
                if name in item["user"]:
                    raise SystemExit(f"скрытая деталь в вопросе: {name}")

    def write(name, rows, prefix):
        for index, item in enumerate(rows, start=1):
            item["id"] = f"{prefix}-{index:04d}"
        text = "\n".join(json.dumps(item, ensure_ascii=False) for item in rows) + "\n"
        (DATA / name).write_text(text, encoding="utf-8")

    write("pool_train.jsonl", buckets["train"], "big-train")
    write("pool_valid.jsonl", buckets["valid"], "big-valid")
    stats = {
        "big_train": len(buckets["train"]),
        "big_valid": len(buckets["valid"]),
        "templates_train": len(TRAIN_TEMPLATES),
        "templates_valid": len(VALID_TEMPLATES),
        "parts": len(train_parts),
        "banned_users": len(banned),
    }
    (DATA / "big_stats.json").write_text(json.dumps(stats, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(stats, ensure_ascii=False))


if __name__ == "__main__":
    main()
