package app.work.server

import app.work.server.activity.Days
import app.work.server.session.Turns
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutesTest {
    @Test
    fun passesTextThrough() = testApplication {
        val seen = mutableListOf<String>()
        application {
            module(
                object : Turns {
                    override suspend fun open() = "session-1"
                    override suspend fun message(sessionId: String, text: String): TurnResponse {
                        seen += "$sessionId:$text"
                        return TurnResponse("уточни проект", emptyList())
                    }
                },
                idleDays(),
            )
        }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val created = client.post("/api/sessions")
        assertEquals(HttpStatusCode.OK, created.status)
        assertEquals("session-1", created.body<SessionCreated>().id)

        val response = client.post("/api/sessions/session-1/messages") {
            contentType(ContentType.Application.Json)
            setBody(IncomingMessage("добавь в спринт"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("уточни проект", response.body<TurnResponse>().reply)
        assertEquals(listOf("session-1:добавь в спринт"), seen)
    }

    @Test
    fun health() = testApplication {
        application { module(idleTurns(), idleDays()) }
        assertEquals(HttpStatusCode.OK, client.get("/api/health").status)
    }
}

internal fun idleTurns() = object : Turns {
    override suspend fun open() = "x"
    override suspend fun message(sessionId: String, text: String) = TurnResponse("", emptyList())
}

private fun idleDays() = object : Days {
    override suspend fun load(rawDate: String): DayResponse = error("не используется")
}
