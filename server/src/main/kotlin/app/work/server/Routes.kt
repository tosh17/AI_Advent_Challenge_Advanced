package app.work.server

import app.work.server.activity.Backlog
import app.work.server.activity.Days
import app.work.server.activity.Search
import app.work.server.activity.Sprint
import app.work.server.session.Turns
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

fun Route.api(sessions: Turns, days: Days, sprint: Sprint, backlog: Backlog, search: Search) {
    route("/api") {
        get("/health") {
            call.respond(Health("ok"))
        }
        get("/days/{date}") {
            val raw = call.parameters["date"].orEmpty()
            call.respond(days.load(raw))
        }
        get("/sprint") {
            call.respond(sprint.load())
        }
        get("/backlog") {
            call.respond(backlog.load())
        }
        get("/search") {
            val raw = call.request.queryParameters["q"].orEmpty()
            call.respond(search.load(raw))
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
    val issueKey: String?,
    val link: String?,
    val createdAt: String,
)

@Serializable
data class SprintResponse(
    val items: List<SprintEntry>,
)

@Serializable
data class SprintEntry(
    val id: String,
    val summary: String,
    val issueKey: String?,
    val link: String?,
    val createdAt: String,
)

@Serializable
data class BacklogResponse(
    val items: List<BacklogEntry>,
)

@Serializable
data class BacklogEntry(
    val id: String,
    val summary: String,
    val issueKey: String?,
    val link: String?,
    val createdAt: String,
)

@Serializable
data class SearchResponse(
    val items: List<SearchHit>,
)

@Serializable
data class SearchHit(
    val kind: String,
    val summary: String,
    val issueKey: String?,
    val link: String?,
    val onDate: String?,
)
