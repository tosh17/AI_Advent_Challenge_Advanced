package app.work.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigTest {
    @Test
    fun defaultsPointAtS1() {
        val config = AppConfig.fromEnv(emptyMap())
        assertEquals("http://10.10.1.101:11434", config.ollamaUrl)
        assertEquals("qwen2.5:3b", config.ollamaModel)
        assertNull(config.jira)
        assertNull(config.confluence)
    }

    @Test
    fun jiraNeedsAllCredentials() {
        val partial = AppConfig.fromEnv(mapOf("JIRA_BASE_URL" to "https://example.atlassian.net"))
        assertNull(partial.jira)
        val ready = AppConfig.fromEnv(
            mapOf(
                "JIRA_BASE_URL" to "https://example.atlassian.net/",
                "JIRA_EMAIL" to "a@b.c",
                "JIRA_API_TOKEN" to "secret",
                "JIRA_PROJECT_KEY" to "APP",
            ),
        )
        assertEquals("https://example.atlassian.net", ready.jira?.baseUrl)
        assertEquals("APP", ready.jira?.projectKey)
    }
}
