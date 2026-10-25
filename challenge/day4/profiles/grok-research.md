# Research. Grok

Код не менялся. Вопрос: почему падают `ToolArgsTest.linkKeepsUppercaseHttpsAndTrimmedEdges` и `SprintRoutesTest.returnsOnlySprintActivities`.

Прогон до правки: `challenge/day4/profiles/grok-tests-red.txt`, 20,39 с, 4 теста, 2 упали.

## Файлы

- `server/src/main/kotlin/app/work/server/tool/ToolArgs.kt` — `JsonObject.link`.
- `server/src/test/kotlin/app/work/server/tool/ToolArgsTest.kt` — строка 15, ссылка `HTTPS://example.com/doc`.
- `server/src/main/kotlin/app/work/server/activity/Sprint.kt` — `StoredSprint.load`.
- `server/src/test/kotlin/app/work/server/SprintRoutesTest.kt` — строка 42.

## Связи

`link()` читает поле `link` и проверяет схему. Тест ждёт, что `HTTPS://example.com/doc` и ссылка с пробелами по краям сохранятся. Сейчас значение не обрезается, а `startsWith("https://")` сравнивает схему с учётом регистра. `HTTPS://` не проходит проверку, метод бросает `ToolInputError`: «Ссылка должна начинаться с http:// или https://».

`GET /api/sprint` зовёт `Sprint.load`. `StoredSprint.load` берёт список через `store.list`. В этой копии вид списка — `ActivityKind.Log`. Тест кладёт в спринт «ревью», в журнал «созвон» и ждёт в ответе только «ревью». Пришло `[созвон]`.

## Выводы

Две независимые поломки. Ссылка с заглавной схемой и с пробелами по краям отклоняется в `ToolArgs.link`. Список спринта читает журнал, поэтому `GET /api/sprint` отдаёт записи `Log`. Остальные два теста этих классов в том же прогоне прошли.
