package com.example.localllmvoice.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GemmaStreamParserTest {
    @Test
    fun visibleText_emittedOutsideThinkBlock() {
        val parser = GemmaStreamParser()
        val visible = StringBuilder()
        assertNull(parser.processToken("Hola, ") { visible.append(it) })
        assertNull(parser.processToken("¿qué tal?") { visible.append(it) })
        assertEquals("Hola, ¿qué tal?", visible.toString())
    }

    @Test
    fun thinkBlock_extractedAndHiddenFromVisible() {
        val parser = GemmaStreamParser()
        val visible = StringBuilder()
        val thoughts = mutableListOf<String?>()

        thoughts += parser.processToken("Visible ") { visible.append(it) }
        thoughts += parser.processToken("<|think|>grammar tip") { visible.append(it) }
        thoughts += parser.processToken(" more</|think|> after") { visible.append(it) }

        assertEquals("grammar tip more", thoughts.filterNotNull().single())
        assertEquals("Visible  after", visible.toString())
    }

    @Test
    fun splitThinkTags_hiddenFromVisible() {
        val parser = GemmaStreamParser()
        val visible = StringBuilder()
        val thoughts = mutableListOf<String?>()

        thoughts += parser.processToken("Bien. <|thi") { visible.append(it) }
        thoughts += parser.processToken("nk|>internal") { visible.append(it) }
        thoughts += parser.processToken(" note</|thi") { visible.append(it) }
        thoughts += parser.processToken("nk|> ¿Quieres practicar más?") { visible.append(it) }

        assertEquals("internal note", thoughts.filterNotNull().single())
        assertEquals("Bien.  ¿Quieres practicar más?", visible.toString())
    }
}
