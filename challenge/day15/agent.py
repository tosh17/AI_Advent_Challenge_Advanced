import json
import re
from datetime import date
from pathlib import Path

CATALOG = Path(__file__).resolve().parent / "catalog" / "supplier.txt"

SECURITY = """Проверь текст перед записью или показом человеку.
Critical или High: ключ, пароль, почта, телефон, номер карты.
Medium или Low: товар назван неясно.
Ответ одной строкой: High: причина, Medium: причина, или CLEAN.
Текст:
"""

FINDING = re.compile(r"(?im)^\s*(critical|high|medium|low)\b\.?\s*(?:[:\-—]\s*(.+))?\s*$")
RECORD = re.compile(r"(?i)(запиш|добав|внес|куп)")
NEGATION = re.compile(r"(?i)(?<![а-яё])не(?![а-яё])")
TOOLS = ("orders_list", "orders_add", "orders_search", "buy_hints")


def _line(row):
    note = f" ({row['note']})" if row["note"] else ""
    return f"{row['bought_on']} {row['item']} × {row['qty']}{note}"


def catalog_text():
    return CATALOG.read_text(encoding="utf-8").strip()


def build_prompt(user, orders, hints, text, extra=""):
    history = "\n".join(_line(row) for row in orders) or "заказов нет"
    hint_lines = "\n".join(
        f"{row['item']}: последний раз {row['last_on']}, покупок {row['times']}" for row in hints
    ) or "подсказок нет"
    tail = f"\n{extra}" if extra else ""
    return (
        "Ты помощник по заказам. Отвечай по-русски.\n"
        "Если нужно действие, ответь одним JSON без пояснений.\n"
        "orders_add только когда человек просит записать покупку. Вопрос и «не покупал» записью не являются.\n"
        "Товар в JSON бери из сообщения, пример не копируй.\n"
        '{"tool":"orders_add","item":"товар из сообщения","qty":1}\n'
        '{"tool":"orders_search","query":"что искать"}\n'
        '{"tool":"orders_list"}\n'
        '{"tool":"buy_hints"}\n'
        "Иначе ответь текстом без JSON.\n"
        f"Заказов всего: {len(orders)}. Блок «Пора купить» — не весь список.\n"
        "На просьбу показать заказы перечисли каждый, строка на заказ: дата, товар, количество.\n"
        f"Покупатель: {user['name']}\n"
        f"История:\n{history}\n"
        f"Пора купить:\n{hint_lines}\n"
        f"Прайс из файла catalog/supplier.txt:\n{catalog_text()}\n"
        f"Сообщение: {text}{tail}"
    )


def read_tool(text):
    raw = (text or "").strip()
    start = raw.find("{")
    end = raw.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        data = json.loads(raw[start : end + 1])
    except json.JSONDecodeError:
        return None
    if not isinstance(data, dict) or data.get("tool") not in TOOLS:
        return None
    return data


def parse_review(text):
    findings = [
        {"severity": match.group(1).lower(), "message": (match.group(2) or "").strip()}
        for match in FINDING.finditer(text or "")
    ]
    clean = bool(re.search(r"(?i)(?<![a-z])clean(?![a-z])", text or ""))
    return findings, clean


def decide(findings, clean):
    highs = [item for item in findings if item["severity"] in ("critical", "high")]
    rest = [item for item in findings if item["severity"] in ("medium", "low")]
    if highs:
        return "fix", highs
    if rest:
        return "warn", rest
    if clean:
        return "clean", []
    return "unclear", []


def answer_of(payload):
    if not isinstance(payload, dict):
        return ""
    error = payload.get("error")
    if isinstance(error, dict) and error.get("message"):
        return error["message"]
    choices = payload.get("choices") or []
    if choices and isinstance(choices[0], dict):
        message = choices[0].get("message") or {}
        return message.get("content") or ""
    return ""


def complete(box, content, ip):
    status, payload = box.handle(
        {"model": "deepseek-v4-flash", "messages": [{"role": "user", "content": content}]},
        ip,
        "mask",
    )
    gateway = payload.get("gateway") if isinstance(payload, dict) else {}
    if not isinstance(gateway, dict):
        gateway = {}
    return {
        "status": status,
        "text": answer_of(payload),
        "action": gateway.get("action") or "",
        "sent": bool(gateway.get("sent_to_model")),
        "kinds": gateway.get("kinds") or [],
    }


