package com.example.localllmvoice.data.repository

import com.example.localllmvoice.domain.model.ConversationTopics
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoLlmRepositoryTest {
    private val repository = DemoLlmRepository()

    @Test
    fun apartmentViewingReplyUsesApartmentScenario() = runBlocking {
        val topic = requireNotNull(ConversationTopics.findById("apartment_viewing"))
        val response = StringBuilder()

        repository.generateStreamingResponse(
            systemPrompt = topic.systemPrompt,
            conversationContext = """
                Partner: ${topic.openingMessage}
                Learner: Busco un piso con dos habitaciones.
            """.trimIndent(),
            userText = "Busco un piso con dos habitaciones.",
        ).collect { response.append(it) }

        val reply = response.toString()
        assertTrue(reply.contains("alquiler", ignoreCase = true))
        assertFalse(reply.contains("cerveza", ignoreCase = true))
        assertFalse(reply.contains("vino", ignoreCase = true))
    }

    @Test
    fun nonFoodTopicPromptsDoNotContainBeerExample() {
        ConversationTopics.all
            .filterNot { it.id == "madrid_food" }
            .forEach { topic ->
                assertFalse(
                    "${topic.id} should not include beer-ordering copy",
                    topic.systemPrompt.contains("cerveza", ignoreCase = true),
                )
            }
    }
}
