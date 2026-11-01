"""Короткий отчёт из summary.json. Пишется в конце прогона."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "output"


def load(path):
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def cell(block, key):
    if not block:
        return "—"
    return str(block.get(key, "—"))


def row(title, block):
    if not block:
        return f"<tr><td>{title}</td><td colspan='6'>—</td></tr>"
    hit = f"{block['hit']} / {block['n']}"
    return (
        "<tr>"
        f"<td>{title}</td>"
        f"<td>{hit}</td>"
        f"<td>{block['part']} / {block['n']}</td>"
        f"<td>{block['bus']} / {block['n']}</td>"
        f"<td>{block['voltage']} / {block['n']}</td>"
        f"<td>{block['seconds_each']}</td>"
        f"<td>{block['prompt_tokens']} / {block['new_tokens']}</td>"
        "</tr>"
    )


def table(summary, label):
    if not summary:
        return ""
    body = []
    for mode, title in (("bare", "0.5B до"), ("tuned", "0.5B после"), ("teacher", "Учитель 7B")):
        if mode not in summary:
            continue
        body.append(row(f"{title}, знакомые", summary[mode].get("seen")))
        body.append(row(f"{title}, не видела", summary[mode].get("held")))
    return (
        f"<h2>{label}</h2><table><thead><tr>"
        "<th></th><th>Верно</th><th>Деталь</th><th>Шина</th><th>Питание</th>"
        "<th>Секунд</th><th>Токены вход / ответ</th>"
        f"</tr></thead><tbody>{''.join(body)}</tbody></table>"
    )


def main():
    summary = load(OUT / "summary.json")
    first = load(OUT / "round1" / "summary.json")
    meta = load(OUT / "run_meta.json") or {}
    filt = load(ROOT / "dataset" / "filter_stats.json") or {}
    big = load(ROOT / "dataset" / "big_stats.json") or {}
    html = f"""<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<title>День 9. Ученик и учитель</title>
<style>
  body {{ margin: 0; background: #f3efe6; color: #1d1a16; font: 17px/1.55 Georgia, serif; }}
  main {{ max-width: 920px; padding: 48px 28px 80px; }}
  h1 {{ font-size: 40px; line-height: 1.1; font-weight: 600; }}
  table {{ width: 100%; border-collapse: collapse; background: #fffdf8; font: 15px/1.4 ui-sans-serif, system-ui, sans-serif; }}
  th, td {{ border-bottom: 1px solid #e3dcd0; text-align: left; padding: 8px 10px; }}
  th {{ font-size: 12px; letter-spacing: 0.03em; text-transform: uppercase; color: #6d655c; }}
  .meta {{ color: #6d655c; font: 14px/1.4 ui-sans-serif, system-ui, sans-serif; }}
</style>
</head>
<body>
<main>
<p class="meta">День 9 · Qwen2.5-0.5B ученик · Qwen2.5 7B учитель · s1, без видеокарты</p>
<h1>Ученик на карточках датчиков</h1>
<p>Один и тот же промпт и те же 60 контрольных вопросов. Верный ответ — совпали имя детали, шина и питание. Тестовые формулировки в обучение не входили.</p>
<p>Большой пул: {big.get('big_train', '—')} новых обучающих вопросов и {big.get('big_valid', '—')} проверочных. После фильтра учитель оставил {filt.get('kept_train', '—')} обучающих карточек и {filt.get('kept_valid', '—')} для valid. Отброшено {filt.get('rejected', '—')}.</p>
<p>Обучение: {meta.get('train_examples', '—')} примеров, {meta.get('epochs', '—')} эпох, {meta.get('train_seconds', '—')} секунд, train loss {meta.get('train_loss', '—')}.</p>
{table(first, 'Первый прогон, 28 карточек')}
{table(summary, 'Повторный прогон')}
<p>Входные токены почти одинаковые: промпт один. Экономия видна по секундам на ответ. Качество ученика сравнивается с ним же до обучения и с учителем.</p>
</main>
</body>
</html>
"""
    dest = ROOT / "day9.html"
    dest.write_text(html, encoding="utf-8")
    print(dest)


if __name__ == "__main__":
    main()
