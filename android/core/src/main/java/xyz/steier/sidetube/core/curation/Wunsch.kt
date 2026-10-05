// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.curation

import xyz.steier.sidetube.core.db.WishEntity
import xyz.steier.sidetube.core.input.Radsuche
import java.time.Instant
import java.time.ZoneId

/** Die drei Wege eines Wunsches (ADR 0001); die Kennungen gelten auf iOS und Android. */
enum class WunschArt(val id: String) {
    THEMA("thema"), MEHR_DAVON("mehrDavon"), NEUE_FOLGE("neueFolge");

    companion object {
        fun from(id: String?): WunschArt? = entries.firstOrNull { it.id == id }
    }
}

enum class WunschStatus(val id: String) {
    OFFEN("offen"), ERFUELLT("erfuellt"), ABGELEHNT("abgelehnt"), BESPRECHEN("besprechen");

    /** Noch nicht abgeschlossen: Eltern sehen ihn in der Pruefliste. */
    val istOffen: Boolean get() = this == OFFEN || this == BESPRECHEN

    companion object {
        fun from(id: String?): WunschStatus? = entries.firstOrNull { it.id == id }
    }
}

/** Was das Kind sich wuenscht, bevor es gespeichert ist. */
sealed interface WunschEntwurf {
    val art: WunschArt

    data class Thema(val text: String) : WunschEntwurf {
        override val art get() = WunschArt.THEMA
    }

    data class MehrDavon(
        val videoId: String, val videoTitle: String, val channelId: String?, val channelTitle: String?
    ) : WunschEntwurf {
        override val art get() = WunschArt.MEHR_DAVON
    }

    data class NeueFolge(
        val videoId: String, val videoTitle: String, val channelId: String, val channelTitle: String
    ) : WunschEntwurf {
        override val art get() = WunschArt.NEUE_FOLGE
    }
}

/**
 * Die Regeln fuer Wuensche, rein und ohne Datenbank (ADR 0001): hoechstens [TAGESGRENZE] je
 * Profil und Kalendertag, gleiche Wuensche entstehen nicht neu.
 *
 * **Doppelt** ist ein Wunsch mit demselben Schluessel, solange der vorige offen ist oder besprochen
 * wird. Nach „erfuellt" oder „nicht jetzt" darf derselbe Wunsch neu entstehen – die Tagesgrenze
 * begrenzt Wiederholungen. So steht es auch in der iOS-Fassung (ADR 0001, „Umsetzung iOS").
 */
object WunschRegeln {
    const val TAGESGRENZE = 3
    /** Laenger ist kein Stichwort mehr; das Rad hat ohnehin nur Buchstaben. */
    const val THEMA_MAX = 30

    /** Stichwort in Anzeigeform: getrimmt, Luecken einfach, hoechstens [THEMA_MAX] Zeichen. */
    fun thema(text: String): String =
        text.trim().replace(Regex("\\s+"), " ").take(THEMA_MAX).trim()

    fun schluessel(entwurf: WunschEntwurf): String = when (entwurf) {
        // Gross/klein und Umlaute zaehlen nicht: „Dinos" und „dinos" sind derselbe Wunsch.
        is WunschEntwurf.Thema -> "thema:" + Radsuche.eingabe(thema(entwurf.text)).trim()
        is WunschEntwurf.MehrDavon -> "mehrDavon:" + entwurf.videoId
        is WunschEntwurf.NeueFolge -> "neueFolge:" + entwurf.videoId
    }

    fun istDoppelt(vorhandene: List<WishEntity>): Boolean =
        vorhandene.any { WunschStatus.from(it.status)?.istOffen == true }

    /** Laenger ist keine kurze Antwort mehr (wie iOS). */
    const val ANTWORT_MAX = 200

    /** Beginn und Ende des Kalendertags von [at] in [zone]. */
    fun tag(at: Instant, zone: ZoneId): Pair<Long, Long> {
        val start = at.atZone(zone).toLocalDate().atStartOfDay(zone)
        return start.toInstant().toEpochMilli() to start.plusDays(1).toInstant().toEpochMilli()
    }

    /** Wie viele Wuensche heute noch gehen, aus den Wuenschen des Profils. */
    fun heuteNoch(wuensche: List<WishEntity>, at: Instant, zone: ZoneId): Int {
        val (von, bis) = tag(at, zone)
        return (TAGESGRENZE - wuensche.count { it.createdAt in von until bis }).coerceAtLeast(0)
    }
}
