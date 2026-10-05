// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.player

import java.time.Duration
import java.time.Instant

/** Ein laufender Schlaf-Timer. Nur der Endzeitpunkt, alles andere leitet sich aus der Uhr ab. */
data class SleepTimer(val endsAt: Instant)

/**
 * Schlaf-Timer: Die Eltern geben eine Dauer vor, danach endet die Wiedergabe von selbst.
 *
 * Etwas anderes als [BedtimePolicy] - die regelt eine wiederkehrende Uhrzeit, dies hier eine
 * einmalige Dauer ab jetzt. Beide koennen gleichzeitig gelten; was zuerst greift, beendet.
 *
 * Der Ablauf ist bewusst allein aus dem Endzeitpunkt abgeleitet und nicht als eigener Zustand
 * gefuehrt: Ein "abgelaufen"-Merker koennte gesetzt bleiben, waehrend die Uhr zurueckspringt, und
 * spaeter nicht mehr zur Uhr passen. So bleibt die Antwort immer die der aktuellen Zeit.
 *
 * Rein und geraeteunabhaengig, damit sie ohne Robolectric pruefbar ist; das ViewModel setzt die
 * Frist als abbrechbaren Job, genau wie bei der Ruhezeit - kein wiederkehrender Wecker.
 */
object SleepTimerPolicy {
    /** Sekunden vor Ablauf, in denen die Lautstaerke linear ausgeblendet wird (wie iOS). */
    const val FADE_SECONDS = 60

    /** Was die Elternoberflaeche anbieten darf; identisch zur iOS-Fassung. */
    val MINUTES = 1..180
    const val DEFAULT_MINUTES = 30

    /** Ausserhalb des Bereichs wird geklemmt statt abgelehnt: Eine Dauer ist immer gemeint. */
    fun start(minutes: Int, now: Instant): SleepTimer =
        SleepTimer(now.plusSeconds(minutes.coerceIn(MINUTES).toLong() * 60))

    /** Verbleibende Sekunden, aufgerundet; `null` ohne Timer. Nie negativ. */
    fun remainingSeconds(timer: SleepTimer?, now: Instant): Int? {
        if (timer == null) return null
        val millis = Duration.between(now, timer.endsAt).toMillis()
        if (millis <= 0) return 0
        return ((millis + 999) / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun hasExpired(timer: SleepTimer?, now: Instant): Boolean =
        timer != null && !now.isBefore(timer.endsAt)

    /**
     * Lautstaerke 0..100 waehrend der Ausblendung, `null` solange nichts zu aendern ist. Die
     * Ausblendung ist der einzige Grund, in dieser App ueberhaupt getaktet zu wecken - deshalb
     * bleibt sie auf [FADE_SECONDS] begrenzt.
     */
    fun fadeVolume(timer: SleepTimer?, now: Instant): Int? {
        val remaining = remainingSeconds(timer, now) ?: return null
        if (remaining > FADE_SECONDS) return null
        return (remaining.toDouble() / FADE_SECONDS * 100).toInt().coerceIn(0, 100)
    }

    /** "Xh Ym", "Xm" oder "<1m" - dieselbe Darstellung wie iOS. */
    fun format(seconds: Int): String {
        val minutes = Math.ceil(seconds.coerceAtLeast(0) / 60.0).toInt()
        if (minutes < 1) return "<1m"
        if (minutes < 60) return "${minutes}m"
        return "${minutes / 60}h ${minutes % 60}m"
    }
}
