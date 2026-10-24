package app.work.server.jira

import app.work.server.JiraConfig
import app.work.server.tool.ToolInputError
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64

class JiraClient(private val config: JiraConfig) : AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
        }
    }
    private val authorization = "Basic " + Base64.getEncoder().encodeToString(
        "${config.email}:${config.token}".toByteArray(Charsets.UTF_8),
    )

    fun projectHint(): String = config.projectKey?.let { " Проект по умолчанию: $it." }
        ?: " Если человек не назвал проект, спроси ключ."

    suspend fun createIssue(projectKey: String, summary: String, description: String?): String {
        val key = projectKey(projectKey)
        val body = buildJsonObject {
            put("fields", buildJsonObject {
                put("project", buildJsonObject { put("key", key) })
                put("summary", summary)
                put("issuetype", buildJsonObject { put("name", "Task") })
                if (!description.isNullOrBlank()) put("description", adf(description))
            })
        }
        val root = request("POST", "/rest/api/3/issue", body)
        val issue = root["key"]?.jsonPrimitive?.content ?: error("Jira не вернула ключ задачи")
        return "Создана задача $issue"
    }

    suspend fun addToCurrentSprint(issueKey: String): String {
        val key = IssueKeys.check(issueKey)
        val board = config.boardId ?: throw ToolInputError("Не задан JIRA_BOARD_ID")
        val sprints = request("GET", "/rest/agile/1.0/board/$board/sprint?state=active")
        val sprintId = sprints["values"]?.jsonArray.orEmpty()
            .firstOrNull()
            ?.jsonObject
            ?.get("id")
            ?.jsonPrimitive
            ?.content
            ?: throw ToolInputError("Активного спринта нет")
        request(
            "POST",
            "/rest/agile/1.0/sprint/$sprintId/issue",
            buildJsonObject { put("issues", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(key)) }) },
        )
        return "Задача $key добавлена в текущий спринт"
    }

    suspend fun setDueDate(issueKey: String, dueDate: String): String {
        val key = IssueKeys.check(issueKey)
        request(
            "PUT",
            "/rest/api/3/issue/$key",
            buildJsonObject {
                put("fields", buildJsonObject { put("duedate", dueDate) })
            },
        )
        return "У задачи $key срок $dueDate"
    }

    suspend fun search(jql: String): String {
        val root = request(
            "POST",
            "/rest/api/3/search/jql",
            buildJsonObject {
                put("jql", jql)
                put("maxResults", 10)
                put("fields", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("summary"))
                    add(kotlinx.serialization.json.JsonPrimitive("status"))
                })
            },
        )
        val issues = root["issues"]?.jsonArray ?: JsonArray(emptyList())
        if (issues.isEmpty()) return "Ничего не найдено"
        return issues.joinToString("\n") { element ->
            val issue = element.jsonObject
            val key = issue["key"]?.jsonPrimitive?.content ?: "?"
            val fields = issue["fields"]?.jsonObject
            val summary = fields?.get("summary")?.jsonPrimitive?.contentOrNull ?: ""
            val status = fields?.get("status")?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
            "$key $status $summary".trim()
        }
    }

    override fun close() {
        http.close()
    }

    private suspend fun request(method: String, path: String, body: JsonObject? = null): JsonObject {
        val response = when (method) {
            "GET" -> http.get(config.baseUrl + path) { header(HttpHeaders.Authorization, authorization) }
            "POST" -> http.post(config.baseUrl + path) {
                header(HttpHeaders.Authorization, authorization)
                contentType(ContentType.Application.Json)
                if (body != null) setBody(body.toString())
            }
            "PUT" -> http.put(config.baseUrl + path) {
                header(HttpHeaders.Authorization, authorization)
                contentType(ContentType.Application.Json)
                if (body != null) setBody(body.toString())
            }
            else -> error("Неизвестный метод")
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw ToolInputError("Jira ответила ${response.status.value}: ${text.take(300)}")
        }
        if (text.isBlank()) return JsonObject(emptyMap())
        return json.parseToJsonElement(text).jsonObject
    }

    private fun projectKey(value: String): String {
        if (!Regex("^[A-Z][A-Z0-9]+$").matches(value)) {
            throw ToolInputError("Ключ проекта должен быть вроде APP")
        }
        return value
    }

    private fun adf(text: String): JsonObject = buildJsonObject {
        put("type", "doc")
        put("version", 1)
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "paragraph")
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    })
                })
            })
        })
    }
}

object IssueKeys {
    private val pattern = Regex("^[A-Z][A-Z0-9]+-\\d+$")

    fun check(value: String): String {
        if (!pattern.matches(value)) throw ToolInputError("Ожидался ключ задачи вроде ABC-1")
        return value
    }
}
