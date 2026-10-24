package app.work.server.session

import app.work.agent.Agent
import app.work.agent.ChatMessage
import app.work.server.CallResponse
import app.work.server.TurnResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface Turns {
    suspend fun open(): String

    suspend fun message(sessionId: String, text: String): TurnResponse
}

class SessionNotFound(val id: String) : RuntimeException()

class BlankMessage : RuntimeException()

class SessionBook(private val agent: Agent) : Turns {
    private val sessions = ConcurrentHashMap<String, List<ChatMessage>>()
    private val gate = Mutex()

    override suspend fun open(): String = gate.withLock {
        val id = UUID.randomUUID().toString()
        sessions[id] = emptyList()
        id
    }

    override suspend fun message(sessionId: String, text: String): TurnResponse {
        if (text.isBlank()) throw BlankMessage()
        return gate.withLock {
            val history = sessions[sessionId] ?: throw SessionNotFound(sessionId)
            val reply = agent.handle(history, text)
            sessions[sessionId] = reply.transcript
            TurnResponse(
                reply = reply.text,
                calls = reply.calls.map { CallResponse(it.name, it.result) },
            )
        }
    }
}
