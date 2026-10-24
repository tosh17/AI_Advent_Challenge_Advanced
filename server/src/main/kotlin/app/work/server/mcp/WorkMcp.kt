package app.work.server.mcp

import app.work.domain.ActivityKind
import app.work.domain.ActivityStore
import app.work.domain.NewActivity
import app.work.server.confluence.ConfluenceClient
import app.work.server.jira.IssueKeys
import app.work.server.jira.JiraClient
import app.work.server.tool.ToolInputError
import app.work.server.tool.isoDate
import app.work.server.tool.optionalText
import app.work.server.tool.summary
import app.work.server.tool.text
import io.ktor.server.application.Application
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.error
import io.modelcontextprotocol.kotlin.sdk.types.success
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("app.work.server.mcp")

fun Application.workMcp(store: ActivityStore, jira: JiraClient?, confluence: ConfluenceClient?) {
    mcpStatelessStreamableHttp(path = "/mcp") {
        workServer(store, jira, confluence)
    }
}

fun workServer(store: ActivityStore, jira: JiraClient?, confluence: ConfluenceClient?): Server {
    val server = Server(
        serverInfo = Implementation(name = "work-assistant", version = "0.1.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
    )
    server.activities(store)
    server.jira(jira)
    server.confluence(confluence)
    return server
}

private fun Server.activities(store: ActivityStore) {
    tool(
        name = "activities_log",
        description = "Записать в журнал, чем человек занимался в указанный день. Не план и не беклог. jira_key передавай только если активность про уже существующую задачу.",
        schema = schema(
            required = listOf("summary", "on_date"),
            "summary" to "Суть своими словами, без выдуманных деталей",
            "on_date" to "День журнала, YYYY-MM-DD",
            "jira_key" to "Ключ Jira или не передавай",
        ),
    ) { args ->
        val saved = store.add(
            NewActivity(
                summary = args.summary(),
                kind = ActivityKind.Log,
                jiraKey = args.jiraKey(),
                onDate = args.isoDate("on_date"),
            ),
        )
        "Записано в журнал ${saved.onDate}: ${saved.summary}"
    }
    tool(
        name = "activities_schedule",
        description = "Запланировать активность на день. Это не журнал и не беклог. В Jira само ничего не меняет.",
        schema = schema(
            required = listOf("summary", "on_date"),
            "summary" to "Что запланировать",
            "on_date" to "День плана, YYYY-MM-DD",
            "jira_key" to "Ключ Jira, если план про задачу",
        ),
    ) { args ->
        val saved = store.add(
            NewActivity(
                summary = args.summary(),
                kind = ActivityKind.Schedule,
                jiraKey = args.jiraKey(),
                onDate = args.isoDate("on_date"),
            ),
        )
        "Запланировано на ${saved.onDate}: ${saved.summary}"
    }
    tool(
        name = "activities_backlog",
        description = "Положить активность в локальный беклог без даты. Задачу в Jira не создаёт и в спринт не кладёт.",
        schema = schema(
            required = listOf("summary"),
            "summary" to "Что отложить в беклог",
            "jira_key" to "Ключ Jira, если беклог про существующую задачу",
        ),
    ) { args ->
        val saved = store.add(
            NewActivity(
                summary = args.summary(),
                kind = ActivityKind.Backlog,
                jiraKey = args.jiraKey(),
                onDate = null,
            ),
        )
        "В беклоге: ${saved.summary}"
    }
    tool(
        name = "activities_list_day",
        description = "Журнал за день: чем человек уже занимался. Не план и не беклог.",
        schema = schema(required = listOf("date"), "date" to "День, YYYY-MM-DD"),
    ) { args ->
        val day = args.isoDate("date")
        val rows = store.find(ActivityKind.Log, day)
        if (rows.isEmpty()) "За $day в журнале пусто" else rows.joinToString("\n") { row ->
            listOfNotNull(row.jiraKey, row.summary).joinToString(" ")
        }
    }
    tool(
        name = "activities_plan",
        description = "Что запланировано на день. Не журнал и не беклог.",
        schema = schema(required = listOf("date"), "date" to "День, YYYY-MM-DD"),
    ) { args ->
        val day = args.isoDate("date")
        val rows = store.find(ActivityKind.Schedule, day)
        if (rows.isEmpty()) "На $day ничего не запланировано" else rows.joinToString("\n") { row ->
            listOfNotNull(row.jiraKey, row.summary).joinToString(" ")
        }
    }
}

private fun Server.jira(jira: JiraClient?) {
    val hint = jira?.projectHint() ?: " Jira не настроена."
    tool(
        name = "jira_create_issue",
        description = "Создать задачу в Jira. В спринт не добавляет.$hint",
        schema = schema(
            required = listOf("project_key", "summary"),
            "project_key" to "Ключ проекта, например APP",
            "summary" to "Название задачи",
            "description" to "Описание, если человек его дал",
        ),
    ) { args ->
        val client = jira ?: throw ToolInputError("Jira не настроена")
        client.createIssue(args.text("project_key"), args.summary(), args.optionalText("description"))
    }
    tool(
        name = "jira_add_to_current_sprint",
        description = "Добавить существующую задачу в активный спринт доски.",
        schema = schema(required = listOf("issue_key"), "issue_key" to "Ключ задачи, например APP-1"),
    ) { args ->
        val client = jira ?: throw ToolInputError("Jira не настроена")
        client.addToCurrentSprint(args.text("issue_key"))
    }
    tool(
        name = "jira_set_due_date",
        description = "Поставить срок задачи. Дату YYYY-MM-DD посчитай сам.",
        schema = schema(
            required = listOf("issue_key", "due_date"),
            "issue_key" to "Ключ задачи",
            "due_date" to "Срок, YYYY-MM-DD",
        ),
    ) { args ->
        val client = jira ?: throw ToolInputError("Jira не настроена")
        client.setDueDate(args.text("issue_key"), args.isoDate("due_date").toString())
    }
    tool(
        name = "jira_search",
        description = "Найти задачи. JQL составляешь ты, не код.",
        schema = schema(required = listOf("jql"), "jql" to "JQL-запрос"),
    ) { args ->
        val client = jira ?: throw ToolInputError("Jira не настроена")
        client.search(args.text("jql"))
    }
}

private fun Server.confluence(confluence: ConfluenceClient?) {
    tool(
        name = "confluence_search",
        description = "Найти документ или макет. page — страницы, attachment — файлы и макеты, any — если вид неясен.",
        schema = schema(
            required = listOf("query", "kind"),
            "query" to "О чём искать, словами человека",
            "kind" to "page, attachment или any",
        ),
    ) { args ->
        val client = confluence ?: throw ToolInputError("Confluence не настроена")
        client.search(args.text("kind"), args.text("query"))
    }
}

private fun JsonObject.jiraKey(): String? = optionalText("jira_key")?.let(IssueKeys::check)

private fun Server.tool(
    name: String,
    description: String,
    schema: ToolSchema,
    block: suspend (JsonObject) -> String,
) {
    addTool(name = name, description = description, inputSchema = schema) { request ->
        try {
            CallToolResult.success(block(request.arguments ?: JsonObject(emptyMap())))
        } catch (error: ToolInputError) {
            CallToolResult.error(error.message ?: "неверные аргументы")
        } catch (error: Exception) {
            logger.warn("tool {} failed", name, error)
            CallToolResult.error(error.message ?: "ошибка инструмента")
        }
    }
}

private fun schema(required: List<String>, vararg fields: Pair<String, String>): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        fields.forEach { (field, description) ->
            put(field, buildJsonObject {
                put("type", "string")
                put("description", description)
            })
        }
    },
    required = required,
)
