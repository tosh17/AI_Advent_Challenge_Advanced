# Ручка дня. Повтор с таймером

Промпт просит одну реализацию `GET /api/days/{date}`. Вторая копия не писалась: ручка уже лежит в дереве и совпадает с заданием. Файлы в этот прогон не менялись.

## Файлы

- `server/src/main/kotlin/app/work/server/activity/Days.kt` — `StoredDays.load` разбирает дату, зовёт `find` для `Log` и для `Schedule`. Беклог не запрашивается. Плохая дата — `BadDate`.
- `server/src/main/kotlin/app/work/server/Routes.kt` — `get("/days/{date}")` берёт параметр и отвечает `days.load(raw)`.
- `server/src/test/kotlin/app/work/server/DayRoutesTest.kt` — день с журналом и планом, пустой день, дата `2026-13-01` даёт `400` и «Неверная дата».

## Соответствие

Роут не вызывает `ActivityStore` и не разбирает дату. В ответе два поля, `log` и `schedule`. Тест проверяет, что «идея» из беклога не попадает ни в журнал, ни в план.
