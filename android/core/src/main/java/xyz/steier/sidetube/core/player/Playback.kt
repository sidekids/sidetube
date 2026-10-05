// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.player

/** Ein Eintrag der Warteschlange. */
data class PlayableVideo(val videoId: String, val title: String)

enum class PlaybackStatus { Loading, Playing, Paused, Ended, Skipped, Failed }

data class PlaybackState(
    val queue: List<PlayableVideo> = emptyList(),
    val index: Int = 0,
    val status: PlaybackStatus = PlaybackStatus.Loading,
    val positionSeconds: Int = 0,
    val skippedTitle: String? = null
) {
    val current: PlayableVideo? get() = queue.getOrNull(index)
    val positionLabel: String get() = "%d:%02d".format(positionSeconds / 60, positionSeconds % 60)
    val progressLabel: String get() = if (queue.isEmpty()) "" else "${index + 1} von ${queue.size}"
}

/**
 * Fuehrt die Warteschlange und rechnet die Sehzeit.
 *
 * Ohne Android und ohne Systemuhr, damit beides pruefbar bleibt: Die Sehzeit ist die Grundlage
 * des Tageslimits – rechnet sie falsch, greift die Grenze zu frueh oder gar nicht.
 */
class PlaybackModel(
    queue: List<PlayableVideo>,
    startIndex: Int = 0,
    private val autoAdvance: Boolean = false,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val budgetSeconds: Int? = null,
    /** Wall-clock time, separate from the monotonic [now]: only used to attribute accumulated
     * seconds to the calendar day they were actually watched on. */
    private val wallClockNow: () -> Long = System::currentTimeMillis,
    private val zone: java.util.TimeZone = java.util.TimeZone.getDefault()
) {
    var state = PlaybackState(queue = queue, index = startIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0)))
        private set

    private var playingSince: Long? = null
   /** Wall-clock time [playingSince] corresponds to - captured once per "playing" stretch so a
    * stretch that runs past midnight can be split by calendar day when it is closed. */
    private var playingSinceWallClock: Long? = null
   /** Already-elapsed millis of the current video, keyed by the calendar day start (epoch millis,
    * device/injected time zone) they were watched on. Almost always a single entry; more than one
    * only when a continuous stretch crossed midnight. */
    private val accumulatedMillisByDay = mutableMapOf<Long, Long>()
    private var sessionMillis: Long = 0
    private val failed = mutableSetOf<String>()

    /** Video-IDs, die der IFrame von sich aus starten wollte und die gestoppt wurden (Diagnose, Tests). */
    val blockedForeignVideoIds: List<String> get() = _blockedForeignVideoIds
    private val _blockedForeignVideoIds = mutableListOf<String>()

    /** Gespielte Sekunden des laufenden Videos, auch waehrend es noch laeuft. */
    val watchedSeconds: Int
        get() {
            val running = playingSince?.let { (now() - it).coerceAtLeast(0) } ?: 0
            return ((accumulatedMillisByDay.values.sum() + running) / 1000).toInt()
        }

    /** One deadline while playing, no recurring idle timer. Budget survives queue changes. */
    val remainingBudgetMillis: Long?
        get() = budgetSeconds?.let {
            val running = playingSince?.let { start -> (now() - start).coerceAtLeast(0) } ?: 0
            (it.toLong().coerceAtLeast(0) * 1000 - sessionMillis - running).coerceAtLeast(0)
        }

    fun checkBudget(): List<Command> {
        if (remainingBudgetMillis != 0L) return emptyList()
        val recorded = closeCurrent()
        state = state.copy(status = PlaybackStatus.Ended)
        return recorded + Command.Stop + Command.LimitReached + Command.Done
    }

    /** Was die Bruecke tun soll, nachdem ein Ereignis verarbeitet wurde. */
    sealed interface Command {
        data class Load(val videoId: String) : Command
        data object Stop : Command
        data object Pause : Command
        data object Resume : Command
        /** Im laufenden Video um [seconds] spulen (negativ = zurueck); SideUI ADR 0015. */
        data class SeekBy(val seconds: Int) : Command
        /** Lautstaerke 0..100; nur die Ausblendung des Schlaf-Timers nutzt das. */
        data class SetVolume(val percent: Int) : Command
        data object LimitReached : Command
        /**
         * Sehzeit fuer das abgeschlossene Video buchen. `at` ist nur fuer einen bereits
         * abgeschlossenen frueheren Kalendertag gesetzt (Mitternachts-Uebergang); `null` heisst
         * "aktueller Tag", der Aufrufer stempelt dann mit der echten Uhrzeit beim Verarbeiten -
         * unveraendert gegenueber dem bisherigen Verhalten, wenn kein Uebergang stattfand.
         */
        data class Record(val video: PlayableVideo, val seconds: Int, val at: Long? = null) : Command
        data object Done : Command
    }

    fun onState(value: Int): List<Command> {
        val limit = checkBudget()
        if (limit.isNotEmpty()) return limit
        return when (value) {
        1 -> {
            if (playingSince == null) { playingSince = now(); playingSinceWallClock = wallClockNow() }
            state = state.copy(status = PlaybackStatus.Playing); emptyList()
        }
        2 -> { pauseAccounting(); state = state.copy(status = PlaybackStatus.Paused); emptyList() }
        0 -> finishCurrent(advance = autoAdvance)
        3, -1, 5 -> { pauseAccounting(); state = state.copy(status = PlaybackStatus.Loading); emptyList() }
        else -> emptyList()
        }
    }

    /**
     * Fehler 101 und 150 heissen: Der Rechteinhaber erlaubt keine Einbettung. Das Video ist
     * nicht abspielbar, also wird es uebersprungen statt das Kind vor einem schwarzen Bild
     * sitzen zu lassen.
     */
    fun onError(code: Int): List<Command> {
        val recorded = closeCurrent()
        val skipped = state.current
        skipped?.let { failed += it.videoId }
        state = state.copy(status = PlaybackStatus.Skipped, skippedTitle = skipped?.title)
        return recorded + advanceToPlayable()
    }

    fun onTime(seconds: Int): List<Command> {
        state = state.copy(positionSeconds = seconds)
        return emptyList()
    }

    /**
     * The IFrame wanted to play a video the app never requested (end-screen card, pause-screen
     * suggestion). Mirrors iOS's `.foreignVideo` handling: save only the already-requested
     * video's accumulated time under its own ID, never under the foreign one, then stop -
     * regardless of autoplay - so nothing unrequested keeps running unattended.
     */
    fun onForeignVideo(foreignVideoId: String): List<Command> {
        _blockedForeignVideoIds += foreignVideoId
        val recorded = closeCurrent()
        state = state.copy(status = PlaybackStatus.Ended)
        return recorded + Command.Stop
    }

    fun next(): List<Command> = finishCurrent(advance = true)

    /**
     * Spulen mit dem Ring (hoch/runter, SideUI ADR 0015). Nur in einem Video, das laeuft oder
     * angehalten ist; die Sehzeit misst die Uhr, nicht die Stelle im Video – Spulen aendert sie nicht.
     */
    fun seekBy(seconds: Int): List<Command> =
        if (state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Paused) {
            listOf(Command.SeekBy(seconds))
        } else {
            emptyList()
        }

    fun previous(): List<Command> {
        val commands = closeCurrent()
        state = state.copy(index = (state.index - 1).coerceAtLeast(0), positionSeconds = 0)
        return commands + startCurrent()
    }

    /** Beim Verlassen des Players: Auch angefangene Zeit wird gebucht. */
    fun close(): List<Command> = closeCurrent() + Command.Stop

    fun start(): List<Command> = startCurrent()

    /** „Nochmal" auf der Endkarte: dasselbe Video von vorn; die Sehzeit zaehlt wie jede Wiedergabe. */
    fun replay(): List<Command> {
        val commands = closeCurrent()
        state = state.copy(positionSeconds = 0, skippedTitle = null)
        return commands + startCurrent()
    }

    private fun finishCurrent(advance: Boolean): List<Command> {
        val commands = closeCurrent()
        state = state.copy(status = PlaybackStatus.Ended)
        if (!advance) return commands + Command.Stop
        if (state.index + 1 >= state.queue.size) return commands + Command.Stop + Command.Done
        state = state.copy(index = state.index + 1, positionSeconds = 0)
        return commands + startCurrent()
    }

    private fun advanceToPlayable(): List<Command> {
        var next = state.index + 1
        while (next < state.queue.size && state.queue[next].videoId in failed) next++
        if (next >= state.queue.size) {
            state = state.copy(status = PlaybackStatus.Failed)
            return listOf(Command.Stop, Command.Done)
        }
        state = state.copy(index = next, positionSeconds = 0)
        return startCurrent()
    }

    private fun startCurrent(): List<Command> {
        val limit = checkBudget()
        if (limit.isNotEmpty()) return limit
        val video = state.current ?: return listOf(Command.Done)
        state = state.copy(status = PlaybackStatus.Loading)
        return listOf(Command.Load(video.videoId))
    }

    private fun closeCurrent(): List<Command> {
        pauseAccounting()
        val video = state.current
        if (video == null || accumulatedMillisByDay.isEmpty()) {
            accumulatedMillisByDay.clear()
            return emptyList()
        }
        val sortedDays = accumulatedMillisByDay.keys.sorted()
        val commands = sortedDays.mapIndexedNotNull { index, day ->
            val millis = accumulatedMillisByDay.getValue(day)
            // Persist partial seconds conservatively so repeated short sessions cannot evade a limit.
            val seconds = ((millis + 999) / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (seconds <= 0) return@mapIndexedNotNull null
            // The most recent (or only) day books at the actual processing time (`at = null`),
            // matching prior single-record behavior exactly when nothing crossed midnight. An
            // earlier, already-completed day books at its own start-of-day - any timestamp within
            // that day attributes correctly, since `secondsToday`/`dayBounds` use a [start, next) range.
            val isLast = index == sortedDays.lastIndex
            Command.Record(video, seconds, at = if (isLast) null else day)
        }
        accumulatedMillisByDay.clear()
        return commands
    }

    private fun pauseAccounting() {
        playingSince?.let { since ->
            val elapsed = (now() - since).coerceAtLeast(0)
            sessionMillis += elapsed
            playingSinceWallClock?.let { wallStart -> addAccumulated(elapsed, wallStart) }
        }
        playingSince = null
        playingSinceWallClock = null
    }

   /**
    * Splits one continuous playing stretch into per-calendar-day millis, so a stretch that runs
    * past midnight is booked to both days instead of entirely to whichever day it happens to be
    * closed on. Monotonic elapsed time is treated as equal to wall-clock elapsed time within this
    * one stretch - a device clock change mid-stretch is a separate, already-documented
    * threat-model caveat, not something this split needs to guard against on its own.
    */
    private fun addAccumulated(elapsedMillis: Long, startWallClock: Long) {
        var remaining = elapsedMillis
        var cursor = startWallClock
        while (remaining > 0) {
            val (dayStart, nextDayStart) = dayBounds(cursor)
            val slice = minOf(remaining, nextDayStart - cursor)
            accumulatedMillisByDay[dayStart] = (accumulatedMillisByDay[dayStart] ?: 0) + slice
            remaining -= slice
            cursor = nextDayStart
        }
    }

    private fun dayBounds(at: Long): Pair<Long, Long> {
        val calendar = java.util.Calendar.getInstance(zone).apply {
            timeInMillis = at
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        return start to calendar.timeInMillis
    }
}
