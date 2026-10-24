package app.work.server

import app.work.server.activity.Days
import app.work.server.session.BlankMessage
import app.work.server.session.SessionNotFound
import app.work.server.session.Turns
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

fun Application.module(sessions: Turns, days: Days) {
    install(ContentNegotiation) {
        json()
    }
    install(StatusPages) {
        exception<BlankMessage> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorBody("Пустое сообщение"))
        }
        exception<BadDate> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorBody("Неверная дата"))
        }
        exception<SessionNotFound> { call, _ ->
            call.respond(HttpStatusCode.NotFound, ErrorBody("Сессия не найдена"))
        }
        exception<ModelFailure> { call, error ->
            call.respond(HttpStatusCode.BadGateway, ErrorBody(error.message ?: "Модель недоступна"))
        }
    }
    routing {
        api(sessions, days)
    }
}

class BadDate : RuntimeException()

class ModelFailure(message: String) : RuntimeException(message)

@Serializable
data class ErrorBody(val error: String)
