package app.work.server

import app.work.domain.ActivityKind
import app.work.server.activity.ExposedActivityStore
import app.work.server.activity.connect
import app.work.server.confluence.cql
import app.work.server.mcp.McpToolCatalog
import app.work.server.mcp.workMcp
import app.work.server.tool.ToolInputError
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class McpToolsTest {
    @Test
    fun journalGoesThroughMcp() = runBlocking {
        val store = ExposedActivityStore(
            connect(
                url = "jdbc:h2:mem:mcp_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                user = "sa",
                password = "",
            ),
        )
        val port = ServerSocket(0).use { it.localPort }
        val engine = embeddedServer(Netty, host = "127.0.0.1", port = port) {
            workMcp(store, jira = null, confluence = null)
        }.start(wait = false)
        val http = HttpClient(CIO) { install(SSE) }
        try {
            val catalog = McpToolCatalog.connect(http, port)
            val names = catalog.definitions().map { it.name }.toSet()
            assertTrue("activities_log" in names)
            assertTrue("jira_add_to_current_sprint" in names)
            assertTrue("confluence_search" in names)

            catalog.call(
                "activities_log",
                kotlinx.serialization.json.buildJsonObject {
                    put("summary", kotlinx.serialization.json.JsonPrimitive("ревью"))
                    put("on_date", kotlinx.serialization.json.JsonPrimitive("2026-10-23"))
                },
            )
            val listed = catalog.call(
                "activities_list_day",
                kotlinx.serialization.json.buildJsonObject {
                    put("date", kotlinx.serialization.json.JsonPrimitive("2026-10-23"))
                },
            )
            assertTrue(listed.contains("ревью"))
            assertEquals(listOf("ревью"), store.find(ActivityKind.Log, kotlinx.datetime.LocalDate(2026, 10, 23)).map { it.summary })

            val jira = catalog.call(
                "jira_search",
                kotlinx.serialization.json.buildJsonObject {
                    put("jql", kotlinx.serialization.json.JsonPrimitive("order by created"))
                },
            )
            assertTrue(jira.startsWith("ошибка:"))
        } finally {
            http.close()
            engine.stop(1_000, 1_000)
        }
    }
}

class ConfluenceQueryTest {
    @Test
    fun kindSelectsType() {
        assertEquals("type = attachment AND text ~ \"макет\"", cql("attachment", "макет"))
        assertFailsWith<ToolInputError> { cql("sketch", "макет") }
    }
}
