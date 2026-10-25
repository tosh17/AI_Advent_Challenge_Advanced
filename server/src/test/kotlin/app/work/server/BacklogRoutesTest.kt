package app.work.server

import kotlin.test.Test
import kotlin.test.assertEquals

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate

import app.work.domain.ActivityKind
import app.work.domain.NewActivity
import app.work.server.activity.ExposedActivityStore
import app.work.server.activity.StoredBacklog
import app.work.server.activity.StoredDays
import app.work.server.activity.StoredSprint
import app.work.server.activity.connect

class BacklogRoutesTest {
    @Test
    fun returnsOnlyBacklogActivities() = testApplication {
        val store = ExposedActivityStore(
            connect(
                url = "jdbc:h2:mem:backlog_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                user = "sa",
                password = "",
            ),
        )
        application {
            module(idleTurns(), StoredDays(store), StoredSprint(store), StoredBacklog(store))
        }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val empty = client.get("/api/backlog")
        assertEquals(HttpStatusCode.OK, empty.status)
        assertEquals(emptyList(), empty.body<BacklogResponse>().items)

        val day = LocalDate(2026, 10, 23)
        store.add(NewActivity("идея", ActivityKind.Backlog, "APP-7", null, "https://example.test/idea"))
        store.add(NewActivity("созвон", ActivityKind.Log, "APP-2", day))
        store.add(NewActivity("ревью", ActivityKind.Sprint, "APP-4", null))

        val response = client.get("/api/backlog")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<BacklogResponse>()
        assertEquals(listOf("идея"), body.items.map { it.summary })
        assertEquals(listOf("APP-7"), body.items.map { it.issueKey })
        assertEquals(listOf("https://example.test/idea"), body.items.map { it.link })
        assertEquals(false, "созвон" in body.items.map { it.summary })
        assertEquals(false, "ревью" in body.items.map { it.summary })
    }
}
