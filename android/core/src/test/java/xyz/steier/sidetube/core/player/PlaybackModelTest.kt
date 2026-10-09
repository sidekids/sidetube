// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Testuhr: Die Sehzeit muss ohne Warten pruefbar sein. */
private class Clock(var millis: Long = 0) : () -> Long {
    override fun invoke(): Long = millis
    fun advance(seconds: Int) { millis += seconds * 1000L }
}

class PlaybackModelTest {

    private val queue = listOf(
        PlayableVideo("a", "Erstes"),
        PlayableVideo("b", "Zweites"),
        PlayableVideo("c", "Drittes")
    )

    @Test
    fun `Spulen gibt es nur in einem laufenden oder angehaltenen Video und aendert die Sehzeit nicht`() {
        val clock = Clock()
        val model = PlaybackModel(queue, now = clock)
        model.start()
        assertThat(model.seekBy(10)).isEmpty()            // lädt noch
        model.onState(1); clock.advance(5)
        assertThat(model.seekBy(10)).containsExactly(PlaybackModel.Command.SeekBy(10))
        model.onState(2)
        assertThat(model.seekBy(-10)).containsExactly(PlaybackModel.Command.SeekBy(-10))
        assertThat(model.watchedSeconds).isEqualTo(5)
        assertThat(model.state.index).isEqualTo(0)
    }

    @Test
    fun `buffering unstarted and cued states do not count as viewing`() {
        for (state in listOf(3, -1, 5)) {
            val clock = Clock()
            val model = PlaybackModel(queue, now = clock)
            model.start()
            model.onState(1); clock.advance(8)
            model.onState(state); clock.advance(100)
            assertThat(model.watchedSeconds).isEqualTo(8)
            model.onState(1); clock.advance(2)
            assertThat(model.close()).contains(PlaybackModel.Command.Record(queue[0], 10))
        }
    }

    @Test
    fun `nach dem Ende aendern Zustaende und Stellen des Players nichts mehr an der Endkarte`() {
        val clock = Clock()
        val model = PlaybackModel(queue, now = clock)
        model.start(); model.onState(1); clock.advance(10); model.onTime(10)
        assertThat(model.onState(0)).containsExactly(PlaybackModel.Command.Record(queue[0], 10),
            PlaybackModel.Command.Stop).inOrder()
        // stopVideo() meldet -1 und 5; mit controls: 0 geht YouTube nach dem Ende von selbst auf „bereit".
        for (state in listOf(-1, 5, 3, 2)) {
            assertThat(model.onState(state)).isEmpty()
            assertThat(model.state.status).isEqualTo(PlaybackStatus.Ended)
        }
        assertThat(model.onTime(0)).isEmpty()
        assertThat(model.state.positionSeconds).isEqualTo(10)
        // Spielt der Player dennoch wieder, wird gestoppt – und nichts gebucht.
        assertThat(model.onState(1)).containsExactly(PlaybackModel.Command.Stop)
        clock.advance(30)
        assertThat(model.close()).containsExactly(PlaybackModel.Command.Stop)
        assertThat(model.state.status).isEqualTo(PlaybackStatus.Ended)
    }

    @Test
    fun `Nochmal laedt dasselbe Video neu und zaehlt weiter gegen das Budget`() {
        val clock = Clock()
        val model = PlaybackModel(queue, startIndex = 1, now = clock, budgetSeconds = 30)
        model.start(); model.onState(1); clock.advance(10)
        assertThat(model.onState(0)).containsExactly(PlaybackModel.Command.Record(queue[1], 10),
            PlaybackModel.Command.Stop).inOrder()
        assertThat(model.state.status).isEqualTo(PlaybackStatus.Ended)

        assertThat(model.replay()).containsExactly(PlaybackModel.Command.Load("b"))
        assertThat(model.state.index).isEqualTo(1)
        assertThat(model.state.positionSeconds).isEqualTo(0)
        model.onState(1); clock.advance(20)
        assertThat(model.remainingBudgetMillis).isEqualTo(0)
        assertThat(model.replay().none { it is PlaybackModel.Command.Load }).isTrue()
    }

    @Test
    fun `exhausted and invalid budgets never load a video`() {
        for (budget in listOf(0, -1)) {
            val model = PlaybackModel(queue, budgetSeconds = budget)
            assertThat(model.start()).containsExactly(PlaybackModel.Command.Stop,
                PlaybackModel.Command.LimitReached, PlaybackModel.Command.Done).inOrder()
            assertThat(model.next().none { it is PlaybackModel.Command.Load }).isTrue()
            assertThat(model.previous().none { it is PlaybackModel.Command.Load }).isTrue()
        }
    }

