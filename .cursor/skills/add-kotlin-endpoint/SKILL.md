---
name: add-kotlin-endpoint
description: Adds a Ktor HTTP endpoint in the work assistant using ActivityStore, a thin route, and an H2 test. Use when adding an endpoint, route, GET/POST handler, or HTTP API in this project.
---

# Endpoint

1. Открой `Routes.kt` и `Module.kt`. Роут не вызывает `ActivityStore`.
2. Добавь интерфейс `Days` с `suspend fun load(rawDate: String): DayResponse`. Ветка роута: параметр и `call.respond(days.load(raw))`.
3. Реализация `load` разбирает `YYYY-MM-DD`, зовёт `find` для `Log` и `Schedule` и собирает DTO. Беклог не включает.
4. Плохая дата — исключение, объявленное в `Module.kt`. Там же ветка `StatusPages` на `400` и `ErrorBody`.
5. DTO — `@Serializable data class` в `Routes.kt`.
6. Тест открывает H2 через `connect()`. JDBC URL не копируй. `idleTurns` не дублируй. Беклог в фикстуре с `onDate = null`, summary беклога отсутствует в обоих списках.
7. Не ищи слова во входной фразе и не пиши SQL строкой.
