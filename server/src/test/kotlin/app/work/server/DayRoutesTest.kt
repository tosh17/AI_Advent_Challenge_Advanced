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

import app.work.domain.Activity
import app.work.domain.ActivityKind
import app.work.domain.ActivityStore
import app.work.domain.NewActivity
import app.work.server.activity.ExposedActivityStore
import app.work.server.activity.StoredDays
import app.work.server.activity.StoredSprint
import app.work.server.activity.connect

class DayRoutesTest {
    @Test
    fun returnsLogAndScheduleForTheDay() = testApplication {
        val store = ExposedActivityStore(
            connect(
                url = "jdbc:h2:mem:days_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                user = "sa",
                password = "",
            ),
        )
        val day = LocalDate(2026, 10, 23)
        store.add(NewActivity("ревью", ActivityKind.Log, "APP-2", day, "https://example.test/review"))
        store.add(NewActivity("созвон", ActivityKind.Schedule, null, day))
        store.add(NewActivity("идея", ActivityKind.Backlog, null, null))
        store.add(NewActivity("спринт", ActivityKind.Sprint, null, null))
        store.add(NewActivity("чужой", ActivityKind.Log, null, LocalDate(2026, 10, 24)))
        application { module(idleTurns(), StoredDays(store), StoredSprint(store)) }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = client.get("/api/days/2026-10-23")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<DayResponse>()
        assertEquals("2026-10-23", body.date)
        assertEquals(listOf("ревью"), body.log.map { it.summary })
        assertEquals(listOf("APP-2"), body.log.map { it.issueKey })
        assertEquals(listOf("https://example.test/review"), body.log.map { it.link })
        assertEquals(listOf("созвон"), body.schedule.map { it.summary })
        assertEquals(listOf<String?>(null), body.schedule.map { it.issueKey })
        assertEquals(listOf<String?>(null), body.schedule.map { it.link })
        assertEquals(false, "идея" in body.log.map { it.summary })
        assertEquals(false, "идея" in body.schedule.map { it.summary })
        assertEquals(false, "спринт" in body.log.map { it.summary })
        assertEquals(false, "спринт" in body.schedule.map { it.summary })

        val empty = client.get("/api/days/2026-10-25")
        assertEquals(HttpStatusCode.OK, empty.status)
        val emptyDay = empty.body<DayResponse>()
        assertEquals("2026-10-25", emptyDay.date)
        assertEquals(emptyList(), emptyDay.log)
        assertEquals(emptyList(), emptyDay.schedule)
    }

    @Test
    fun rejectsBadDate() = testApplication {
        application { module(idleTurns(), StoredDays(closedStore()), StoredSprint(closedStore())) }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = client.get("/api/days/2026-13-01")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Неверная дата", response.body<ErrorBody>().error)
    }
}

private fun closedStore() = object : ActivityStore {
    override suspend fun add(draft: NewActivity): Activity = error("не используется")

    override suspend fun find(kind: ActivityKind, onDate: LocalDate): List<Activity> = error("не используется")

    override suspend fun list(kind: ActivityKind): List<Activity> = error("не используется")

    override suspend fun search(query: String): List<Activity> = error("не используется")
}
