package app.work.server

import kotlin.test.Test
import kotlin.test.assertEquals

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject

import app.work.agent.Agent
import app.work.agent.ChatMessage
import app.work.agent.LanguageModel
import app.work.agent.ModelStep
import app.work.agent.ToolCatalog
import app.work.agent.ToolDefinition
import app.work.server.activity.Backlog
import app.work.server.activity.Days
import app.work.server.activity.Sprint
import app.work.server.session.SessionBook

class StatusRoutesTest {
    @Test
    fun blankMessageIs400() = testApplication {
        val book = talkingBook(ModelStep("ок", emptyList()))
        application { module(book, idleDays(), idleSprint(), idleBacklog()) }
        val client = jsonClient()
        val id = client.post("/api/sessions").body<SessionCreated>().id

        val response = client.post("/api/sessions/$id/messages") {
            contentType(ContentType.Application.Json)
            setBody(IncomingMessage("   "))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Пустое сообщение", response.body<ErrorBody>().error)
    }

    @Test
    fun unknownSessionIs404() = testApplication {
        application { module(talkingBook(ModelStep("ок", emptyList())), idleDays(), idleSprint(), idleBacklog()) }
        val client = jsonClient()

        val response = client.post("/api/sessions/чужая/messages") {
            contentType(ContentType.Application.Json)
            setBody(IncomingMessage("план"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("Сессия не найдена", response.body<ErrorBody>().error)
    }

    @Test
    fun modelFailureIs502() = testApplication {
        val book = talkingBook { _ -> throw ModelFailure("нет модели") }
        application { module(book, idleDays(), idleSprint(), idleBacklog()) }
        val client = jsonClient()
        val id = client.post("/api/sessions").body<SessionCreated>().id

        val response = client.post("/api/sessions/$id/messages") {
            contentType(ContentType.Application.Json)
            setBody(IncomingMessage("план"))
        }
        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals("нет модели", response.body<ErrorBody>().error)
    }

    @Test
    fun chatAssetsAreServed() = testApplication {
        application { module(idleTurns(), idleDays(), idleSprint(), idleBacklog()) }
        val script = client.get("/chat.js")
        val style = client.get("/chat.css")
        assertEquals(HttpStatusCode.OK, script.status)
        assertEquals(HttpStatusCode.OK, style.status)
        assertEquals(true, "work-session" in script.bodyAsText())
        assertEquals(true, "--bg" in style.bodyAsText())
    }
}

private fun io.ktor.server.testing.ApplicationTestBuilder.jsonClient() = createClient {
    install(ContentNegotiation) { json() }
}

private fun talkingBook(reply: (List<ChatMessage>) -> ModelStep): SessionBook {
    val model = object : LanguageModel {
        override suspend fun complete(messages: List<ChatMessage>, tools: List<ToolDefinition>): ModelStep {
            return reply(messages)
        }
    }
    val tools = object : ToolCatalog {
        override suspend fun definitions(): List<ToolDefinition> = emptyList()

        override suspend fun call(name: String, arguments: JsonObject): String = ""
    }
    return SessionBook(Agent(model, tools, today = { LocalDate(2026, 10, 24) }))
}

private fun talkingBook(step: ModelStep): SessionBook = talkingBook { step }

private fun idleDays() = object : Days {
    override suspend fun load(rawDate: String): DayResponse = error("не используется")
}

private fun idleSprint() = object : Sprint {
    override suspend fun load(): SprintResponse = error("не используется")
}

private fun idleBacklog() = object : Backlog {
    override suspend fun load(): BacklogResponse = error("не используется")
}
