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
import app.work.server.activity.StoredSearch
import app.work.server.activity.StoredSprint
import app.work.server.activity.connect

class SearchRoutesTest {
    @Test
    fun findsRecordedActivitiesAndRejectsAnEmptyQuery() = testApplication {
        val store = ExposedActivityStore(
            connect(
                url = "jdbc:h2:mem:search_http_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                user = "sa",
                password = "",
            ),
        )
        val day = LocalDate(2026, 10, 23)
        store.add(NewActivity("идея", ActivityKind.Backlog, null, null))
        store.add(NewActivity("ревью", ActivityKind.Log, "APP-7", day))
        application {
            module(
                idleTurns(),
                StoredDays(store),
                StoredSprint(store),
                StoredBacklog(store),
                StoredSearch(store),
            )
        }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val empty = client.get("/api/search?q=")
        assertEquals(HttpStatusCode.BadRequest, empty.status)
        assertEquals("Пустой запрос", empty.body<ErrorBody>().error)

        val missing = client.get("/api/search")
        assertEquals(HttpStatusCode.BadRequest, missing.status)
        assertEquals("Пустой запрос", missing.body<ErrorBody>().error)

        val byKey = client.get("/api/search?q=app-7")
        assertEquals(HttpStatusCode.OK, byKey.status)
        assertEquals(listOf("ревью"), byKey.body<SearchResponse>().items.map { it.summary })
        assertEquals(listOf("Log"), byKey.body<SearchResponse>().items.map { it.kind })

        val bySummary = client.get("/api/search?q=Идея")
        assertEquals(HttpStatusCode.OK, bySummary.status)
        val hits = bySummary.body<SearchResponse>().items
        assertEquals(listOf("идея"), hits.map { it.summary })
        assertEquals(listOf("Backlog"), hits.map { it.kind })
        assertEquals(listOf<String?>(null), hits.map { it.onDate })
    }
}
