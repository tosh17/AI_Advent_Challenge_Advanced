package app.work.server

import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigTest {
    @Test
    fun defaultsPointAtS1() {
        val config = AppConfig.fromEnv(emptyMap())
        assertEquals("http://10.10.1.101:11434", config.ollamaUrl)
        assertEquals("qwen2.5:3b", config.ollamaModel)
    }
}
