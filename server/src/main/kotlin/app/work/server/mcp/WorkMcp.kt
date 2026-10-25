package app.work.server.mcp

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

import app.work.domain.Activity
import app.work.domain.ActivityKind
import app.work.domain.ActivityStore
import app.work.domain.NewActivity
import app.work.server.tool.ToolInputError
import app.work.server.tool.isoDate
import app.work.server.tool.issueKey
import app.work.server.tool.link
import app.work.server.tool.summary
import app.work.server.tool.text

private val logger = LoggerFactory.getLogger("app.work.server.mcp")

fun Application.workMcp(store: ActivityStore) {
    mcpStatelessStreamableHttp(path = "/mcp") {
        workServer(store)
    }
}

fun workServer(store: ActivityStore): Server {
    val server = Server(
        serverInfo = Implementation(name = "work-assistant", version = "0.1.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
    )
    server.activities(store)
    return server
}

private fun Server.activities(store: ActivityStore) {
    val refs = arrayOf(
        "issue_key" to "Номер задачи, если человек его назвал. Иначе не передавай",
        "link" to "Ссылка http или https, если человек её дал. Иначе не передавай",
    )
    tool(
        name = "activities_log",
        description = "Записать в журнал, чем человек занимался в указанный день. Сюда же пиши ссылку на документ или страницу. Не план, не беклог и не спринт. Номер и ссылка необязательны.",
        schema = schema(
            required = listOf("summary", "on_date"),
            "summary" to "Суть своими словами, без выдуманных деталей",
            "on_date" to "День журнала, YYYY-MM-DD",
            *refs,
        ),
    ) { args ->
        val saved = store.add(
            NewActivity(
                summary = args.summary(),
                kind = ActivityKind.Log,
                issueKey = args.issueKey(),
                onDate = args.isoDate("on_date"),
                link = args.link(),
            ),
        )
        "Записано в журнал ${saved.onDate}: ${saved.line()}"
    }
    tool(
        name = "activities_schedule",
        description = "Запланировать активность на день. Это не журнал, не беклог и не спринт. Номер и ссылка необязательны.",
        schema = schema(
            required = listOf("summary", "on_date"),
            "summary" to "Что запланировать",
            "on_date" to "День плана, YYYY-MM-DD",
            *refs,
        ),
    ) { args ->
        val saved = store.add(
            NewActivity(
                summary = args.summary(),
                kind = ActivityKind.Schedule,
                issueKey = args.issueKey(),
                onDate = args.isoDate("on_date"),
                link = args.link(),
            ),
        )
        "Запланировано на ${saved.onDate}: ${saved.line()}"
    }
    tool(
        name = "activities_backlog",
        description = "Положить активность в беклог без даты. Ссылку на документацию сюда не клади, для неё есть журнал. В спринт не кладёт. Номер и ссылка необязательны.",
        schema = schema(
            required = listOf("summary"),
            "summary" to "Что отложить в беклог",
            *refs,
        ),
    ) { args ->
        val saved = store.add(
            NewActivity(
                summary = args.summary(),
                kind = ActivityKind.Backlog,
                issueKey = args.issueKey(),
                onDate = null,
                link = args.link(),
            ),
        )
        "В беклоге: ${saved.line()}"
    }
    tool(
        name = "activities_add_to_sprint",
        description = "Отметить активность в текущем спринте локально. Это не план на день. Номер и ссылка необязательны.",
        schema = schema(
            required = listOf("summary"),
            "summary" to "Что добавить в спринт",
            *refs,
        ),
    ) { args ->
        val saved = store.add(
            NewActivity(
                summary = args.summary(),
                kind = ActivityKind.Sprint,
                issueKey = args.issueKey(),
                onDate = null,
                link = args.link(),
            ),
        )
        "В текущем спринте: ${saved.line()}"
    }
    tool(
        name = "activities_list_day",
        description = "Журнал за один день: чем человек уже занимался. Для нескольких дней вызывай activities_list_range. Не план, не беклог и не спринт.",
        schema = schema(required = listOf("date"), "date" to "День, YYYY-MM-DD"),
    ) { args ->
        val day = args.isoDate("date")
        val rows = store.find(ActivityKind.Log, day)
        if (rows.isEmpty()) "За $day в журнале пусто" else rows.joinToString("\n") { it.line() }
    }
    tool(
        name = "activities_list_range",
        description = "Журнал за интервал дат включительно. Для недели и любого отрезка длиннее одного дня вызывай этот инструмент, не activities_list_day. Не план, не беклог и не спринт.",
        schema = schema(
            required = listOf("from_date", "to_date"),
            "from_date" to "Начало интервала, YYYY-MM-DD",
            "to_date" to "Конец интервала, YYYY-MM-DD",
        ),
    ) { args ->
        val from = args.isoDate("from_date")
        val to = args.isoDate("to_date")
        val rows = store.findBetween(ActivityKind.Log, from, to)
        if (rows.isEmpty()) {
            "С $from по $to в журнале пусто"
        } else {
            rows.joinToString("\n") { row ->
                listOfNotNull(row.onDate?.toString(), row.line()).joinToString(" ")
            }
        }
    }
    tool(
        name = "activities_plan",
        description = "Что запланировано на один день. Для нескольких дней вызывай activities_plan_range. Не журнал, не беклог и не спринт.",
        schema = schema(required = listOf("date"), "date" to "День, YYYY-MM-DD"),
    ) { args ->
        val day = args.isoDate("date")
        val rows = store.find(ActivityKind.Schedule, day)
        if (rows.isEmpty()) "На $day ничего не запланировано" else rows.joinToString("\n") { it.line() }
    }
    tool(
        name = "activities_plan_range",
        description = "План на интервал дат включительно. Для следующей недели и любого отрезка длиннее одного дня вызывай этот инструмент, не список спринта и не план одного дня. Не журнал, не беклог и не спринт.",
        schema = schema(
            required = listOf("from_date", "to_date"),
            "from_date" to "Начало интервала, YYYY-MM-DD",
            "to_date" to "Конец интервала, YYYY-MM-DD",
        ),
    ) { args ->
        val from = args.isoDate("from_date")
        val to = args.isoDate("to_date")
        val rows = store.findBetween(ActivityKind.Schedule, from, to)
        if (rows.isEmpty()) {
            "С $from по $to ничего не запланировано"
        } else {
            rows.joinToString("\n") { row ->
                listOfNotNull(row.onDate?.toString(), row.line()).joinToString(" ")
            }
        }
    }
    tool(
        name = "activities_list_sprint",
        description = "Что отмечено в текущем спринте локально. Это не план на следующую неделю: для плана по датам есть activities_plan_range.",
        schema = schema(required = emptyList()),
    ) { _ ->
        val rows = store.list(ActivityKind.Sprint)
        if (rows.isEmpty()) "В текущем спринте пусто" else rows.joinToString("\n") { it.line() }
    }
    tool(
        name = "activities_find",
        description = "Найти уже записанное по словам, номеру задачи или ссылке. Ищет журнал, план, беклог и спринт. Внешних систем нет.",
        schema = schema(required = listOf("query"), "query" to "Что искать среди уже записанного"),
    ) { args ->
        val query = args.text("query")
        val rows = store.search(query)
        if (rows.isEmpty()) "Ничего не записано по этому запросу" else rows.joinToString("\n") { row ->
            listOfNotNull(row.kind.name, row.onDate?.toString(), row.line()).joinToString(" ")
        }
    }
}

private fun Activity.line(): String = listOfNotNull(issueKey, link, summary).joinToString(" ")

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
