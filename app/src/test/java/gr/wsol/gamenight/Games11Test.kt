package gr.wsol.gamenight

import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Content
import gr.wsol.gamenight.core.History
import gr.wsol.gamenight.core.Night
import gr.wsol.gamenight.core.Palette
import gr.wsol.gamenight.core.Place
import gr.wsol.gamenight.core.Player
import gr.wsol.gamenight.core.Settings
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.Word
import gr.wsol.gamenight.core.plus
import gr.wsol.gamenight.extras.MotionNow
import gr.wsol.gamenight.games.AiGames
import gr.wsol.gamenight.games.AiqRules
import gr.wsol.gamenight.games.BottleRules
import gr.wsol.gamenight.games.ClosestRules
import gr.wsol.gamenight.games.DefuseRules
import gr.wsol.gamenight.games.DiceRules
import gr.wsol.gamenight.games.DroneMaze
import gr.wsol.gamenight.games.DualGame
import gr.wsol.gamenight.games.EndlessRules
import gr.wsol.gamenight.games.Games
import gr.wsol.gamenight.games.IceCursor
import gr.wsol.gamenight.games.LaserGame
import gr.wsol.gamenight.games.RingGame
import gr.wsol.gamenight.games.SafeDial
import gr.wsol.gamenight.games.SoundChain
import gr.wsol.gamenight.games.SpiralGame
import gr.wsol.gamenight.games.SpyRules
import gr.wsol.gamenight.games.Tally11
import gr.wsol.gamenight.games.TowerRules
import gr.wsol.gamenight.games.WhisperRules
import gr.wsol.gamenight.games.WhoRules
import gr.wsol.gamenight.games.angleGap
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.random.Random

class Games11Test {
    @Before fun store() { Store.inMemory(Night(players = listOf("Α", "Β", "Γ", "Δ").mapIndexed { i, n -> Player(n, Palette.players[i]) }), Settings(sounds = false, haptics = false)) }

    @Test fun every11GameIsPlayableAndInACategory() {
        val new = listOf("whois", "knowme", "dice", "bottle", "closest", "quiz", "chain", "mime", "whisper", "spy", "lietower", "tavli",
            "interro", "drawai", "endless", "court", "aiq", "oracle", "dual", "ring", "laser", "spiral", "drone", "defuse", "icequiz", "safe")
        assertEquals(26, new.size)
        new.forEach { id -> val g = Games.byId(id); assertNotNull(id, g); assertTrue(id, g!!.ready) }
    }

    @Test fun whoGivesAPointToEveryoneWhoPickedTheTop() {
        val v = mapOf("Α" to "Β", "Β" to "Β", "Γ" to "Α", "Δ" to "Β")
        assertEquals(setOf("Β"), Tally11.tops(v))
        assertEquals(mapOf("Α" to 1, "Β" to 1, "Δ" to 1), WhoRules.points(v))
    }

    @Test fun closestParsesGreekNumbersAndPaysTheBet() {
        assertEquals(2918.0, ClosestRules.parse("2.918")!!, 0.0)
        assertEquals(2918.0, ClosestRules.parse("2918")!!, 0.0)
        assertEquals(2.5, ClosestRules.parse("2,5")!!, 0.0)
        assertEquals(384400.0, ClosestRules.parse("384.400")!!, 0.0)
        val p = ClosestRules.points(100.0, mapOf("Α" to 100.0, "Β" to 90.0, "Γ" to 150.0), mapOf("Α" to 2, "Β" to 1, "Γ" to 3))
        assertEquals(mapOf("Α" to 5, "Β" to 0, "Γ" to -1), p) // 1 + bet 2 + exact 2
    }

    @Test fun diceFacesAndBottleSeats() {
        assertEquals(listOf("q", "d", "m", "n", "s", "*"), (1..6).map { DiceRules.face(it) })
        assertEquals(0, BottleRules.seat(0f, 4)); assertEquals(1, BottleRules.seat(90f, 4)); assertEquals(0, BottleRules.seat(350f, 4)); assertEquals(2, BottleRules.seat(-180f, 4))
    }

