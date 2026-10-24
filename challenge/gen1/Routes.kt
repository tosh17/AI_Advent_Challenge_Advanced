package app.work.server

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

import app.work.domain.Activity
import app.work.domain.ActivityKind
import app.work.domain.ActivityStore
import app.work.server.session.Turns

fun Route.api(
    sessions: Turns,
    store: ActivityStore,
) {
    route("/api") {
        get("/health") {
            call.respond(Health("ok"))
        }
        get("/days/{date}") {
            val date = call.parameters["date"].orEmpty()
            call.respond(readDay(store, date))
        }
        post("/sessions") {
            call.respond(SessionCreated(sessions.open()))
        }
        post("/sessions/{id}/messages") {
            val id = call.parameters["id"].orEmpty()
            val body = call.receive<IncomingMessage>()
            call.respond(sessions.message(id, body.text))
        }
    }
}

private suspend fun readDay(store: ActivityStore, rawDate: String): DayResponse {
    val date = pathDate(rawDate)
    return DayResponse(
        date = date.toString(),
        log = store.find(ActivityKind.Log, date).map { it.toEntry() },
        schedule = store.find(ActivityKind.Schedule, date).map { it.toEntry() },
    )
}

private fun pathDate(raw: String): LocalDate = try {
    LocalDate.parse(raw)
} catch (_: IllegalArgumentException) {
    throw BadDate()
}

private fun Activity.toEntry() = DayEntry(
    id = id,
    summary = summary,
    jiraKey = jiraKey,
    createdAt = createdAt.toString(),
)

class BadDate : RuntimeException()

@Serializable
data class Health(val status: String)

@Serializable
data class SessionCreated(val id: String)

@Serializable
data class IncomingMessage(val text: String)

@Serializable
data class TurnResponse(
    val reply: String,
    val calls: List<CallResponse>,
)

@Serializable
data class CallResponse(
    val tool: String,
    val result: String,
)

@Serializable
data class DayResponse(
    val date: String,
    val log: List<DayEntry>,
    val schedule: List<DayEntry>,
)

@Serializable
data class DayEntry(
    val id: String,
    val summary: String,
    val jiraKey: String?,
    val createdAt: String,
)
