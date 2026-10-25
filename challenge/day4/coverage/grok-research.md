# Research. Дыры в покрытии

Код не менялся. Смотрел текущее дерево: `Routes.kt`, `Module.kt`, `WorkMcp.kt` и тесты в `server/src/test`.

## Файлы

- `server/src/main/kotlin/app/work/server/Routes.kt`
- `server/src/main/kotlin/app/work/server/Module.kt` — статика `web`
- `server/src/main/kotlin/app/work/server/mcp/WorkMcp.kt` — `POST /mcp`
- Тесты: `RoutesTest`, `DayRoutesTest`, `StatusRoutesTest`, `McpToolsTest`. Файла `SprintRoutesTest.kt` в дереве нет.

## Связи

Роут берёт параметр или тело и зовёт один метод. День идёт в `Days.load`, спринт в `Sprint.load`, сообщение в `Turns.message`.

## Эндпоинты

| Эндпоинт | Тест |
| --- | --- |
| `GET /api/health` | теста нет |
| `GET /api/days/{date}` | `DayRoutesTest.returnsLogAndScheduleForTheDay`, `DayRoutesTest.rejectsBadDate` |
| `GET /api/sprint` | теста нет |
| `POST /api/sessions` | `RoutesTest.passesTextThrough` |
| `POST /api/sessions/{id}/messages` | успех: `RoutesTest.passesTextThrough`; `400`: `StatusRoutesTest.blankMessageIs400`; `404`: `StatusRoutesTest.unknownSessionIs404`; `502`: `StatusRoutesTest.modelFailureIs502` |
| `GET /` | `RoutesTest.chatPage` |
| `GET /chat.js`, `GET /chat.css` | теста нет |
| `POST /mcp` | `McpToolsTest.journalGoesThroughMcp` |

## Выводы

Без теста три места: `GET /api/health`, `GET /api/sprint`, статика `chat.js` и `chat.css`. Остальные маршруты из `Routes.kt` и `POST /mcp` закрыты.
