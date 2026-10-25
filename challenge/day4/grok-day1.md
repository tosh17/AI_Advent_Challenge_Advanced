# День 1, endpoint. Grok, этот чат

Промпт тот же, что в `prompt-day1.txt`. Вторая реализация не писалась: ручка уже лежит в ветке `day4` и совпадает с заданием. В этот прогон файлы не переписывались.

## Файлы

- `server/src/main/kotlin/app/work/server/activity/Days.kt` — `StoredDays.load` разбирает дату, зовёт `find` для `Log` и для `Schedule`, собирает `DayResponse`. Беклог не запрашивается. Плохая дата — `BadDate`.
- `server/src/main/kotlin/app/work/server/Routes.kt` — `get("/days/{date}")` берёт параметр и отвечает `days.load(raw)`.
- `server/src/test/kotlin/app/work/server/DayRoutesTest.kt` — день с журналом и планом, пустой день, дата `2026-13-01`.
- `Module.kt` — `BadDate` снаружи даёт `400` и `ErrorBody`.

## Соответствие правилам

Роут не вызывает `ActivityStore` и не разбирает дату. Вид активности задают два вызова `find`. Тест открывает данные через хранилище, JDBC URL в тесте ручки не скопирован.