    @Test
    fun `exact deadline records once stops and denies subsequent playback events`() {
        val clock = Clock()
        val model = PlaybackModel(queue, now = clock, budgetSeconds = 10)
        model.start(); model.onState(1); clock.advance(10)
        assertThat(model.remainingBudgetMillis).isEqualTo(0)
        assertThat(model.checkBudget()).containsExactly(PlaybackModel.Command.Record(queue[0], 10),
            PlaybackModel.Command.Stop, PlaybackModel.Command.LimitReached,
            PlaybackModel.Command.Done).inOrder()
        assertThat(model.onState(1)).contains(PlaybackModel.Command.LimitReached)
        assertThat(model.close()).containsExactly(PlaybackModel.Command.Stop)
    }

    @Test
    fun `pause and buffering preserve budget while queue changes do not reset it`() {
        val clock = Clock()
        val model = PlaybackModel(queue, now = clock, budgetSeconds = 10)
        model.start(); model.onState(1); clock.advance(3)
        model.onState(2); clock.advance(100)
        assertThat(model.remainingBudgetMillis).isEqualTo(7000)
        model.onState(1); clock.advance(2)
        model.onState(3); clock.advance(100)
        assertThat(model.remainingBudgetMillis).isEqualTo(5000)
        model.next(); model.onState(1); clock.advance(5)
        assertThat(model.checkBudget()).contains(PlaybackModel.Command.LimitReached)
    }

    @Test
    fun `autoplay cannot load next video at the budget boundary`() {
        val clock = Clock()
        val model = PlaybackModel(queue, autoAdvance = true, now = clock, budgetSeconds = 2)
        model.start(); model.onState(1); clock.advance(2)
        val commands = model.onState(0)
        assertThat(commands).contains(PlaybackModel.Command.LimitReached)
        assertThat(commands.none { it is PlaybackModel.Command.Load }).isTrue()
    }

    @Test
    fun `unlimited profiles have no deadline`() {
        val clock = Clock()
        val model = PlaybackModel(queue, now = clock)
        model.start(); model.onState(1); clock.advance(100000)
        assertThat(model.remainingBudgetMillis).isNull()
        assertThat(model.checkBudget()).isEmpty()
    }

    @Test
    fun `fractional viewing is persisted instead of lost on repeated short sessions`() {
        val clock = Clock()
        val model = PlaybackModel(queue, now = clock)
        model.start(); model.onState(1); clock.millis = 100
        assertThat(model.close()).contains(PlaybackModel.Command.Record(queue[0], 1))
    }

    @Test
    fun `error records the failed video before loading the next without leaking its time`() {
        val clock = Clock()
        val model = PlaybackModel(queue, now = clock)
        model.start(); model.onState(1); clock.advance(12)
        assertThat(model.onError(150)).containsExactly(
            PlaybackModel.Command.Record(queue[0], 12), PlaybackModel.Command.Load("b")
        ).inOrder()
        clock.advance(100)
        assertThat(model.watchedSeconds).isEqualTo(0)
        model.onState(1); clock.advance(7)
        assertThat(model.close()).containsExactly(
            PlaybackModel.Command.Record(queue[1], 7), PlaybackModel.Command.Stop
        ).inOrder()
        assertThat(model.close()).containsExactly(PlaybackModel.Command.Stop)
    }

    @Test
    fun `last video failure records time before terminating`() {
        val clock = Clock()
        val model = PlaybackModel(queue, startIndex = 2, now = clock)
        model.start(); model.onState(1); clock.advance(9)
        assertThat(model.onError(101)).containsExactly(
            PlaybackModel.Command.Record(queue[2], 9), PlaybackModel.Command.Stop,
            PlaybackModel.Command.Done
        ).inOrder()
        assertThat(model.close()).containsExactly(PlaybackModel.Command.Stop)
    }

    @Test
    fun `duplicate playing events do not reset elapsed time`() {
        val clock = Clock()
        val model = PlaybackModel(queue, now = clock)
        model.start(); model.onState(1); clock.advance(6)
        model.onState(1); clock.advance(4)
        assertThat(model.watchedSeconds).isEqualTo(10)
    }

    @Test
    fun `backward injected clock cannot create negative watch records`() {
        val clock = Clock(10000)
        val model = PlaybackModel(queue, now = clock)
        model.start(); model.onState(1)
        clock.millis = 1000
        assertThat(model.watchedSeconds).isEqualTo(0)
        assertThat(model.close()).containsExactly(PlaybackModel.Command.Stop)
    }

