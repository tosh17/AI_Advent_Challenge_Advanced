# День 4. Логи прогона

Модель в запросе: `tosh_local`. Сервер в ответе: `qwen-local`. Адрес: `https://tosh17.duckdns.org:8787/v1/chat/completions`. Температура `0.2`. `chat_template_kwargs.enable_thinking` = false. Ключ в эти файлы не записывался.

Промпты одни и те же. Менялся только `max_tokens`.

## Первый проход

Потолки: чат 800, Research 2500, ручка 6000. Research закончился с пустым `content` и finish `length`.

| Файл | Что внутри |
| --- | --- |
| `system-prompt.txt` | Правила в system |
| `prompt-chat.txt`, `prompt-day1.txt`, `prompt-research.txt` | Три задания |
| `local-chat.json`, `local-day1.json`, `local-research.json` | Время, HTTP, finish, usage |
| `local-timings.json` | Сводка |

## Повтор, max_tokens 30000

Каталог `max30000/`. Все три вызова finish `stop`, текст ответа есть.

| Файл | Что внутри |
| --- | --- |
| `max30000/timings.json` | 10,082 с / 106,544 с / 202,421 с |
| `max30000/local-chat.txt` | «ок» |
| `max30000/local-research.txt` | список эндпоинтов |
| `max30000/local-day1.txt` | сгенерированная ручка, в дерево не вносилась |
| `max30000/local-*-reasoning.txt` | рассуждение модели |

Ответы Grok на те же промпты: `grok-chat.txt`, `grok-research.md`, `grok-day1.md`.

## Профили поиска и починки

Две поломки: ссылка без обрезки и без `ignoreCase`, спринт читает `Log`. Каталог `profiles/`.

| Файл | Что внутри |
| --- | --- |
| `profiles/buggy/` | Код с двумя поломками |
| `profiles/grok-tests-red.txt` | 20,39 с, 2 из 4 упали |
| `profiles/grok-research.md`, `profiles/grok-bugfix.md` | Ответы Grok |
| `profiles/grok-tests-green.txt` | 8,47 с, `BUILD SUCCESSFUL` |
| `profiles/local-research.txt`, `profiles/local-bugfix.txt` | Ответы `tosh_local`, 31,11 с и 44,99 с |
| `profiles/local-tests.txt` | Правка локальной модели: 6,89 с, ссылка с пробелами всё ещё падает |
| `profiles/local-timings.json` | HTTP, finish, usage |

После прогона `ToolArgs.kt` и `Sprint.kt` возвращены к исходному виду.

Повтор Grok с секундомером: `grok-rerun/`. Чат 5,406 с, Research 9,670 с, ручка 13,724 с. Локальный HTTP-прогон заново не запускался.

Отчёт: `report/day4.html`.
