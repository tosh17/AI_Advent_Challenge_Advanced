"""Вопросы про детали для Arduino.

Тестовые формулировки не повторяют обучающие. Шесть деталей есть только в тесте:
их учитель отвечает на проверке, ученик в обучении не видит.
"""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "dataset"

PARTS = [
    {
        "part": "DHT11",
        "aliases": ["dht11"],
        "measures": "температура и влажность",
        "bus": "digital",
        "voltage": "3.3-5",
        "library": "DHT",
        "library_ok": ["dht"],
    },
    {
        "part": "DHT22",
        "aliases": ["dht22", "am2302"],
        "measures": "температура и влажность",
        "bus": "digital",
        "voltage": "3.3-5",
        "library": "DHT",
        "library_ok": ["dht"],
    },
    {
        "part": "DS18B20",
        "aliases": ["ds18b20"],
        "measures": "температура",
        "bus": "onewire",
        "voltage": "3.3-5",
        "library": "DallasTemperature",
        "library_ok": ["dallas", "onewire"],
    },
    {
        "part": "BMP280",
        "aliases": ["bmp280"],
        "measures": "давление и температура",
        "bus": "i2c",
        "voltage": "3.3",
        "library": "Adafruit_BMP280",
        "library_ok": ["bmp280"],
    },
    {
        "part": "BME280",
        "aliases": ["bme280"],
        "measures": "давление, температура и влажность",
        "bus": "i2c",
        "voltage": "3.3",
        "library": "Adafruit_BME280",
        "library_ok": ["bme280"],
    },
    {
        "part": "HC-SR04",
        "aliases": ["hcsr04"],
        "measures": "расстояние",
        "bus": "digital",
        "voltage": "5",
        "library": "NewPing",
        "library_ok": ["newping", "hcsr04", "ultrasonic"],
    },
    {
        "part": "HC-SR501",
        "aliases": ["hcsr501", "pir"],
        "measures": "движение",
        "bus": "digital",
        "voltage": "5",
        "library": "нет",
        "library_ok": ["нет", "digitalread"],
    },
    {
        "part": "MPU6050",
        "aliases": ["mpu6050"],
        "measures": "ускорение и гироскоп",
        "bus": "i2c",
        "voltage": "3.3-5",
        "library": "Adafruit_MPU6050",
        "library_ok": ["mpu6050"],
    },
    {
        "part": "SSD1306",
        "aliases": ["ssd1306"],
        "measures": "дисплей",
        "bus": "i2c",
        "voltage": "3.3-5",
        "library": "Adafruit_SSD1306",
        "library_ok": ["ssd1306"],
    },
    {
        "part": "SG90",
        "aliases": ["sg90"],
        "measures": "сервопривод",
        "bus": "pwm",
        "voltage": "5",
        "library": "Servo",
        "library_ok": ["servo"],
    },
    {
        "part": "WS2812B",
        "aliases": ["ws2812b", "ws2812"],
        "measures": "адресная лента",
        "bus": "digital",
        "voltage": "5",
        "library": "Adafruit_NeoPixel",
        "library_ok": ["neopixel", "fastled", "ws2812"],
    },
    {
        "part": "INA219",
        "aliases": ["ina219"],
        "measures": "ток и напряжение",
        "bus": "i2c",
        "voltage": "3.3-5",
        "library": "Adafruit_INA219",
        "library_ok": ["ina219"],
    },
    {
        "part": "ADS1115",
        "aliases": ["ads1115"],
        "measures": "аналоговый ввод",
        "bus": "i2c",
        "voltage": "3.3-5",
        "library": "Adafruit_ADS1X15",
        "library_ok": ["ads1115", "ads1x15"],
    },
    {
        "part": "MFRC522",
        "aliases": ["mfrc522", "rc522"],
        "measures": "rfid",
        "bus": "spi",
        "voltage": "3.3",
        "library": "MFRC522",
        "library_ok": ["mfrc522", "rc522"],
    },
    {
        "part": "NRF24L01",
        "aliases": ["nrf24l01", "nrf24"],
        "measures": "радио",
        "bus": "spi",
        "voltage": "3.3",
        "library": "RF24",
        "library_ok": ["rf24", "nrf24"],
    },
    {
        "part": "NEO-6M",
        "aliases": ["neo6m", "neo-6m"],
        "measures": "gps",
        "bus": "uart",
        "voltage": "3.3-5",
        "library": "TinyGPSPlus",
        "library_ok": ["tinygps", "neo6"],
    },
    {
        "part": "DS3231",
        "aliases": ["ds3231"],
        "measures": "часы",
        "bus": "i2c",
        "voltage": "3.3-5",
        "library": "RTClib",
        "library_ok": ["rtclib", "ds3231"],
    },
    {
        "part": "DS1307",
        "aliases": ["ds1307"],
        "measures": "часы",
        "bus": "i2c",
        "voltage": "5",
        "library": "RTClib",
        "library_ok": ["rtclib", "ds1307"],
    },
    {
        "part": "MQ-2",
        "aliases": ["mq2", "mq-2"],
        "measures": "газ",
        "bus": "analog",
        "voltage": "5",
        "library": "нет",
        "library_ok": ["нет", "analogread"],
    },
    {
        "part": "LCD1602",
        "aliases": ["lcd1602", "1602"],
        "measures": "дисплей",
        "bus": "i2c",
        "voltage": "5",
        "library": "LiquidCrystal_I2C",
        "library_ok": ["liquidcrystal"],
    },
    {
        "part": "A4988",
        "aliases": ["a4988"],
        "measures": "шаговый двигатель",
        "bus": "digital",
        "voltage": "3.3-5",
        "library": "AccelStepper",
        "library_ok": ["accelstepper", "a4988"],
    },
    {
        "part": "HX711",
        "aliases": ["hx711"],
        "measures": "вес",
        "bus": "digital",
        "voltage": "3.3-5",
        "library": "HX711",
        "library_ok": ["hx711"],
    },
    {
        "part": "BH1750",
        "aliases": ["bh1750"],
        "measures": "освещённость",
        "bus": "i2c",
        "voltage": "3.3-5",
        "library": "BH1750",
        "library_ok": ["bh1750"],
    },
    {
        "part": "LM35",
        "aliases": ["lm35"],
        "measures": "температура",
        "bus": "analog",
        "voltage": "5",
        "library": "нет",
        "library_ok": ["нет", "analogread"],
    },
    {
        "part": "VL53L0X",
        "aliases": ["vl53l0x"],
        "measures": "расстояние",
        "bus": "i2c",
        "voltage": "3.3",
        "library": "Adafruit_VL53L0X",
        "library_ok": ["vl53"],
        "held": True,
    },
    {
        "part": "MAX30102",
        "aliases": ["max30102"],
        "measures": "пульс и кислород",
        "bus": "i2c",
        "voltage": "3.3",
        "library": "MAX3010x",
        "library_ok": ["max3010"],
        "held": True,
    },
    {
        "part": "APDS9960",
        "aliases": ["apds9960"],
        "measures": "жест и цвет",
        "bus": "i2c",
        "voltage": "3.3",
        "library": "Adafruit_APDS9960",
        "library_ok": ["apds9960", "apds-9960"],
        "held": True,
    },
    {
        "part": "TCS34725",
        "aliases": ["tcs34725"],
        "measures": "цвет",
        "bus": "i2c",
        "voltage": "3.3-5",
        "library": "Adafruit_TCS34725",
        "library_ok": ["tcs34725", "tcs3472"],
        "held": True,
    },
    {
        "part": "PN532",
        "aliases": ["pn532"],
        "measures": "nfc",
        "bus": "i2c",
        "voltage": "3.3",
        "library": "Adafruit_PN532",
        "library_ok": ["pn532"],
        "held": True,
    },
    {
        "part": "SIM800L",
        "aliases": ["sim800l", "sim800"],
        "measures": "gsm",
        "bus": "uart",
        "voltage": "3.7",
        "library": "нет",
        "library_ok": ["нет", "sim800", "softwareserial"],
        "held": True,
    },
]


