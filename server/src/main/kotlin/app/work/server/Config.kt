package app.work.server

import kotlinx.datetime.TimeZone

data class AppConfig(
    val httpHost: String,
    val httpPort: Int,
    val mcpPort: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val ollamaUrl: String,
    val ollamaModel: String,
    val zone: TimeZone,
) {
    companion object {
        fun fromEnv(env: Map<String, String>): AppConfig {
            val zoneId = env["ZONE"] ?: "Europe/Moscow"
            return AppConfig(
                httpHost = env["HTTP_HOST"] ?: "0.0.0.0",
                httpPort = env.int("HTTP_PORT", 8080),
                mcpPort = env.int("MCP_PORT", 8081),
                databaseUrl = env["DATABASE_URL"]
                    ?: "jdbc:h2:file:./data/work;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                databaseUser = env["DATABASE_USER"] ?: "sa",
                databasePassword = env["DATABASE_PASSWORD"] ?: "",
                ollamaUrl = (env["OLLAMA_URL"] ?: "http://10.10.1.101:11434").trimEnd('/'),
                ollamaModel = env["OLLAMA_MODEL"] ?: "qwen2.5:3b",
                zone = TimeZone.of(zoneId),
            )
        }

        private fun Map<String, String>.int(name: String, default: Int): Int {
            val raw = this[name] ?: return default
            return raw.toIntOrNull() ?: error("Переменная $name должна быть числом")
        }
    }
}
