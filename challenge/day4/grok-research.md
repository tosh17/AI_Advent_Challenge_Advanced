# Research. Grok, этот чат

Один проход по тем же роутам и списку тестов, что лежали в `prompt-research.txt`. Код не менялся. Отдельного HTTP-таймера нет: ответ собран в этой сессии по дереву ветки `day4`.

## Файлы

- `server/src/main/kotlin/app/work/server/Routes.kt` — пять ручек под `/api`.
- `server/src/main/kotlin/app/work/server/Module.kt` — статика `web` и `POST /mcp`.
- Тесты: `RoutesTest`, `DayRoutesTest`, `SprintRoutesTest`, `StatusRoutesTest`, `McpToolsTest`.

## Связи

Роут принимает параметр или тело и зовёт один метод. `GET /api/days/{date}` идёт в `Days.load`. `GET /api/sprint` идёт в `Sprint.load`. Сообщение идёт в `Turns.message`.

## Эндпоинты

| Эндпоинт | Тест |
| --- | --- |
| `GET /api/health` | `RoutesTest.health` |
| `GET /api/days/{date}` | `DayRoutesTest.returnsLogAndScheduleForTheDay`, `DayRoutesTest.rejectsBadDate` |
| `GET /api/sprint` | `SprintRoutesTest.returnsOnlySprintActivities` |
| `POST /api/sessions` | `RoutesTest.passesTextThrough` |
| `POST /api/sessions/{id}/messages` | успех: `RoutesTest.passesTextThrough`; `400`: `StatusRoutesTest.blankMessageIs400`; `404`: `StatusRoutesTest.unknownSessionIs404`; `502`: `StatusRoutesTest.modelFailureIs502` |
| `GET /` | `RoutesTest.chatPage` |
| `GET /chat.js`, `GET /chat.css` | `StatusRoutesTest.chatAssetsAreServed` |
| `POST /mcp` | `McpToolsTest.journalGoesThroughMcp` |

## Выводы

У каждого маршрута из `Routes.kt` есть тест. Статика и журнал через MCP тоже закрыты. Отдельной HTTP-ручки поиска нет: поиск проверяет `ActivitySearchTest` на хранилище. Дыры, которые Research назвал на дне 2 (`400`/`404`/`502`, `chat.js`, `chat.css`, `SessionBook`), закрыты тестами дня 3. `SessionBookTest` ходит в книгу напрямую, без HTTP.
