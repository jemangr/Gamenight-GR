package gr.wsol.gamenight

import gr.wsol.gamenight.ai.Ai
import gr.wsol.gamenight.ai.AiResult
import gr.wsol.gamenight.ai.Havales
import gr.wsol.gamenight.ai.Keys
import gr.wsol.gamenight.ai.Provider
import gr.wsol.gamenight.ai.Reason
import gr.wsol.gamenight.core.Night
import gr.wsol.gamenight.core.Player
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.extras.Report
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTest {
    @Test fun keysAreFoundInsideCopiedText() {
        val g = "AIza" + "x".repeat(35)
        assertEquals(g, Provider.GEMINI.findKey("  $g\n"))
        val q = "gsk_" + "A1".repeat(26)
        assertEquals(q, Provider.GROQ.findKey("key: $q"))
        assertNull(Provider.OPENROUTER.findKey("hello"))
    }

    @Test fun answersAreParsed() {
        assertEquals(AiResult.Ok("Γεια"), Ai.geminiText("""{"candidates":[{"content":{"parts":[{"text":"Γει"},{"text":"α"}]}}]}"""))
        assertEquals(Reason.BLOCKED, (Ai.geminiText("""{"promptFeedback":{"blockReason":"SAFETY"}}""") as AiResult.Fail).reason)
        assertEquals(AiResult.Ok("Hi"), Ai.chatText("""{"choices":[{"message":{"content":" Hi "}}]}"""))
        assertTrue(Ai.chatText("nope") is AiResult.Fail)
        assertEquals(Reason.BAD_KEY, Ai.reasonFor(401)); assertEquals(Reason.LIMIT, Ai.reasonFor(429)); assertEquals(Reason.BUSY, Ai.reasonFor(503))
    }

    @Test fun bodiesCarryTheRules() {
        val sys = Havales.system(rude = false, english = false)
        assertTrue(sys.contains("Χωρίς βρισιές")); assertTrue(sys.contains("σεξουαλικό"))
        assertTrue(Havales.system(rude = true, english = true).contains("Mild swearing"))
        assertFalse(Havales.system(rude = false, english = true, short = false).contains("Always very short"))
        val b = Ai.chatBody(Provider.GROQ, "s", "p").toString()
        assertTrue(b.contains("openai/gpt-oss-120b") && b.contains("\"system\""))
        assertTrue(Ai.geminiBody("s", "p").toString().contains("systemInstruction"))
    }

    @Test fun summaryHasNamesAndPointsButNeverTheKey() {
        Keys.set(Provider.GEMINI, "AIza-secret")
        Store.night = Night(players = listOf(Player("Μαρία", 0), Player("Νίκος", 1)), points = mapOf("Μαρία" to 3))
        val s = Havales.summary(Store.night)
        assertTrue(s.contains("Μαρία 3") && s.contains("Νίκος 0"))
        assertFalse(s.contains("secret"))
        assertFalse(Havales.ready()) // off by default
        Keys.set(Provider.GEMINI, "")
    }
}
