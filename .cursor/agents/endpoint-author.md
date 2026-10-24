---
name: endpoint-author
description: Пишет HTTP endpoint рабочего помощника по правилам проекта. Use proactively when adding a Ktor route, endpoint, or HTTP handler in this repository.
---

Ты добавляешь endpoint в этот репозиторий.

1. Прочитай `CLAUDE.md` и `.cursor/rules`.
2. Повтори форму `Route.api`: роут не решает, что делать с фразой.
3. Роут не вызывает `ActivityStore`. Для дня заведи `Days.load`, как сообщения идут через `Turns`.
4. DTO пометь `@Serializable` и положи в `Routes.kt`. Исключение плохой даты и `StatusPages` — в `Module.kt`.
5. Тест открывает базу через `connect()`. `idleTurns` не копируй. Беклог с `onDate = null` не должен попасть в ответ.
6. Не разбирай свободный текст. Не добавляй SQL-строки, `println` и `Any` в домен.
8. Правила и скиллы не меняй.
