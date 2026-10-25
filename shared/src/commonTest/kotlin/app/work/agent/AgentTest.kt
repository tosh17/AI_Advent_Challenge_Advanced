package app.work.agent

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentTest {
    @Test
    fun wordsDoNotSelectTools() = runTest {
        val catalog = RecordingCatalog()
        val model = ScriptedModel(
            listOf(ModelStep("В какой проект положить задачу?", emptyList())),
        )
        val reply = agent(model, catalog).handle(
            emptyList(),
            "добавь в текущий спринт и запланируй на завтра",
        )

        assertEquals(emptyList(), catalog.calls)
        assertEquals("В какой проект положить задачу?", reply.text)
        assertEquals(
            listOf("добавь в текущий спринт и запланируй на завтра"),
            userTexts(model),
        )
    }

    @Test
    fun executesEveryToolTheModelRequested() = runTest {
        val catalog = RecordingCatalog()
        val sprint = ToolCall("1", "activities_add_to_sprint", json("summary" to "ревью", "issue_key" to "APP-4"))
        val due = ToolCall("2", "activities_schedule", json("summary" to "ревью", "on_date" to "2026-10-25", "issue_key" to "APP-4"))
        val model = ScriptedModel(
            listOf(
                ModelStep("", listOf(sprint, due)),
                ModelStep("Поставил APP-4 в спринт и на завтра.", emptyList()),
            ),
        )

        val reply = agent(model, catalog).handle(emptyList(), "в спринт и на завтра")

        assertEquals(listOf("activities_add_to_sprint", "activities_schedule"), catalog.calls.map { it.first })
        assertEquals("Поставил APP-4 в спринт и на завтра.", reply.text)
        assertEquals(listOf("activities_add_to_sprint", "activities_schedule"), reply.calls.map { it.name })
        assertTrue(model.seen.first().filterIsInstance<ChatMessage.System>().single().text.contains("2026-10-24"))
    }

    @Test
    fun stopsWhenStepsRunOut() = runTest {
        val catalog = RecordingCatalog()
        val call = ToolCall("1", "activities_add_to_sprint", json("summary" to "ревью"))
        val model = ScriptedModel(
            listOf(
                ModelStep("", listOf(call)),
                ModelStep("этот ответ не используется", emptyList()),
            ),
        )
        val reply = Agent(
            model = model,
            tools = catalog,
            today = { LocalDate(2026, 10, 24) },
            maxSteps = 1,
        ).handle(emptyList(), "в спринт")

        assertEquals("Не хватило шагов, чтобы закончить.", reply.text)
        assertEquals(1, model.seen.size)
        assertEquals(listOf("activities_add_to_sprint"), reply.calls.map { it.name })
    }

    private fun agent(model: LanguageModel, catalog: ToolCatalog) = Agent(
        model = model,
        tools = catalog,
        today = { LocalDate(2026, 10, 24) },
    )

    private fun userTexts(model: ScriptedModel): List<String> =
        model.seen.flatMap { messages -> messages.filterIsInstance<ChatMessage.User>().map { it.text } }.distinct()

    private fun json(vararg fields: Pair<String, String>): JsonObject = buildJsonObject {
        fields.forEach { (name, value) -> put(name, JsonPrimitive(value)) }
    }
}

private class ScriptedModel(private val steps: List<ModelStep>) : LanguageModel {
    val seen = mutableListOf<List<ChatMessage>>()
    private var index = 0

    override suspend fun complete(messages: List<ChatMessage>, tools: List<ToolDefinition>): ModelStep {
        seen += messages
        return steps[index++]
    }
}

private class RecordingCatalog : ToolCatalog {
    val calls = mutableListOf<Pair<String, JsonObject>>()

    override suspend fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition("activities_add_to_sprint", "sprint", JsonObject(emptyMap())),
        ToolDefinition("activities_schedule", "plan", JsonObject(emptyMap())),
    )

    override suspend fun call(name: String, arguments: JsonObject): String {
        calls += name to arguments
        return "ok"
    }
}