    @Test
    fun `beim Start wird das gewaehlte Video geladen`() {
        val model = PlaybackModel(queue, startIndex = 1)

        val commands = model.start()

        assertThat(commands).containsExactly(PlaybackModel.Command.Load("b"))
        assertThat(model.state.progressLabel).isEqualTo("2 von 3")
    }

    @Test
    fun `nicht einbettbare Videos werden uebersprungen statt schwarz zu bleiben`() {
        val model = PlaybackModel(queue)
        model.start()

        val commands = model.onError(150)   // Rechteinhaber erlaubt keine Einbettung

        assertThat(commands).contains(PlaybackModel.Command.Load("b"))
        assertThat(model.state.skippedTitle).isEqualTo("Erstes")
    }

    @Test
    fun `scheitern alle, endet die Wiedergabe statt endlos zu suchen`() {
        val model = PlaybackModel(queue)
        model.start()

        model.onError(101)
        model.onError(101)
        val letzte = model.onError(101)

        assertThat(letzte).contains(PlaybackModel.Command.Done)
        assertThat(model.state.status).isEqualTo(PlaybackStatus.Failed)
    }

    @Test
    fun `ohne Autoplay bleibt es am Ende stehen`() {
        val model = PlaybackModel(queue, autoAdvance = false)
        model.start()
        model.onState(1)

        val commands = model.onState(0)   // Video zu Ende

        assertThat(commands).contains(PlaybackModel.Command.Stop)
        assertThat(commands.none { it is PlaybackModel.Command.Load }).isTrue()
    }

    @Test
    fun `mit Autoplay laeuft das naechste an`() {
        val model = PlaybackModel(queue, autoAdvance = true)
        model.start()
        model.onState(1)

        val commands = model.onState(0)

        assertThat(commands).contains(PlaybackModel.Command.Load("b"))
    }

    @Test
    fun `die Sehzeit zaehlt nur, solange wirklich gespielt wird`() {
        val clock = Clock()
        val model = PlaybackModel(queue, now = clock)
        model.start()

        model.onState(1); clock.advance(30)     // 30 s Wiedergabe
        model.onState(2); clock.advance(120)    // zwei Minuten Pause zaehlen nicht
        model.onState(1); clock.advance(15)     // weitere 15 s

        assertThat(model.watchedSeconds).isEqualTo(45)
    }

    @Test
    fun `beim Verlassen wird auch angefangene Zeit gebucht`() {
        val clock = Clock()
        val model = PlaybackModel(queue, now = clock)
        model.start()
        model.onState(1)
        clock.advance(42)

        val commands = model.close()

        assertThat(commands).contains(PlaybackModel.Command.Record(PlayableVideo("a", "Erstes"), 42))
        assertThat(commands).contains(PlaybackModel.Command.Stop)
    }

    @Test
    fun `beim Wechsel wird die Zeit des vorigen Videos gebucht`() {
        val clock = Clock()
        val model = PlaybackModel(queue, now = clock)
        model.start()
        model.onState(1)
        clock.advance(60)

        val commands = model.next()

        assertThat(commands).contains(PlaybackModel.Command.Record(PlayableVideo("a", "Erstes"), 60))
        assertThat(commands).contains(PlaybackModel.Command.Load("b"))
    }

    @Test
    fun `Zeit wird nicht doppelt gebucht`() {
        val clock = Clock()
        val model = PlaybackModel(queue, now = clock)
        model.start(); model.onState(1); clock.advance(20)

        model.next()
        clock.advance(10)
        val zweiteBuchung = model.close()

        val gebucht = zweiteBuchung.filterIsInstance<PlaybackModel.Command.Record>()
        assertThat(gebucht).isEmpty()   // seit dem Wechsel lief nichts
    }

    @Test
    fun `am Anfang fuehrt zurueck nicht ins Leere`() {
        val model = PlaybackModel(queue)
        model.start()

        model.previous()

        assertThat(model.state.index).isEqualTo(0)
    }

    @Test
    fun `die Position wird lesbar dargestellt`() {
        val model = PlaybackModel(queue)
        model.onTime(75)
        assertThat(model.state.positionLabel).isEqualTo("1:15")
        model.onTime(5)
        assertThat(model.state.positionLabel).isEqualTo("0:05")
    }

    @Test
    fun `ein von der Einbettung selbst gestartetes fremdes Video wird gestoppt statt weiterzulaufen`() {
        val clock = Clock()
        val model = PlaybackModel(queue, now = clock)
        model.start(); model.onState(1); clock.advance(20)

        val commands = model.onForeignVideo("zzzzzzzzzzz")

        assertThat(commands).containsExactly(
            PlaybackModel.Command.Record(queue[0], 20), PlaybackModel.Command.Stop
        ).inOrder()
        assertThat(model.blockedForeignVideoIds).containsExactly("zzzzzzzzzzz")
        assertThat(model.state.status).isEqualTo(PlaybackStatus.Ended)
    }

