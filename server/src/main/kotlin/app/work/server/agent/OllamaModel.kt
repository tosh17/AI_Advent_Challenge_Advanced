package app.work.server.agent

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

import app.work.agent.ChatMessage
import app.work.agent.LanguageModel
import app.work.agent.ModelStep
import app.work.agent.ToolCall
import app.work.agent.ToolDefinition
import app.work.server.ModelFailure

private val logger = LoggerFactory.getLogger(OllamaModel::class.java)

class OllamaModel(
    private val http: HttpClient,
    private val baseUrl: String,
    private val model: String,
) : LanguageModel {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun complete(messages: List<ChatMessage>, tools: List<ToolDefinition>): ModelStep {
        val response = try {
            http.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(requestBody(messages, tools).toString())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warn("model request failed", error)
            throw ModelFailure("Модель недоступна")
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw ModelFailure("Ollama ответила ${response.status.value}: ${body.take(300)}")
        }
        val message = json.parseToJsonElement(body).jsonObject["message"]?.jsonObject
            ?: throw ModelFailure("Ollama не вернула сообщение")
        val calls = message["tool_calls"]?.jsonArray.orEmpty().mapIndexed { index, element ->
            val function = element.jsonObject["function"]?.jsonObject
                ?: throw ModelFailure("В tool_calls нет function")
            ToolCall(
                id = element.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: "call-$index",
                name = function["name"]?.jsonPrimitive?.content ?: throw ModelFailure("У инструмента нет имени"),
                arguments = function["arguments"].asObject(),
            )
        }
        return ModelStep(
            text = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            toolCalls = calls,
        )
    }

    private fun requestBody(messages: List<ChatMessage>, tools: List<ToolDefinition>): JsonObject = buildJsonObject {
        put("model", model)
        put("stream", false)
        put("messages", JsonArray(messages.map { it.toOllama() }))
        put("tools", JsonArray(tools.map { it.toOllama() }))
    }

    private fun ChatMessage.toOllama(): JsonObject = when (this) {
        is ChatMessage.System -> role("system", text)
        is ChatMessage.User -> role("user", text)
        is ChatMessage.Tool -> buildJsonObject {
            put("role", "tool")
            put("content", text)
            put("tool_name", name)
        }
        is ChatMessage.Assistant -> buildJsonObject {
            put("role", "assistant")
            put("content", text)
            if (toolCalls.isNotEmpty()) {
                put("tool_calls", buildJsonArray {
                    toolCalls.forEach { call ->
                        add(buildJsonObject {
                            put("id", call.id)
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", call.name)
                                put("arguments", call.arguments)
                            })
                        })
                    }
                })
            }
        }
    }

    private fun role(role: String, text: String): JsonObject = buildJsonObject {
        put("role", role)
        put("content", text)
    }

    private fun ToolDefinition.toOllama(): JsonObject = buildJsonObject {
        put("type", "function")
        put("function", buildJsonObject {
            put("name", name)
            put("description", description)
            put("parameters", parameters)
        })
    }

    private fun JsonElement?.asObject(): JsonObject = when (this) {
        null, is kotlinx.serialization.json.JsonNull -> JsonObject(emptyMap())
        is JsonObject -> this
        is JsonPrimitive -> runCatching { json.parseToJsonElement(content).jsonObject }.getOrElse {
            JsonObject(emptyMap())
        }
        else -> JsonObject(emptyMap())
    }
}
