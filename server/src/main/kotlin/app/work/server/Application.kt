package app.work.server

import app.work.agent.Agent
import app.work.server.activity.ExposedActivityStore
import app.work.server.activity.StoredBacklog
import app.work.server.activity.StoredDays
import app.work.server.activity.StoredSearch
import app.work.server.activity.StoredSprint
import app.work.server.activity.openDatabase
import app.work.server.agent.OllamaModel
import app.work.server.mcp.McpToolCatalog
import app.work.server.mcp.workMcp
import app.work.server.session.SessionBook
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("app.work.server")

fun main() {
    val config = AppConfig.fromEnv(System.getenv())
    val database = openDatabase(config)
    val store = ExposedActivityStore(database)
    val days = StoredDays(store)
    val sprint = StoredSprint(store)
    val backlog = StoredBacklog(store)
    val search = StoredSearch(store)
    val mcp = embeddedServer(Netty, host = "127.0.0.1", port = config.mcpPort) {
        workMcp(store)
    }.start(wait = false)

    val mcpHttp = HttpClient(CIO) { install(SSE) }
    val ollamaHttp = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 180_000
            socketTimeoutMillis = 180_000
        }
    }
    val catalog = runBlocking { McpToolCatalog.connect(mcpHttp, config.mcpPort) }
    val agent = Agent(
        model = OllamaModel(ollamaHttp, config.ollamaUrl, config.ollamaModel),
        tools = catalog,
        today = { Clock.System.now().toLocalDateTime(config.zone).date },
    )
    val sessions = SessionBook(agent)
    logger.info("MCP tools: {}", runBlocking { catalog.definitions().map { it.name } })
    logger.info("API http://{}:{}", config.httpHost, config.httpPort)

    Runtime.getRuntime().addShutdownHook(Thread {
        mcp.stop(1_000, 2_000)
        mcpHttp.close()
        ollamaHttp.close()
    })

    embeddedServer(Netty, host = config.httpHost, port = config.httpPort) {
        module(sessions, days, sprint, backlog, search)
    }.start(wait = true)
}
