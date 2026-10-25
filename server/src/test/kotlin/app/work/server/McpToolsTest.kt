package app.work.server

import app.work.domain.ActivityKind
import app.work.server.activity.ExposedActivityStore
import app.work.server.activity.connect
import app.work.server.mcp.McpToolCatalog
import app.work.server.mcp.workMcp
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
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
            workMcp(store)
        }.start(wait = false)
        val http = HttpClient(CIO) { install(SSE) }
        try {
            val catalog = McpToolCatalog.connect(http, port)
            val names = catalog.definitions().map { it.name }.toSet()
            assertTrue("activities_log" in names)
            assertTrue("activities_add_to_sprint" in names)
            assertTrue("activities_find" in names)
            assertEquals(false, "jira_search" in names)
            assertEquals(false, "confluence_search" in names)

            catalog.call(
                "activities_log",
                buildJsonObject {
                    put("summary", JsonPrimitive("ревью"))
                    put("on_date", JsonPrimitive("2026-10-23"))
                    put("issue_key", JsonPrimitive("APP-2"))
                    put("link", JsonPrimitive("https://example.test/review"))
                },
            )
            catalog.call(
                "activities_log",
                buildJsonObject {
                    put("summary", JsonPrimitive("созвон"))
                    put("on_date", JsonPrimitive("2026-10-23"))
                },
            )
            val listed = catalog.call(
                "activities_list_day",
                buildJsonObject {
                    put("date", JsonPrimitive("2026-10-23"))
                },
            )
            assertTrue(listed.contains("ревью"))
            assertTrue(listed.contains("созвон"))
            val day = store.find(ActivityKind.Log, LocalDate(2026, 10, 23))
            assertEquals(listOf("ревью", "созвон"), day.map { it.summary })
            assertEquals(listOf("APP-2", null), day.map { it.issueKey })
            assertEquals(listOf("https://example.test/review", null), day.map { it.link })

            val found = catalog.call(
                "activities_find",
                buildJsonObject {
                    put("query", JsonPrimitive("example.test/review"))
                },
            )
            assertTrue(found.contains("ревью"))
            assertEquals(false, found.contains("созвон"))
        } finally {
            http.close()
            engine.stop(1_000, 1_000)
        }
    }

    @Test
    fun planStaysOutOfTheJournal() = withStore { store, catalog ->
        catalog.call(
            "activities_schedule",
            buildJsonObject {
                put("summary", JsonPrimitive("созвон"))
                put("on_date", JsonPrimitive("2026-10-26"))
            },
        )
        val plan = catalog.call(
            "activities_plan",
            buildJsonObject {
                put("date", JsonPrimitive("2026-10-26"))
            },
        )
        assertTrue(plan.contains("созвон"))
        val day = LocalDate(2026, 10, 26)
        assertEquals(listOf("созвон"), store.find(ActivityKind.Schedule, day).map { it.summary })
        assertEquals(emptyList(), store.find(ActivityKind.Log, day))
        val names = catalog.definitions().map { it.name }.toSet()
        assertEquals(false, "jira_search" in names)
        assertEquals(false, "confluence_search" in names)
    }

    private fun withStore(block: suspend (ExposedActivityStore, McpToolCatalog) -> Unit) = runBlocking {
        val store = ExposedActivityStore(
            connect(
                url = "jdbc:h2:mem:mcp_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                user = "sa",
                password = "",
            ),
        )
        val port = ServerSocket(0).use { it.localPort }
        val engine = embeddedServer(Netty, host = "127.0.0.1", port = port) {
            workMcp(store)
        }.start(wait = false)
        val http = HttpClient(CIO) { install(SSE) }
        try {
            block(store, McpToolCatalog.connect(http, port))
        } finally {
            http.close()
            engine.stop(1_000, 1_000)
        }
    }
}
