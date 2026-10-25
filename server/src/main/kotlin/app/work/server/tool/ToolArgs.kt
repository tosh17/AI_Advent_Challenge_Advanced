package app.work.server.tool

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class ToolInputError(message: String) : IllegalArgumentException(message)

fun JsonObject.text(name: String): String {
    val value = this[name] ?: throw ToolInputError("Нужно поле $name")
    if (value is JsonNull) throw ToolInputError("Нужно поле $name")
    val text = value.jsonPrimitive.content
    if (text.isBlank()) throw ToolInputError("Пустое поле $name")
    return text
}

fun JsonObject.optionalText(name: String): String? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return value.jsonPrimitive.content.takeIf { it.isNotBlank() }
}

fun JsonObject.isoDate(name: String): LocalDate {
    val raw = text(name).trim()
    return try {
        LocalDate.parse(raw)
    } catch (_: IllegalArgumentException) {
        throw ToolInputError("Поле $name должно быть датой YYYY-MM-DD")
    }
}

fun JsonObject.summary(): String {
    val value = text("summary").trim()
    if (value.isEmpty()) throw ToolInputError("Пустое поле summary")
    if (value.length > 2_000) throw ToolInputError("Слишком длинное описание")
    return value
}

fun JsonObject.issueKey(): String? {
    val value = optionalText("issue_key")?.trim() ?: return null
    if (value.isEmpty()) return null
    if (value.length > 40) throw ToolInputError("Слишком длинный номер задачи")
    if (value.any { it.isWhitespace() }) throw ToolInputError("В номере задачи не должно быть пробелов")
    return value
}

fun JsonObject.link(): String? {
    val value = optionalText("link")?.trim() ?: return null
    if (value.length > 2_000) throw ToolInputError("Слишком длинная ссылка")
    if (!value.startsWith("https://", ignoreCase = true) && !value.startsWith("http://", ignoreCase = true)) {
        throw ToolInputError("Ссылка должна начинаться с http:// или https://")
    }
    return value
}
