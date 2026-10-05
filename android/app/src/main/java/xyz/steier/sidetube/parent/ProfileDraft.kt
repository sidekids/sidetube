// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.model.AgeBand

/**
 * Was die Profilmaske gerade zeigt. Getrennt von der Oberflaeche, damit Grenzen und Schrittweiten
 * pruefbar sind: Auf dem SidePhone gibt es keine Tastatur fuer Zahlen, nur „−“ und „+“, und jede
 * Grenze muss deshalb hier sitzen statt in einer Eingabepruefung.
 *
 * Bereiche und Schritte wie auf iOS (`ProfileEditorView`): Beginn 17:00–23:00, Ende 5:00–9:00,
 * je 15 Minuten; Fr/Sa 0–120 Minuten spaeter; Tageslimit 5–300 Minuten in Fuenferschritten.
 */
data class ProfileDraft(
    val name: String,
    val ageBand: AgeBand,
    val allowNews: Boolean,
    val allowManga: Boolean,
    val allowMangaEntertainment: Boolean,
    val allowShorts: Boolean,
    val autoplayNext: Boolean,
    val bedtimeEnabled: Boolean,
    val bedtimeStartMinutes: Int,
    val bedtimeEndMinutes: Int,
    val bedtimeWeekendOffsetMinutes: Int,
    val bedtimeSkipUntil: Long?,
    /** Eltern haben die Ausnahme in der Maske aufgehoben. */
    val skipCleared: Boolean = false,
    val limitEnabled: Boolean,
    val limitMinutes: Int
) {
    val canSave: Boolean get() = name.isNotBlank()

    /** Anime & Manga gibt es nur ab 12 und nur, wenn Manga ueberhaupt erlaubt ist. */
    val mangaEntertainmentSelectable: Boolean get() = allowManga && ageBand == AgeBand.TWEEN

    fun clearBedtimeSkip() = copy(bedtimeSkipUntil = null, skipCleared = true)

    fun stepLimit(direction: Int) = copy(limitMinutes = step(limitMinutes, direction, 5, LIMIT_RANGE))
    fun stepBedtimeStart(direction: Int) = copy(bedtimeStartMinutes = step(bedtimeStartMinutes, direction, 15, START_RANGE))
    fun stepBedtimeEnd(direction: Int) = copy(bedtimeEndMinutes = step(bedtimeEndMinutes, direction, 15, END_RANGE))
    fun stepWeekendOffset(direction: Int) =
        copy(bedtimeWeekendOffsetMinutes = step(bedtimeWeekendOffsetMinutes, direction, 15, WEEKEND_RANGE))

    /**
     * Schreibt den Entwurf auf das *aktuelle* Profil. Bewusst nicht auf die Fassung, mit der die
     * Maske geoeffnet wurde: Was dazwischen anderswo gesetzt wurde (etwa eine Ausnahme von der
     * Ruhezeit), soll das Speichern nicht unbemerkt zuruecknehmen - ausser die Eltern haben sie
     * in der Maske selbst aufgehoben.
     */
    fun applyTo(profile: KidProfileEntity): KidProfileEntity = profile.copy(
        name = name.trim(),
        ageBand = ageBand.id,
        allowNews = allowNews,
        allowManga = allowManga,
        allowMangaEntertainment = allowMangaEntertainment && mangaEntertainmentSelectable,
        allowShorts = allowShorts,
        autoplayNext = autoplayNext,
        bedtimeEnabled = bedtimeEnabled,
        bedtimeStartMinutes = bedtimeStartMinutes,
        bedtimeEndMinutes = bedtimeEndMinutes,
        bedtimeWeekendOffsetMinutes = bedtimeWeekendOffsetMinutes,
        bedtimeSkipUntil = if (skipCleared) null else profile.bedtimeSkipUntil,
        dailyLimitMinutes = if (limitEnabled) limitMinutes else null
    )

    companion object {
        val LIMIT_RANGE = 5..300
        val START_RANGE = 17 * 60..23 * 60
        val END_RANGE = 5 * 60..9 * 60
        val WEEKEND_RANGE = 0..120
        const val DEFAULT_LIMIT = 60

        /** Vorschlaege fuer den Beginn der Ruhezeit nach Alter, wie auf iOS. */
        val BEDTIME_SUGGESTIONS = listOf("6–9 Jahre" to 19 * 60, "10–12 Jahre" to 20 * 60, "ab 13" to 21 * 60)

        fun from(profile: KidProfileEntity) = ProfileDraft(
            name = profile.name,
            ageBand = AgeBand.from(profile.ageBand) ?: AgeBand.KIDS,
            allowNews = profile.allowNews,
            allowManga = profile.allowManga,
            allowMangaEntertainment = profile.allowMangaEntertainment,
            allowShorts = profile.allowShorts,
            autoplayNext = profile.autoplayNext,
            bedtimeEnabled = profile.bedtimeEnabled,
            bedtimeStartMinutes = profile.bedtimeStartMinutes,
            bedtimeEndMinutes = profile.bedtimeEndMinutes,
            bedtimeWeekendOffsetMinutes = profile.bedtimeWeekendOffsetMinutes,
            bedtimeSkipUntil = profile.bedtimeSkipUntil,
            limitEnabled = profile.dailyLimitMinutes != null,
            limitMinutes = profile.dailyLimitMinutes ?: DEFAULT_LIMIT
        )

        /**
         * Ein Schritt nach oben oder unten, an den Grenzen festgehalten. Ein gespeicherter Wert
         * ausserhalb des Rasters (aeltere Fassung, Startpaket) springt beim ersten Tippen auf
         * den naechsten gueltigen Wert, statt ausserhalb zu bleiben.
         */
        internal fun step(value: Int, direction: Int, size: Int, range: IntRange): Int {
            val next = when {
                direction > 0 -> (value / size + 1) * size
                direction < 0 -> ((value + size - 1) / size - 1) * size
                else -> value
            }
            return next.coerceIn(range)
        }
    }
}
