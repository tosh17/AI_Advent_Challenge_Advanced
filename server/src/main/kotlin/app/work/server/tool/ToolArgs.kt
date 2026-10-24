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
    val raw = text(name)
    return try {
        LocalDate.parse(raw)
    } catch (_: IllegalArgumentException) {
        throw ToolInputError("Поле $name должно быть датой YYYY-MM-DD")
    }
}

fun JsonObject.summary(): String {
    val value = text("summary")
    if (value.length > 2_000) throw ToolInputError("Слишком длинное описание")
    return value.trim()
}