    @Test
    fun `ein fremdes Video wird auch bei aktivem Autoplay nicht als Weiterlaufen gewertet`() {
        val model = PlaybackModel(queue, autoAdvance = true)
        model.start(); model.onState(1)

        val commands = model.onForeignVideo("zzzzzzzzzzz")

        assertThat(commands.none { it is PlaybackModel.Command.Load }).isTrue()
        assertThat(model.state.status).isEqualTo(PlaybackStatus.Ended)
    }

    @Test
    fun `die fremde ID wird nie gebucht, nur die des angeforderten Videos`() {
        val clock = Clock()
        val model = PlaybackModel(queue, now = clock)
        model.start(); model.onState(1); clock.advance(5)

        val commands = model.onForeignVideo("zzzzzzzzzzz")

        assertThat(commands.filterIsInstance<PlaybackModel.Command.Record>()
            .none { it.video.videoId == "zzzzzzzzzzz" }).isTrue()
    }

    private fun berlinMidnightEve(): Pair<java.util.TimeZone, Long> {
        val zone = java.util.TimeZone.getTimeZone("Europe/Berlin")
        val calendar = java.util.Calendar.getInstance(zone).apply {
            set(2026, java.util.Calendar.JANUARY, 1, 23, 59, 50)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return zone to calendar.timeInMillis
    }

    @Test
    fun `eine durchgehende Wiedergabe ueber Mitternacht wird pro Kalendertag gebucht`() {
        val clock = Clock()
        val (zone, start) = berlinMidnightEve()
        var wallClock = start
        val model = PlaybackModel(queue, now = clock, wallClockNow = { wallClock }, zone = zone)
        model.start(); model.onState(1)
        clock.advance(20); wallClock += 20_000   // 23:59:50 -> 00:00:10 am naechsten Tag

        val commands = model.close().filterIsInstance<PlaybackModel.Command.Record>()

        assertThat(commands).hasSize(2)
        assertThat(commands[0].seconds).isEqualTo(10)
        assertThat(commands[0].at).isNotNull()   // abgeschlossener frueherer Tag: eigener Zeitstempel
        assertThat(commands[1].seconds).isEqualTo(10)
        assertThat(commands[1].at).isNull()      // aktueller Tag: Aufrufer stempelt mit echter Zeit
    }

    @Test
    fun `Sehzeit ueber Mitternacht zaehlt schon waehrend der Wiedergabe vollstaendig`() {
        val clock = Clock()
        val (zone, start) = berlinMidnightEve()
        var wallClock = start
        val model = PlaybackModel(queue, now = clock, wallClockNow = { wallClock }, zone = zone)
        model.start(); model.onState(1)
        clock.advance(10); wallClock += 10_000   // ueberschreitet Mitternacht, noch keine Pause/kein Schliessen

        assertThat(model.watchedSeconds).isEqualTo(10)
    }

    @Test
    fun `ein Fehlschlag am frueheren Tag darf spaeter erneut versucht werden, der Aufrufer entscheidet`() {
        // PlaybackModel selbst kennt keine Fehlschlaege - es reicht nur die aufgeteilten Befehle
        // weiter. Dieser Test haelt fest, dass beide Tage als getrennte Record-Befehle ankommen,
        // nicht als ein einzelner - die Fehlerbehandlung liegt bewusst beim Aufrufer (KidViewModel).
        val clock = Clock()
        val (zone, start) = berlinMidnightEve()
        var wallClock = start
        val model = PlaybackModel(queue, now = clock, wallClockNow = { wallClock }, zone = zone)
        model.start(); model.onState(1)
        clock.advance(20); wallClock += 20_000

        val commands = model.close().filterIsInstance<PlaybackModel.Command.Record>()

        assertThat(commands.map { it.video }).containsExactly(queue[0], queue[0])
        assertThat(commands.sumOf { it.seconds }).isEqualTo(20)
    }

    @Test
    fun `ohne Mitternachts-Uebergang bleibt genau ein Record ohne Zeitstempel wie bisher`() {
        val clock = Clock()
        val model = PlaybackModel(queue, now = clock)
        model.start(); model.onState(1); clock.advance(15)

        val commands = model.close().filterIsInstance<PlaybackModel.Command.Record>()

        assertThat(commands).containsExactly(PlaybackModel.Command.Record(queue[0], 15, at = null))
    }
}
