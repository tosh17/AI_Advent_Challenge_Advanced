"""Сверка ответа с карточкой детали. Плохой ответ учителя сюда же не проходит."""

import json
import re


def parse_obj(text):
    if not text:
        return None
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end <= start:
        return None
    chunk = text[start : end + 1]
    try:
        obj = json.loads(chunk)
    except json.JSONDecodeError:
        return None
    if not isinstance(obj, dict):
        return None
    return obj


def fold(text):
    return str(text).casefold().replace("ё", "е")


def norm_part(text):
    return re.sub(r"[^a-z0-9]+", "", fold(text))


def norm_bus(text):
    raw = fold(text).replace(" ", "")
    table = {
        "i2c": "i2c",
        "iic": "i2c",
        "i²c": "i2c",
        "spi": "spi",
        "uart": "uart",
        "serial": "uart",
        "ttl": "uart",
        "analog": "analog",
        "adc": "analog",
        "digital": "digital",
        "gpio": "digital",
        "pwm": "pwm",
        "onewire": "onewire",
        "1wire": "onewire",
        "1-wire": "onewire",
        "one-wire": "onewire",
    }
    return table.get(raw)


def norm_voltage(text):
    raw = fold(text).replace(" ", "").replace("вольт", "").replace("v", "").replace("в", "")
    raw = raw.replace("–", "-").replace("—", "-").replace(",", ".")
    if raw in ("3.3-5", "5-3.3", "3.3to5", "3.3...5", "3.3~5"):
        return "3.3-5"
    if raw in ("3.3", "3.30"):
        return "3.3"
    if raw in ("5", "5.0"):
        return "5"
    if raw in ("3.7", "3.70", "4.0", "3.4-4.4", "3.4-4.2"):
        return "3.7"
    return None


def part_ok(pred, card):
    got = norm_part(pred)
    if not got:
        return False
    names = [card["part"], *card.get("aliases", [])]
    return any(norm_part(name) == got or norm_part(name) in got for name in names)


def library_ok(pred, card):
    got = fold(pred)
    return any(alias in got for alias in card["library_ok"])


def judge(text, card):
    obj = parse_obj(text)
    if obj is None:
        return {"json_ok": False, "part_ok": False, "bus_ok": False, "voltage_ok": False, "library_ok": False, "hit": False}
    bus = norm_bus(obj.get("bus", ""))
    voltage = norm_voltage(obj.get("voltage", ""))
    part = part_ok(obj.get("part", ""), card)
    library = library_ok(obj.get("library", ""), card)
    hit = part and bus == card["bus"] and voltage == card["voltage"]
    return {
        "json_ok": True,
        "part_ok": part,
        "bus_ok": bus == card["bus"],
        "voltage_ok": voltage == card["voltage"],
        "library_ok": library,
        "hit": hit,
    }
