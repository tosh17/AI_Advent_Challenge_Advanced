package app.work.server.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject

import app.work.agent.Agent
import app.work.agent.ChatMessage
import app.work.agent.LanguageModel
import app.work.agent.ModelStep
import app.work.agent.ToolCatalog
import app.work.agent.ToolDefinition

class SessionBookTest {
    @Test
    fun rejectsBlankAndUnknownSession() = runTest {
        val book = book { ModelStep("ок", emptyList()) }
        assertFailsWith<BlankMessage> { book.message("нет", "  ") }
        assertFailsWith<SessionNotFound> { book.message("нет", "план") }
    }

    @Test
    fun keepsTheReplyAndThePreviousTurn() = runTest {
        val seen = mutableListOf<List<ChatMessage>>()
        val book = book { messages ->
            seen += messages
            ModelStep("записал", emptyList())
        }
        val id = book.open()
        val first = book.message(id, "ревью")
        val second = book.message(id, "и что дальше")
        assertEquals("записал", first.reply)
        assertEquals("записал", second.reply)
        val secondUserTexts = seen[1].mapNotNull { message ->
            (message as? ChatMessage.User)?.text
        }
        assertEquals(listOf("ревью", "и что дальше"), secondUserTexts)
        assertIs<ChatMessage.System>(seen[1].first())
    }

    private fun book(reply: (List<ChatMessage>) -> ModelStep): SessionBook {
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
}
