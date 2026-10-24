package app.work.server.tool

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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

    private fun linkOf(link: String) = buildJsonObject {
        put("link", JsonPrimitive(link))
    }
}
