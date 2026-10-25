# Bug Fix. Grok

## Нашёл

`ToolArgsTest.linkKeepsUppercaseHttpsAndTrimmedEdges` падает на `HTTPS://example.com/doc`: `ToolArgs.link` не обрезает края и сравнивает схему с учётом регистра. `SprintRoutesTest.returnsOnlySprintActivities` ждёт `[ревью]`, получает `[созвон]`: `StoredSprint.load` читает `ActivityKind.Log`.

Красный прогон: 20,39 с, 4 теста, 2 упали. Лог: `grok-tests-red.txt`.

## Починил

В `JsonObject.link` значение снова обрезается, схема `http://` и `https://` принимается без учёта регистра. В `StoredSprint.load` список снова берётся как `ActivityKind.Sprint`. Новых тестов не добавлял: оба симптома уже закрыты существующими.

## Проверил

`./gradlew :server:test --tests app.work.server.tool.ToolArgsTest --tests app.work.server.SprintRoutesTest` — `BUILD SUCCESSFUL`, 8,47 с. Лог: `grok-tests-green.txt`.