    @Test fun soundChainKeepsGoingWithoutWhoeverSlips() {
        val g = SoundChain(listOf("Α", "Β", "Γ"))
        g.ok("γάτα"); g.ok("τρένο")
        assertEquals("Γ", g.current)
        assertEquals("Γ", g.out())
        assertEquals("Α", g.current)
        assertEquals(2, g.sounds.size)
        g.out(); assertTrue(g.over)
        assertEquals(listOf("Β"), g.alive)
    }

    @Test fun whisperPointsForCatchingOrForGettingAway() {
        val caught = WhisperRules.points(mapOf("Α" to "Γ", "Β" to "Γ", "Γ" to "Α", "Δ" to "Γ"), listOf("Γ"), setOf("Γ"))
        assertEquals(mapOf("Α" to 1, "Β" to 1, "Δ" to 1), caught)
        val away = WhisperRules.points(mapOf("Α" to "Β", "Β" to "Α", "Γ" to "Β", "Δ" to "Β"), listOf("Γ"), setOf("Γ"))
        assertEquals(mapOf("Γ" to 3), away)
        assertEquals(1, WhisperRules.suspects(listOf("Α", "Β", "Γ", "Δ", "Ε"), two = true).size)
        assertEquals(2, WhisperRules.suspects(listOf("Α", "Β", "Γ", "Δ", "Ε", "Ζ"), two = true).size)
    }

    @Test fun spyDealsOneSpyAndTheRealPlaceIsAChoice() {
        val place = Place("Νοσοκομείο", listOf("γιατρός", "ασθενής"))
        val (spy, roles) = SpyRules.deal(listOf("Α", "Β", "Γ", "Δ"), place, Random(2))
        assertEquals(null, roles[spy])
        assertEquals(3, roles.values.count { it != null })
        val all = listOf(place, Place("Σχολείο", listOf("δάσκαλος")), Place("Ταβέρνα", listOf("μάγειρας")))
        assertTrue("Νοσοκομείο" in SpyRules.choices(place, all))
        assertEquals(mapOf("Α" to 3), SpyRules.points(listOf("Α", "Β"), "Α", true))
        assertEquals(mapOf("Β" to 1, "Γ" to 1), SpyRules.points(listOf("Α", "Β", "Γ"), "Α", false))
    }

    @Test fun towerLiarsScore() {
        assertEquals(1, TowerRules.liars(listOf("Α", "Β", "Γ", "Δ")).size)
        assertEquals(2, TowerRules.liars(listOf("Α", "Β", "Γ", "Δ", "Ε")).size)
        val p = TowerRules.points(mapOf("Α" to "Β", "Β" to "Α", "Γ" to "Β", "Δ" to "Γ"), setOf("Β", "Γ"))
        assertEquals(mapOf("Α" to 1, "Γ" to 3, "Δ" to 1), p)
    }

    @Test fun aiAnswersAreReadEvenWrappedInMarkdown() {
        val o = AiGames.parse("Here:\n```json\n{\"who\": \"σερβιτόρος\", \"ok\": true}\n```")
        assertEquals("σερβιτόρος", AiGames.str(o, "who"))
        assertEquals(true, AiGames.bool(o, "ok"))
        assertEquals(null, AiGames.parse("όχι json"))
    }

    @Test fun drawingGuessesMatchWithoutAccentsOrArticles() {
        assertTrue(AiGames.matches("Μια γάτα", "γάτα"))
        assertTrue(AiGames.matches("γατούλα", "γάτα"))
        assertTrue(AiGames.matches("ΑΕΡΟΠΛΑΝΟ!", "αεροπλάνο"))
        assertTrue(AiGames.matches("a bicycle", "Bicycle"))
        assertFalse(AiGames.matches("σκύλος", "γάτα"))
        assertFalse(AiGames.matches("σπίτι", "σπιτάκι για πουλιά"))
    }

