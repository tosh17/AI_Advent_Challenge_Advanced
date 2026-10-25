package app.work.agent

import kotlinx.datetime.LocalDate

data class ExecutedCall(
    val name: String,
    val result: String,
)

data class AgentReply(
    val text: String,
    val calls: List<ExecutedCall>,
    val transcript: List<ChatMessage>,
)

class Agent(
    private val model: LanguageModel,
    private val tools: ToolCatalog,
    private val today: () -> LocalDate,
    private val maxSteps: Int = 4,
) {
    suspend fun handle(history: List<ChatMessage>, userText: String): AgentReply {
        val conversation = remember(history).toMutableList()
        conversation += ChatMessage.User(userText)
        val calls = mutableListOf<ExecutedCall>()
        val definitions = tools.definitions()
        repeat(maxSteps) {
            val step = model.complete(prompt(conversation), definitions)
            if (step.toolCalls.isEmpty()) {
                conversation += ChatMessage.Assistant(step.text, emptyList())
                return AgentReply(step.text, calls.toList(), conversation.toList())
            }
            conversation += ChatMessage.Assistant(step.text, step.toolCalls)
            for (call in step.toolCalls) {
                val result = tools.call(call.name, call.arguments)
                calls += ExecutedCall(call.name, result)
                conversation += ChatMessage.Tool(call.id, call.name, result)
            }
        }
        return AgentReply(
            text = "Не хватило шагов, чтобы закончить.",
            calls = calls.toList(),
            transcript = conversation.toList(),
        )
    }

    private fun prompt(conversation: List<ChatMessage>): List<ChatMessage> =
        listOf(ChatMessage.System(systemPrompt(today()))) + conversation

    private fun remember(history: List<ChatMessage>): List<ChatMessage> {
        val userOrAssistantIndices = history
            .mapIndexed { index, message ->
                if (message is ChatMessage.User || message is ChatMessage.Assistant) index else -1
            }
            .filter { it != -1 }

        return if (userOrAssistantIndices.size <= 3) {
            history
        } else {
            val dropIndex = userOrAssistantIndices[userOrAssistantIndices.size - 3]
            history.drop(dropIndex)
        }
    }
}