def run_tool(store, user, call, today):
    name = call["tool"]
    if name == "orders_list":
        return store.orders(user["id"])
    if name == "orders_search":
        query = str(call.get("query") or "").strip()
        if not query:
            return {"error": "Пустой поиск."}
        return store.search(user["id"], query)
    if name == "buy_hints":
        return store.hints(user["id"], today)
    if name == "orders_add":
        item = str(call.get("item") or "").strip()
        qty = call.get("qty") or 1
        try:
            qty = int(qty)
        except (TypeError, ValueError):
            return {"error": "Количество не число."}
        if not item or qty < 1:
            return {"error": "Нужны товар и количество."}
        return {"pending": {"item": item, "qty": qty}}
    return {"error": "Нет такого инструмента."}


def talk(store, user, text, box, ip, today=None):
    moment = today or date.today()
    orders = store.orders(user["id"])
    hints = store.hints(user["id"], moment)
    first = complete(box, build_prompt(user, orders, hints, text), ip)
    if first["status"] != 200 or first["action"] == "blocked":
        return _stopped(first)
    call = read_tool(first["text"])
    if call is None:
        return _show_text(first["text"])
    result = run_tool(store, user, call, moment)
    if call["tool"] == "orders_add" and isinstance(result, dict) and result.get("pending"):
        pending = result["pending"]
        if not _may_save(text, pending["item"]):
            follow = complete(
                box,
                build_prompt(
                    user,
                    orders,
                    hints,
                    text,
                    "Запись не сделана: человек не просил записать этот товар. Ответь на сообщение по истории, без JSON.",
                ),
                ip,
            )
            if follow["status"] != 200 or follow["action"] == "blocked":
                return _stopped(follow)
            shown = _show_text(follow["text"])
            shown["committed"] = False
            return shown
        verdict, warning = _review(box, ip, f"{pending['item']} x {pending['qty']}")
        if verdict == "clean" or verdict == "warn":
            saved = store.add_order(user["id"], pending["item"], pending["qty"], "", moment.isoformat())
            return {
                "reply": f"Записала: {saved['item']}, {saved['qty']} шт.",
                "stopped": False,
                "committed": True,
                "warning": warning,
                "tool": "orders_add",
            }
        return {
            "reply": "Проверка не пропустила запись заказа.",
            "stopped": True,
            "committed": False,
            "warning": warning,
            "tool": "orders_add",
        }
    follow = complete(
        box,
        build_prompt(
            user,
            orders,
            hints,
            text,
            "Результат инструмента "
            + json.dumps(result, ensure_ascii=False)
            + "\n"
            + (
                "Перечисли каждый заказ из результата отдельной строкой: дата, товар, количество. Ничего не пропускай."
                if call["tool"] == "orders_list"
                else "Ответь человеку по результату, без JSON."
            ),
        ),
        ip,
    )
    if follow["status"] != 200 or follow["action"] == "blocked":
        return _stopped(follow)
    shown = _show_text(follow["text"])
    shown["tool"] = call["tool"]
    return shown


def _may_save(text, item):
    folded = text.casefold()
    if not item or item.casefold() not in folded:
        return False
    if NEGATION.search(text):
        return False
    return RECORD.search(text) is not None


def _review(box, ip, text):
    result = complete(box, SECURITY + text, ip)
    if result["status"] != 200 or result["action"] == "blocked":
        return "blocked", result["text"]
    findings, clean = parse_review(result["text"])
    verdict, items = decide(findings, clean)
    warning = "; ".join(item["message"] for item in items if item["message"])
    return verdict, warning


def _show_text(text):
    return {
        "reply": text,
        "stopped": False,
        "committed": False,
        "warning": "",
        "tool": "",
    }


def _stopped(result):
    return {
        "reply": result["text"] or "Запрос остановлен.",
        "stopped": True,
        "committed": False,
        "warning": "",
        "tool": "",
        "kinds": result["kinds"],
        "sent": result["sent"],
    }
