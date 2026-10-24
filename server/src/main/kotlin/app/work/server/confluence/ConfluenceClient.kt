package app.work.server.confluence

import app.work.server.ConfluenceConfig
import app.work.server.tool.ToolInputError
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

class ConfluenceClient(private val config: ConfluenceConfig) : AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) { requestTimeoutMillis = 20_000 }
    }
    private val authorization = "Basic " + Base64.getEncoder().encodeToString(
        "${config.email}:${config.token}".toByteArray(Charsets.UTF_8),
    )

    suspend fun search(kind: String, query: String): String {
        val response = http.get("${config.baseUrl}/wiki/rest/api/search") {
            header(HttpHeaders.Authorization, authorization)
            parameter("cql", cql(kind, query))
            parameter("limit", 10)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw ToolInputError("Confluence ответила ${response.status.value}: ${text.take(300)}")
        }
        val results = json.parseToJsonElement(text).jsonObject["results"]?.jsonArray.orEmpty()
        if (results.isEmpty()) return "Ничего не найдено"
        return results.joinToString("\n") { element ->
            val item = element.jsonObject
            val title = item["title"]?.jsonPrimitive?.content ?: item["content"]?.jsonObject
                ?.get("title")?.jsonPrimitive?.content ?: "без названия"
            val type = item["entityType"]?.jsonPrimitive?.content
                ?: item["content"]?.jsonObject?.get("type")?.jsonPrimitive?.content
                ?: kind
            val link = item["url"]?.jsonPrimitive?.content ?: ""
            listOf(type, title, link).filter { it.isNotBlank() }.joinToString(" ")
        }
    }

    override fun close() {
        http.close()
    }
}

fun cql(kind: String, query: String): String {
    val type = when (kind) {
        "page" -> "type = page AND "
        "attachment" -> "type = attachment AND "
        "any" -> ""
        else -> throw ToolInputError("kind: page, attachment или any")
    }
    val escaped = query.replace("\\", "\\\\").replace("\"", "\\\"")
    return "${type}text ~ \"$escaped\""
}