    @Test fun endlessCardsSkipWhatExists() {
        val el = Json.parseToJsonElement("""[{"w":"Οφσάιντ","no":["α","β","γ","δ","ε"]},{"w":"Πέναλτι","no":["α","β","γ","δ","ε"]},{"w":"οφσαΐντ","no":["α","β","γ"]},{"w":"Κόρνερ","no":["α"]}]""")
        val cards = EndlessRules.read(EndlessRules.Kind.Taboo, el, listOf("Πέναλτι"))
        assertEquals(listOf("Οφσάιντ"), cards.map { it.w })
        val words = EndlessRules.read(EndlessRules.Kind.Forehead, Json.parseToJsonElement("""["Μέσι","Ρονάλντο","μέσι"]"""), emptyList())
        assertEquals(listOf("Μέσι", "Ρονάλντο"), words.map { it.w })
    }

    @Test fun whoIsTheAiPoints() {
        val byAnswer = mapOf("μπύρα" to "Α", "πίτσα" to "Β", "σουβλάκι" to "Γ")
        val p = AiqRules.points(mapOf("Α" to "ΑΙ", "Β" to "μπύρα", "Γ" to "μπύρα", "Δ" to "ΑΙ"), "ΑΙ", byAnswer)
        assertEquals(mapOf("Α" to 4, "Δ" to 2), p)
    }

    @Test fun historyCountsWinsAndStreaks() {
        val r = { a: Int, b: Int -> History.NightRecord(0, listOf(History.Row("Α", listOf("Α"), a), History.Row("Β", listOf("Β"), b))) }
        val list = listOf(r(5, 3), r(4, 1), r(2, 7), r(9, 1), r(8, 2)).mapIndexed { i, n -> n.copy(at = i.toLong()) }
        val s = History.stats(list).associateBy { it.name }
        assertEquals(4, s["Α"]!!.wins); assertEquals(2, s["Α"]!!.streak); assertEquals(2, s["Α"]!!.best)
        assertEquals(1, s["Β"]!!.wins); assertEquals(0, s["Β"]!!.streak)
        History.clear()
        Store.night = Store.night.copy(points = mapOf("Α" to 3))
        History.add(Store.night); assertEquals(1, History.all().size)
        History.add(Store.night.copy(points = emptyMap())); assertEquals(1, History.all().size)
    }

    @Test fun packsAndOwnWordsJoinTheDecks() {
        val c = Content(forehead = listOf(Word("α"))).plus(Content(forehead = listOf(Word("β")), draw = listOf(Word("γ"))))
        assertEquals(2, c.forehead.size); assertEquals(1, c.draw.size)
        gr.wsol.gamenight.core.Packs.setCustom(listOf("ο σκύλος του Νίκου", "x", "ο σκύλος του Νίκου"))
        val m = gr.wsol.gamenight.core.Packs.mix(Content(), "el")
        assertEquals(listOf("ο σκύλος του Νίκου"), m.forehead.map { it.w })
        assertEquals(1, m.taboo.size)
        gr.wsol.gamenight.core.Packs.setCustom(emptyList())
    }