def gold_of(card):
    return {
        "part": card["part"],
        "measures": card["measures"],
        "bus": card["bus"],
        "voltage": card["voltage"],
        "library": card["library"],
    }


def train_questions(part):
    return [
        f"Как подключить {part} к Arduino для поделки?",
        f"Какая шина и какое питание у модуля {part}?",
        f"Какую библиотеку ставить для {part}?",
        f"Что умеет {part} и от скольких вольт его питать?",
    ]


def valid_question(part):
    return f"Дай карточку {part}: что меряет, шина, питание, библиотека."


def test_questions(part):
    return [
        f"Собираю схему с {part}. Напиши шину, питание и библиотеку.",
        f"В ящике лежит {part}. На каком напряжении он живёт и как с ним говорит Arduino?",
    ]


def row(split, card, user):
    return {
        "split": split,
        "seen": not card.get("held", False),
        "part": card["part"],
        "user": user,
        "gold": gold_of(card),
        "card": card,
    }


def main():
    DATA.mkdir(parents=True, exist_ok=True)
    buckets = {"train": [], "valid": [], "test": []}
    seen = set()
    for card in PARTS:
        held = card.get("held", False)
        questions = test_questions(card["part"]) if held else train_questions(card["part"])
        target = "test" if held else "train"
        for user in questions:
            if user in seen:
                raise SystemExit(f"повтор вопроса: {user}")
            seen.add(user)
            buckets[target].append(row(target, card, user))
        if not held:
            user = valid_question(card["part"])
            if user in seen:
                raise SystemExit(f"повтор вопроса: {user}")
            seen.add(user)
            buckets["valid"].append(row("valid", card, user))
            for user in test_questions(card["part"]):
                if user in seen:
                    raise SystemExit(f"повтор вопроса: {user}")
                seen.add(user)
                buckets["test"].append(row("test", card, user))

    def write(name, rows):
        for index, item in enumerate(rows, start=1):
            item["id"] = f"{name}-{index:03d}"
            item.pop("card", None)
        text = "\n".join(json.dumps(item, ensure_ascii=False) for item in rows) + "\n"
        (DATA / f"{name}.jsonl").write_text(text, encoding="utf-8")

    write("pool_train", buckets["train"])
    write("pool_valid", buckets["valid"])
    write("test", buckets["test"])
    stats = {
        "parts": len(PARTS),
        "parts_held": sum(1 for card in PARTS if card.get("held")),
        "pool_train": len(buckets["train"]),
        "pool_valid": len(buckets["valid"]),
        "test": len(buckets["test"]),
        "test_seen": sum(1 for item in buckets["test"] if item["seen"]),
        "test_held": sum(1 for item in buckets["test"] if not item["seen"]),
    }
    (DATA / "stats.json").write_text(json.dumps(stats, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(stats, ensure_ascii=False))


if __name__ == "__main__":
    main()
