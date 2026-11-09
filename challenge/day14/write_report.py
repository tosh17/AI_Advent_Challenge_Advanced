import html
import json
from pathlib import Path

import loop

ROOT = Path(__file__).resolve().parent
REPORT = ROOT.parents[1] / "report" / "day14.html"


def write(results, audit):
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    log_dir = ROOT / "logs"
    log_dir.mkdir(exist_ok=True)
    (log_dir / "loop.jsonl").write_text(
        "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in results),
        encoding="utf-8",
    )
    (log_dir / "audit.jsonl").write_text(
        "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in audit),
        encoding="utf-8",
    )
    REPORT.write_text(page(results, audit), encoding="utf-8")
    return REPORT


def page(results, audit):
    audit_text = "\n".join(json.dumps(row, ensure_ascii=False) for row in audit)
    return f"""<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<title>День 14. Security step</title>
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
  <h1>День 14. Security step</h1>
  <p>Цикл короткий: промпт, код, py_compile и проверка поведения, затем отдельный вызов на проверку безопасности, и только после неё коммит. Critical и High возвращают цикл к генерации с текстом «исправь: …». Medium и Low пишут предупреждение в лог и коммит всё равно делается. Чистое заключение коммитит без предупреждения. Генерация и проверка идут через прокси дня 13 в режиме block. Файл <code>secrets.env</code> шлюз останавливает до модели, в промпт генерации он не попадает. Ответ проверки — одно слово, потому что шлюз подставляет свой короткий системный промпт.</p>
  <h2>Промпт под стек</h2>
  <p>Kotlin, Ktor, Exposed, SLF4J: ключ в коде, персональные данные и тело запроса в логе, HTTP вместо HTTPS, склейка SQL, нет проверки входа. Компилятора Kotlin в системе нет, поэтому сам файл — короткий Python, а правила те же.</p>
  <pre>{html.escape(loop.SECURITY_PROMPT.strip())}</pre>
  <h2>Три задачи</h2>
  <table>
    <thead>
      <tr><th>Задача</th><th>Шлюз</th><th>Security step</th><th>Пропустили оба</th><th>Коммит</th></tr>
    </thead>
    <tbody>
{"".join(tr(row) for row in results)}
    </tbody>
  </table>
  {"".join(attempt_block(row) for row in results)}
  <h2>Лог шлюза</h2>
  <pre>{html.escape(audit_text)}</pre>
</main>
</body>
</html>
"""


def tr(row):
    commit = row["commit"][:8] if row.get("committed") else "нет"
    warning = f"<br>{html.escape(row['warning'])}" if row.get("warning") else ""
    return (
        "<tr><td>"
        + html.escape(row["title"])
        + "</td><td>"
        + lines(row.get("caught_gateway"))
        + "</td><td>"
        + lines(row.get("caught_security"))
        + "</td><td>"
        + lines(row.get("missed_both"), empty="—")
        + "</td><td><code>"
        + html.escape(commit)
        + "</code>"
        + warning
        + "</td></tr>\n"
    )


def lines(items, empty="—"):
    if not items:
        return empty
    return "<br>".join(html.escape(item) for item in items)


def attempt_block(row):
    parts = [f"<h2>{html.escape(row['title'])}</h2>"]
    for attempt in row["attempts"]:
        stage = attempt.get("stage")
        if stage == "test":
            mark = "тест прошёл" if attempt.get("ok") else "тест упал"
            parts.append(f"<p>{mark}. {html.escape(attempt.get('text') or '')}</p>")
            continue
        header = f"{stage}: HTTP {attempt.get('status')} {attempt.get('action') or ''}".strip()
        parts.append(f"<p>{html.escape(header)}</p>")
        if attempt.get("code"):
            parts.append(f"<pre>{html.escape(attempt['code'])}</pre>")
        elif attempt.get("text"):
            parts.append(f"<pre>{html.escape(attempt['text'])}</pre>")
    if row.get("final_code"):
        parts.append("<p>В коммит ушло:</p>")
        parts.append(f"<pre>{html.escape(row['final_code'])}</pre>")
    return "\n".join(parts) + "\n"
