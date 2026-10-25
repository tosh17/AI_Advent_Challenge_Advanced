# Research. Повтор с таймером

Код не менялся. Вопрос: какие HTTP-эндпоинты есть и какие не покрыты тестами.

## Файлы

- `server/src/main/kotlin/app/work/server/Routes.kt` — пять ручек под `/api`.
- `server/src/main/kotlin/app/work/server/Module.kt` — статика `web`.
- `server/src/main/kotlin/app/work/server/mcp/WorkMcp.kt` — `POST /mcp`.

## Связи

Роут берёт параметр или тело и зовёт один метод. День идёт в `Days.load`, спринт в `Sprint.load`, сообщение в `Turns.message`.

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

У каждого маршрута из `Routes.kt` есть тест. Страница, стили, скрипт и `POST /mcp` тоже закрыты. Отдельной HTTP-ручки поиска нет: поиск проверяет `ActivitySearchTest` на хранилище.
