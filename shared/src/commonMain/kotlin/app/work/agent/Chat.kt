package app.work.agent

import kotlinx.serialization.json.JsonObject

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject,
)

data class ModelStep(
    val text: String,
    val toolCalls: List<ToolCall>,
)

sealed interface ChatMessage {
    data class System(val text: String) : ChatMessage

    data class User(val text: String) : ChatMessage

    data class Assistant(val text: String, val toolCalls: List<ToolCall>) : ChatMessage

    data class Tool(val id: String, val name: String, val text: String) : ChatMessage
}

interface LanguageModel {
    suspend fun complete(messages: List<ChatMessage>, tools: List<ToolDefinition>): ModelStep
}

interface ToolCatalog {
    suspend fun definitions(): List<ToolDefinition>

    suspend fun call(name: String, arguments: JsonObject): String
}
