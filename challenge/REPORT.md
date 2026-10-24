# Правила v2 и две генерации одного endpoint

Промпт оба раза один и тот же, без уточнений. Текст лежит в `prompt.txt`. Сборка после генерации запускалась отдельно, модель её не чинила.

Задача: `GET /api/days/{date}`. Дата `YYYY-MM-DD`. В ответе журнал (`Log`) и план (`Schedule`). Беклог не входит. Нужен тест.

## Генерация 1, правила v1

Скомпилировалось. `:server:test` прошёл, включая запрет `println`, SQL-строк и `.contains(` в агенте и роуте.

Роут сам ходил в базу:

```kotlin
get("/days/{date}") {
    val date = call.parameters["date"].orEmpty()
    call.respond(readDay(store, date))
}
```

`readDay`, разбор даты и `BadDate` лежали в `Routes.kt`. Обработчик `400` — в `Module.kt`. В двух тестах были свои копии `idleTurns` и свой `Database.connect` с JDBC URL. Беклог в фикстуре записан с датой, отсутствие проверялось только тем, что списки равны `ревью` и `созвон`.

Файлы: `challenge/gen1/`. Дифф к базе: `challenge/gen1.diff`.

## Генерация 2, правила v2

Скомпилировалось. `:server:test` и `:shared:jvmTest` прошли.

Роут больше не знает про базу:

```kotlin
get("/days/{date}") {
    val raw = call.parameters["date"].orEmpty()
    call.respond(days.load(raw))
}
```

`Days` и `StoredDays` в `activity/Days.kt`. Два `find` — там. `BadDate` объявлен рядом со `StatusPages` в `Module.kt`. `idleTurns` один на тестовый исходник. База открывается через `connect()`. Беклог записан с `onDate = null`, тест отдельно проверяет, что summary `идея` нет ни в журнале, ни в плане.

Файлы: `challenge/gen2/`. Дифф к базе: `challenge/gen2.diff`. Сравнение генераций: `challenge/gen1-vs-gen2.diff`.

Осталось не по шаблону: в `Routes.kt` импорты `app.work` стоят выше Ktor, как в старом файле. Строка JDBC URL в тесте всё ещё есть — `connect` принимает её аргументом.

## Что сильнее всего сдвинуло результат

1. Из v1 убрана формулировка «роут делегирует `Turns` или `ActivityStore`». Она разрешала передать базу в роут, и первая генерация так и сделала.
2. В v2 вставлен готовый вид ветки: параметр и `days.load(raw)`. Запрета без образца оказалось мало.
3. Исключение плохой даты привязано к `Module.kt`, к тем же `StatusPages`, что и остальные ошибки HTTP.
4. Тест описан конкретно: `connect()`, один `idleTurns`, беклог с `onDate = null`, явная проверка summary.

Скилл `add-kotlin-endpoint` и правило `.cursor/rules/http.mdc` обновлены вместе с текстом v2, иначе они спорили бы с ним. Промпт не менялся.

Текст v2, которым пользовались генерации, сохранён в `challenge/rules-v2.md`. Сейчас он лежит в `.cursorrules` и `.cursor/rules/project.mdc`. v1 сохранён в `challenge/rules-v1.md`.
