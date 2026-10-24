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
import org.jetbrains.exposed.v1.jdbc.Database

import app.work.domain.Activity
import app.work.domain.ActivityKind
import app.work.domain.ActivityStore
import app.work.domain.NewActivity
import app.work.server.activity.ExposedActivityStore
import app.work.server.session.Turns

class DayRoutesTest {
    @Test
    fun returnsLogAndScheduleForTheDay() = testApplication {
        val store = ExposedActivityStore(
            Database.connect(
                url = "jdbc:h2:mem:day_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                user = "sa",
                password = "",
            ),
        )
        val day = LocalDate(2026, 10, 23)
        store.add(NewActivity("ревью", ActivityKind.Log, "APP-2", day))
        store.add(NewActivity("созвон", ActivityKind.Schedule, null, day))
        store.add(NewActivity("идея", ActivityKind.Backlog, null, day))
        store.add(NewActivity("чужой", ActivityKind.Log, null, LocalDate(2026, 10, 24)))
        application { module(idleTurns(), store) }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = client.get("/api/days/2026-10-23")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<DayResponse>()
        assertEquals("2026-10-23", body.date)
        assertEquals(listOf("ревью"), body.log.map { it.summary })
        assertEquals(listOf("APP-2"), body.log.map { it.jiraKey })
        assertEquals(listOf("созвон"), body.schedule.map { it.summary })
        assertEquals(listOf(null), body.schedule.map { it.jiraKey })

        val empty = client.get("/api/days/2026-10-25")
        assertEquals(HttpStatusCode.OK, empty.status)
        val emptyDay = empty.body<DayResponse>()
        assertEquals("2026-10-25", emptyDay.date)
        assertEquals(emptyList(), emptyDay.log)
        assertEquals(emptyList(), emptyDay.schedule)
    }

    @Test
    fun rejectsBadDate() = testApplication {
        application { module(idleTurns(), closedStore()) }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = client.get("/api/days/2026-13-01")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Неверная дата", response.body<ErrorBody>().error)
    }
}

private fun idleTurns() = object : Turns {
    override suspend fun open() = "x"
    override suspend fun message(sessionId: String, text: String) = TurnResponse("", emptyList())
}

private fun closedStore() = object : ActivityStore {
    override suspend fun add(draft: NewActivity): Activity = error("не используется")

    override suspend fun find(kind: ActivityKind, onDate: LocalDate): List<Activity> = error("не используется")
}
