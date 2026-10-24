package app.work.server.mcp

import app.work.agent.ToolCatalog
import app.work.agent.ToolDefinition
import io.ktor.client.HttpClient
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class McpToolCatalog private constructor(
    private val client: Client,
    private val tools: List<ToolDefinition>,
) : ToolCatalog {
    override suspend fun definitions(): List<ToolDefinition> = tools

    override suspend fun call(name: String, arguments: JsonObject): String {
        val result = client.callTool(name, arguments.toSdkMap())
        val text = result.content.filterIsInstance<TextContent>().joinToString("\n") { it.text.orEmpty() }
        return if (result.isError == true) "ошибка: $text" else text
    }

    companion object {
        suspend fun connect(http: HttpClient, port: Int): McpToolCatalog {
            val client = Client(clientInfo = Implementation(name = "work-assistant", version = "0.1.0"))
            client.connect(StreamableHttpClientTransport(client = http, url = "http://127.0.0.1:$port/mcp"))
            val tools = client.listTools().tools.map { tool ->
                ToolDefinition(
                    name = tool.name,
                    description = tool.description.orEmpty(),
                    parameters = tool.inputSchema.toParameters(),
                )
            }
            return McpToolCatalog(client, tools)
        }
    }
}

private fun ToolSchema.toParameters(): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", properties ?: JsonObject(emptyMap()))
    val names = required.orEmpty()
    if (names.isNotEmpty()) {
        put("required", JsonArray(names.map { JsonPrimitive(it) }))
    }
}

private fun JsonObject.toSdkMap(): Map<String, Any?> = mapValues { (_, value) -> value.toSdkValue() }

private fun JsonElement.toSdkValue(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        content == "true" || content == "false" -> content.toBooleanStrict()
        content.toLongOrNull() != null && '.' !in content -> content.toLong()
        content.toDoubleOrNull() != null -> content.toDouble()
        else -> content
    }
    is JsonArray -> map { it.toSdkValue() }
    is JsonObject -> mapValues { it.value.toSdkValue() }
}