    @Test fun packsInTheRepoAreValid() {
        val dir = File("../packs").takeIf { it.exists() } ?: File("packs")
        val json = Json { ignoreUnknownKeys = true }
        val index = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(gr.wsol.gamenight.core.Packs.Info.serializer()), File(dir, "index.json").readText())
        assertTrue(index.size >= 3)
        index.forEach { p ->
            val c = json.decodeFromString(Content.serializer(), File(dir, p.file).readText())
            assertTrue(p.id, gr.wsol.gamenight.core.Packs.count(c) >= 20)
            c.taboo.forEach { assertTrue(it.w, it.no.size == 5) }
        }
    }

    @Test fun newDecksAreThereInBothLanguages() {
        for (lang in listOf("el", "en")) {
            val c = Cards.load(lang) { name -> File("src/main/assets/content/$lang/$name.json").takeIf { it.exists() }?.readText() }
            listOf(c.whois, c.knowme, c.tower, c.court, c.aiq).forEach { assertTrue("$lang prompts", it.size >= 20) }
            assertTrue(c.dice.size >= 50 && c.mime.size >= 60 && c.whisper.size >= 50 && c.bottle.size >= 40 && c.closest.size >= 50 && c.spy.size >= 30 && c.quiz.size >= 60)
            assertTrue(c.mime.count { it.c == "r" } >= 20); assertTrue(c.whisper.count { it.c == "w" } >= 30)
            c.quiz.forEach { assertEquals(it.q, 3, it.no.size); assertFalse(it.q, it.ok in it.no) }
            c.spy.forEach { assertTrue(it.place, it.roles.size >= 4) }
        }
    }

    // ---- arcade 1.1 ----
    @Test fun dualDodgerThatSwitchesInTimeSurvives() {
        val g = DualGame(Random(4))
        repeat(60 * 40) {
            for (s in 0..1) {
                val danger = g.blocks.filter { it.side == s && !it.passed && it.lane == g.lane[s] && it.y in (g.dotY - 0.25f)..(g.dotY - 0.05f) }
                val other = g.blocks.any { it.side == s && !it.passed && it.lane != g.lane[s] && it.y in (g.dotY - 0.25f)..(g.dotY + 0.05f) }
                if (danger.isNotEmpty() && !other) g.tap(s)
            }
            g.step(1 / 60f)
        }
        assertTrue("score ${g.score}", g.score > 20)
        val h = DualGame(Random(1)); var r = 0; repeat(60 * 30) { if (r >= 0) r = h.step(1 / 60f) }
        assertTrue(h.over)
    }

    @Test fun ringBouncesOnCalmPartsAndStopsOnSpikes() {
        val g = RingGame(Random(2))
        var cool = 0
        while (!g.over && g.bounces < 30) {
            // a bot that reverses once when the ball is heading for a spike
            if (cool > 0) cool-- else if (g.out && g.ballR > 0.6f && g.sectorAt(g.ballA) in g.spikes) { g.tap(); cool = 20 }
            g.step(1 / 60f)
        }
        assertTrue("bounces ${g.bounces}", g.bounces >= 5)
    }

    @Test fun laserHitsWhoeverStaysInTheLine() {
        val g = LaserGame(Random(3))
        var r = 0
        repeat(60 * 20) { if (r >= 0) r = g.step(1 / 60f) }
        assertTrue(g.over)
        val h = LaserGame(Random(3))
        repeat(60 * 15) {
            fun danger(rr: Int, cc: Int) = h.lasers.any { l -> if (l.row) l.index == rr else l.index == cc }
            if (danger(h.r, h.c)) {
                val safe = (0 until h.size * h.size).map { it / h.size to it % h.size }.filter { (rr, cc) -> !danger(rr, cc) }.minByOrNull { (rr, cc) -> abs((rr - h.r).toFloat()) + abs((cc - h.c).toFloat()) }
                if (safe != null) { repeat(4) { h.move((safe.first - h.r).coerceIn(-1, 1), 0) }; repeat(4) { h.move(0, (safe.second - h.c).coerceIn(-1, 1)) } }
            } else h.core?.let { k -> if (!danger(k.r, h.c)) h.move((k.r - h.r).coerceIn(-1, 1), 0); if (!danger(h.r, k.c)) h.move(0, (k.c - h.c).coerceIn(-1, 1)) }
            h.step(1 / 60f)
        }
        assertTrue("score ${h.score}", h.score > 5)
    }

    @Test fun spiralShieldThatFollowsTheBallScores() {
        val g = SpiralGame(Random(5))
        repeat(60 * 30) { g.balls.firstOrNull { it.alive && it.vr > 0 }?.let { g.shield = it.a }; g.step(1 / 60f) }
        assertFalse(g.over); assertTrue("score ${g.score}", g.score > 10); assertTrue(g.perfects > 0)
        val h = SpiralGame(Random(5)); h.shield = (PI).toFloat()
        var r = 0; repeat(60 * 60) { if (r >= 0) r = h.step(1 / 60f) }
        assertTrue(h.over)
        assertEquals(0f, angleGap(0.1f, (2 * PI).toFloat() + 0.1f), 1e-4f)
    }

    // ---- motion 1.1 ----
    @Test fun droneMazeHasAWayToTheGoal() {
        repeat(10) { seed ->
            val m = DroneMaze(rnd = Random(seed))
            val seen = HashSet<Pair<Int, Int>>(); val q = ArrayDeque(listOf(1 to 1)); seen += 1 to 1
            while (q.isNotEmpty()) { val (x, y) = q.removeFirst(); for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) { val n = x + dx to y + dy; if (!m.wall[n.second][n.first] && seen.add(n)) q.addLast(n) } }
            assertTrue("seed $seed", m.goal in seen)
        }
        val m = DroneMaze(rnd = Random(1))
        repeat(120) { m.step(0f, 0f, 1 / 60f) }
        assertEquals(1.5f, m.x, 0.01f)
    }

    @Test fun defuseBlowsOnTiltOrJolt() {
        val flat = MotionNow(0f, 0f, 9.81f, 0f, true)
        assertFalse(DefuseRules.boom(flat, flat))
        val ten = MotionNow(9.81f * kotlin.math.sin(Math.toRadians(10.0)).toFloat(), 0f, 9.81f * kotlin.math.cos(Math.toRadians(10.0)).toFloat(), 0f, true)
        assertEquals(10.0, DefuseRules.tilt(flat, ten), 0.1)
        assertFalse(DefuseRules.boom(flat, ten))
        val twenty = MotionNow(9.81f * kotlin.math.sin(Math.toRadians(20.0)).toFloat(), 0f, 9.81f * kotlin.math.cos(Math.toRadians(20.0)).toFloat(), 0f, true)
        assertTrue(DefuseRules.boom(flat, twenty))
        assertTrue(DefuseRules.boom(flat, flat.copy(jerk = 9f)))
    }

    @Test fun iceCursorPicksACornerAfterDwelling() {
        val c = IceCursor(Random(1)); c.spikes.clear()
        var picked = -1
        repeat(60 * 12) { if (picked < 0) { val r = c.step(9f, 9f, 1 / 60f); if (r >= 0) picked = r } }
        assertEquals(3, picked)
    }

    @Test fun safeOpensOnlyTheRightWay() {
        val d = SafeDial(listOf(5, 30, 10))
        d.feed(0f, 1 / 60f)
        var w = 0f
        fun to(target: Int, cw: Boolean) { var guard = 0; while (d.number != target && guard++ < 2000) { w += if (cw) 2f else -2f; d.feed(w, 1 / 60f) } }
        to(5, true); assertTrue(d.lock())
        to(30, false); assertTrue(d.lock())
        to(10, true); assertTrue(d.lock()); assertTrue(d.open)
        val e = SafeDial(listOf(5, 30, 10)); e.feed(0f, 1 / 60f)
        var x = 0f; while (e.number != 7) { x += 2f; e.feed(x, 1 / 60f) }
        assertFalse(e.lock()); assertEquals(0, e.step); assertEquals(1, e.resets)
        val f = SafeDial(listOf(5, 30, 10)); f.feed(0f, 1 / 60f); assertEquals("reset", f.feed(40f, 1 / 60f))
    }

    private fun abs(f: Float) = kotlin.math.abs(f)
}
