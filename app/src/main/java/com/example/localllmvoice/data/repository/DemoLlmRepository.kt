package com.example.localllmvoice.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DemoLlmRepository : LlmRepository {
    override val isDemoMode: Boolean = true

    override fun checkModelAvailability(): Flow<ModelAvailability> = flow {
        emit(
            ModelAvailability(
                status = GemmaModelStatus.READY,
                message = "Practice mode — scripted replies (no Gemma download)",
                activeBackend = "Demo",
                deviceCapability = null,
            ),
        )
    }

    override fun downloadModel(): Flow<ModelDownloadEvent> = flow {
        emit(ModelDownloadEvent.Completed)
    }

    override fun generateStreamingResponse(
        systemPrompt: String,
        conversationContext: String,
        userText: String,
    ): Flow<String> = flow {
        val reply = buildDemoReply(systemPrompt, conversationContext)
        val thinkBlock =
            "<|think|>Good rhythm. Watch subjunctive after 'quiero que'. Score: 7/10</|think|>"
        for (char in reply + thinkBlock) {
            emit(char.toString())
            delay(12)
        }
    }

    override fun analyzeConversation(transcript: String): Flow<String> = flow {
        val report = buildDemoAnalysisReport(transcript)
        for (char in report) {
            emit(char.toString())
            delay(12)
        }
    }

    override suspend fun resetConversation() = Unit

    private fun buildDemoReply(systemPrompt: String, conversationContext: String): String {
        val turnCount = conversationContext.lines().count { it.startsWith("Learner:") }
        val replies = when {
            systemPrompt.contains("camarero amable", ignoreCase = true) -> listOf(
                "Perfecto. ¿Quieres empezar con una tapa o prefieres pedir la bebida primero?",
                "De acuerdo. ¿Te gustaría algo ligero o un plato más contundente?",
                "Muy bien. ¿Quieres pedir algo más o prefieres la cuenta?",
            )

            systemPrompt.contains("entrevistadora de recursos humanos", ignoreCase = true) -> listOf(
                "Gracias. ¿Cuál fue tu responsabilidad principal en ese proyecto?",
                "Interesante. ¿Cómo sueles trabajar cuando hay un problema en equipo?",
                "Muy bien. ¿Qué tecnología te gustaría mejorar este año?",
            )

            systemPrompt.contains("persona local y servicial", ignoreCase = true) -> listOf(
                "Claro. ¿Cuál es el destino exacto al que quieres llegar?",
                "Entiendo. ¿Prefieres ir caminando o usar el metro?",
                "De acuerdo. ¿Quieres que te repita la ruta paso a paso?",
            )

            systemPrompt.contains("recepcionista", ignoreCase = true) -> listOf(
                "Perfecto. ¿A nombre de quién está la reserva?",
                "Gracias. ¿Tiene un documento de identidad para confirmar los datos?",
                "Muy bien. ¿Quiere que le explique el horario del desayuno?",
            )

            systemPrompt.contains("farmacia", ignoreCase = true) -> listOf(
                "Entiendo. ¿Desde cuándo tiene esos síntomas?",
                "Gracias. ¿Tiene alguna alergia a medicamentos?",
                "De acuerdo. ¿Quiere que le explique cómo se toma este medicamento?",
            )

            systemPrompt.contains("médica de atención primaria", ignoreCase = true) -> listOf(
                "Entiendo. ¿Cuándo empezaron esos síntomas?",
                "Gracias. ¿El dolor es constante o aparece solo a veces?",
                "De acuerdo. ¿Ha tomado algún medicamento hoy?",
            )

            systemPrompt.contains("agente inmobiliario", ignoreCase = true) -> listOf(
                "Perfecto. ¿Prefieres ver primero el salón o la cocina?",
                "Entiendo. ¿Qué presupuesto tienes pensado para el alquiler?",
                "De acuerdo. ¿Quieres saber más sobre los gastos o sobre el contrato?",
            )

            systemPrompt.contains("tienda de ropa", ignoreCase = true) -> listOf(
                "Claro. ¿Qué talla sueles usar?",
                "Perfecto. ¿Prefieres este color o buscas otro estilo?",
                "De acuerdo. ¿Quieres probarlo en el probador?",
            )

            systemPrompt.contains("estación de tren", ignoreCase = true) -> listOf(
                "Perfecto. ¿Para qué día quiere viajar?",
                "Entiendo. ¿Prefiere un billete de ida o de ida y vuelta?",
                "De acuerdo. ¿Quiere consultar también el andén?",
            )

            systemPrompt.contains("banco", ignoreCase = true) -> listOf(
                "Perfecto. ¿Tiene ya un documento de identidad preparado?",
                "Entiendo. ¿Quiere una cuenta para uso diario o para ahorrar?",
                "De acuerdo. ¿Quiere que le explique las comisiones básicas?",
            )

            systemPrompt.contains("vecina simpática", ignoreCase = true) -> listOf(
                "Qué bien. ¿Te apetece hacer algo tranquilo o salir por la ciudad?",
                "Suena interesante. ¿Con quién sueles hacer esos planes?",
                "Muy bien. ¿Hay algún sitio nuevo que quieras conocer?",
            )

            else -> listOf(
                "Perfecto. ¿Puedes contarme un poco más?",
                "Entiendo. ¿Qué te gustaría hacer ahora?",
                "Muy bien. ¿Quieres practicar otro detalle de esta situación?",
            )
        }
        return replies[turnCount % replies.size]
    }

    private fun buildDemoAnalysisReport(transcript: String): String {
        return """
            Lo hiciste bien manteniendo el hilo de la conversación y respondiendo con frases completas.

            Revisa la concordancia de género y número, y los tiempos verbales cuando hablas del pasado o del futuro. Por ejemplo, "yo voy" suena más natural que construcciones forzadas con el infinitivo.

            Sigue usando conectores sencillos como "porque", "pero" y "entonces" para que suene más fluido.
        """.trimIndent()
    }
}
