package com.eldersoftware.anytimespanish.domain.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepetitionDetectorTest {
    @Test
    fun testNormalSentencesAreNotRepetitive() {
        assertFalse(RepetitionDetector.isRepetitive("Hola, ¿cómo estás? Yo estoy muy bien, gracias."))
        assertFalse(RepetitionDetector.isRepetitive("No, no, no es lo que quería decir."))
        assertFalse(RepetitionDetector.isRepetitive("Hola, hola. ¿Qué tal?"))
    }

    @Test
    fun testConsecutiveSentenceRepetitionIsDetected() {
        assertTrue(RepetitionDetector.isRepetitive("Hola. ¿Qué te gustaría pedir? ¿Qué te gustaría pedir?"))
        assertTrue(RepetitionDetector.isRepetitive("Perfecto. Voy a pedir un café. Voy a pedir un café."))
    }

    @Test
    fun testConsecutivePhraseRepetitionIsDetected() {
        assertTrue(RepetitionDetector.isRepetitive("quiero pedir un café con leche quiero pedir un café con leche"))
        assertTrue(RepetitionDetector.isRepetitive("Me gustaría saber cómo estás hoy cómo estás hoy en este día."))
    }

    @Test
    fun testShortRepetitionsAreIgnored() {
        assertFalse(RepetitionDetector.isRepetitive("bueno bueno bueno"))
        assertFalse(RepetitionDetector.isRepetitive("sí sí claro"))
    }

    @Test
    fun testLowValueAcknowledgementIsDetected() {
        assertTrue(RepetitionDetector.isLowValueAcknowledgement("Perfecto y qué bien."))
        assertTrue(RepetitionDetector.isLowValueAcknowledgement("Muy bien, perfecto."))
    }

    @Test
    fun testSpecificTutorRepliesAreNotLowValueAcknowledgements() {
        assertFalse(
            RepetitionDetector.isLowValueAcknowledgement(
                "Perfecto, se dice 'una cerveza'. ¿Qué tipo te gustaría pedir?",
            ),
        )
        assertFalse(
            RepetitionDetector.isLowValueAcknowledgement(
                "Muy bien, ahora dime qué experiencia tienes con Kotlin.",
            ),
        )
    }
}
