package app.work.server.tool

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ToolArgsTest {
    @Test
    fun linkKeepsUppercaseHttpsAndTrimmedEdges() {
        assertEquals("HTTPS://example.com/doc", linkOf("HTTPS://example.com/doc").link())
        assertEquals("HTTPS://example.com/doc", linkOf(" HTTPS://example.com/doc ").link())
        assertEquals("https://example.com/doc", linkOf("https://example.com/doc").link())
        assertEquals("http://example.com/doc", linkOf("http://example.com/doc").link())
        assertEquals("https://example.com/doc", linkOf("  https://example.com/doc  ").link())
    }

    @Test
    fun linkRejectsWhitespaceInside() {
        val space = assertFailsWith<ToolInputError> {
            linkOf("https://ex ample.com").link()
        }
        assertEquals("В ссылке не должно быть пробелов", space.message)
        val line = assertFailsWith<ToolInputError> {
            linkOf("https://example.com/a\nb").link()
        }
        assertEquals("В ссылке не должно быть пробелов", line.message)
    }

    @Test
    fun linkRejectsAddressWithoutScheme() {
        val error = assertFailsWith<ToolInputError> {
            linkOf("example.com/doc").link()
        }
        assertEquals("Ссылка должна начинаться с http:// или https://", error.message)
    }

    @Test
    fun linkIsOptional() {
        assertNull(buildJsonObject { }.link())
    }

    @Test
    fun issueKeyTrimsEdgesAndRejectsInnerSpace() {
        assertEquals("APP-1", buildJsonObject { put("issue_key", JsonPrimitive(" APP-1 ")) }.issueKey())
        val inner = assertFailsWith<ToolInputError> {
            buildJsonObject { put("issue_key", JsonPrimitive("APP 1")) }.issueKey()
        }
        assertEquals("В номере задачи не должно быть пробелов", inner.message)
        val tooLong = assertFailsWith<ToolInputError> {
            buildJsonObject { put("issue_key", JsonPrimitive("A".repeat(41))) }.issueKey()
        }
        assertEquals("Слишком длинный номер задачи", tooLong.message)
        assertNull(buildJsonObject { put("issue_key", JsonPrimitive("  ")) }.issueKey())
        assertNull(buildJsonObject { }.issueKey())
    }

    @Test
    fun isoDateTrimsEdgesAndRejectsWords() {
        assertEquals(
            LocalDate(2026, 10, 25),
            buildJsonObject { put("on_date", JsonPrimitive(" 2026-10-25 ")) }.isoDate("on_date"),
        )
        val word = assertFailsWith<ToolInputError> {
            buildJsonObject { put("on_date", JsonPrimitive("завтра")) }.isoDate("on_date")
        }
        assertEquals("Поле on_date должно быть датой YYYY-MM-DD", word.message)
        val blank = assertFailsWith<ToolInputError> {
            buildJsonObject { put("on_date", JsonPrimitive("  ")) }.isoDate("on_date")
        }
        assertEquals("Пустое поле on_date", blank.message)
    }

    @Test
    fun summaryTrimsBeforeLengthCheck() {
        val trimmed = buildJsonObject {
            put("summary", JsonPrimitive(" " + "я".repeat(2_000)))
        }
        assertEquals("я".repeat(2_000), trimmed.summary())

        val tooLong = assertFailsWith<ToolInputError> {
            buildJsonObject {
                put("summary", JsonPrimitive("я".repeat(2_001)))
            }.summary()
        }
        assertEquals("Слишком длинное описание", tooLong.message)

        val blank = assertFailsWith<ToolInputError> {
            buildJsonObject {
                put("summary", JsonPrimitive("   "))
            }.summary()
        }
        assertEquals("Пустое поле summary", blank.message)
    }

    private fun linkOf(link: String) = buildJsonObject {
        put("link", JsonPrimitive(link))
    }
}
