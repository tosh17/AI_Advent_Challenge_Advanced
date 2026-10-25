package app.work.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigTest {
    @Test
    fun defaultsPointAtS1() {
        val config = AppConfig.fromEnv(emptyMap())
        assertEquals("http://10.10.1.101:11434", config.ollamaUrl)
        assertEquals("qwen2.5:3b", config.ollamaModel)
    }

    @Test
    fun readsOverridesAndRejectsABadPort() {
        val config = AppConfig.fromEnv(
            mapOf(
                "DATABASE_URL" to "jdbc:postgresql://db.example/work",
                "DATABASE_USER" to "worker",
                "HTTP_PORT" to "9090",
                "OLLAMA_URL" to "http://10.10.1.101:11434/",
            ),
        )
        assertEquals("jdbc:postgresql://db.example/work", config.databaseUrl)
        assertEquals("worker", config.databaseUser)
        assertEquals(9090, config.httpPort)
        assertEquals("http://10.10.1.101:11434", config.ollamaUrl)

        val error = assertFailsWith<IllegalStateException> {
            AppConfig.fromEnv(mapOf("HTTP_PORT" to "нет"))
        }
        assertTrue(error.message.orEmpty().contains("HTTP_PORT"))
    }
}
