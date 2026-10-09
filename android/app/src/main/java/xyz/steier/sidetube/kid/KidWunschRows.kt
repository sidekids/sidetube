// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import xyz.steier.sidetube.R
import xyz.steier.sidetube.Texte

import xyz.steier.sidetube.core.curation.NeueFolge
import xyz.steier.sidetube.core.curation.WunschArt
import xyz.steier.sidetube.core.curation.WunschRegeln
import xyz.steier.sidetube.core.curation.WunschStatus
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.db.WishEntity
import xyz.steier.sidetube.core.input.RadTaste
import xyz.steier.sidetube.core.input.Radsuche
import xyz.steier.sidetube.core.input.Radtasten

/** Ob ein Wunsch gerade geht – für „Wünschen"-Zeilen, Endkarte und Player-Menü. */
enum class WunschMoeglich { JA, SCHON_GEWUENSCHT, GRENZE }

/**
 * Die Zeilen der Wünsche (ADR 0001). Rein und ohne Datenbank wie [KidRows]: Was abspielbar ist,
 * kommt aus `visible`; eine neue Folge ist nie abspielbar, nur wünschbar.
 */
internal class KidWunschRows(private val texte: Texte) {
    val NEU = texte.get(R.string.wunsch_abschnitt_neu)
    val WUENSCHE = texte.get(R.string.wunsch_abschnitt_wuensche)

    companion object {
        const val WUNSCH_ZEILE_SUCHE = "wish-topic"
        const val MEINE_WUENSCHE = "wishes"

        fun wortanfaenge(text: String): String =
            text.split(' ').joinToString(" ") { wort -> wort.replaceFirstChar { if (it == 'ß') it else it.titlecaseChar() } }

        /**
         * Das freie Rad des Themenwunsches: alle Buchstaben, dazu Ä Ö Ü ß, Lücke und Löschen. Anders
         * als die Suche bietet es auch an, wozu es nichts gibt – das ist ja der Sinn des Wunsches.
         */
        fun freiesRad(vorher: RadZustand): RadZustand {
            val text = vorher.getippt
            val tasten = buildList {
                ALPHABET.forEach { add(RadTaste.Buchstabe(Radsuche.Zeichen(it, it.toString()))) }
                if (text.isNotEmpty() && !text.endsWith(' ') && text.length < WunschRegeln.THEMA_MAX) add(RadTaste.Luecke)
                if (text.isNotEmpty()) add(RadTaste.Loeschen)
            }.let { if (text.length >= WunschRegeln.THEMA_MAX) it.filterNot { t -> t is RadTaste.Buchstabe } else it }
            val fokus = Radtasten.fokusNach(vorher.tasten.getOrNull(vorher.fokus), tasten)
            return vorher.copy(tasten = tasten, fokus = fokus, anzeige = text, t9 = false)
        }

        private val ALPHABET = ('a'..'z') + listOf('ä', 'ö', 'ü', 'ß')
    }

    fun heuteText(heuteNoch: Int): String =
        if (heuteNoch == 0) texte.get(R.string.wunsch_heute_keine) else texte.plural(R.plurals.wunsch_heute_noch, heuteNoch)

    /** Startseite: „Neu bei deinen Kanälen" (gesperrt) und „Meine Wünsche". */
    fun home(folgen: List<NeueFolge>, wuensche: List<WishEntity>, heuteNoch: Int): List<KidRow> {
        val offen = wuensche.count { WunschStatus.from(it.status)?.istOffen == true }
        return folgen.map(::folgenZeile) + KidRow(
            id = MEINE_WUENSCHE, title = texte.get(R.string.kid_meine_wuensche),
            subtitle = listOfNotNull(heuteText(heuteNoch), texte.get(R.string.wunsch_offen, offen).takeIf { offen > 0 }).joinToString(" · "),
            action = KidAction.OpenWishes, section = WUENSCHE
        )
    }

    fun folgenZeile(folge: NeueFolge) = KidRow(
        id = "neu-" + folge.videoId, title = folge.title,
        subtitle = texte.get(if (folge.gewuenscht) R.string.wunsch_folge_gewuenscht else R.string.wunsch_folge_wuenschen, folge.channelTitle),
        thumbnailUrl = Vorschaubilder.fuerVideo(folge.videoId),
        action = KidAction.OpenNeueFolge(folge.videoId), section = NEU
    )

    /** In der Suche: der Weg zum Themenwunsch – ohne Treffer als einzige Zeile, sonst nach den Treffern. */
    fun suchZeile(anzeige: String) = KidRow(
        id = WUNSCH_ZEILE_SUCHE, title = texte.get(R.string.kid_wunsch_an_die_eltern),
        subtitle = if (anzeige.isBlank()) texte.get(R.string.wunsch_thema_wuenschen) else texte.get(R.string.wunsch_text_wuenschen, wortanfaenge(anzeige.trim())),
        action = KidAction.OpenThemaWunsch, section = texte.get(R.string.wunsch_abschnitt_nicht_gefunden)
    )

    /** Themenwunsch: eine Zeile zum Abschicken (oder warum es nicht geht). */
    fun themaZeilen(text: String, moeglich: WunschMoeglich, heuteNoch: Int): List<KidRow> {
        val thema = WunschRegeln.thema(text)
        val zeile = when {
            moeglich == WunschMoeglich.GRENZE -> KidRow("send", heuteText(0), action = KidAction.Info)
            thema.isEmpty() -> KidRow("send", texte.get(R.string.wunsch_erst_buchstaben), subtitle = texte.get(R.string.wunsch_oben_mit_rad), action = KidAction.Info)
            moeglich == WunschMoeglich.SCHON_GEWUENSCHT ->
                KidRow("send", texte.get(R.string.wunsch_schon_gewuenscht_text, wortanfaenge(thema)), subtitle = texte.get(R.string.wunsch_unter_meine_wuensche), action = KidAction.Info)
            else -> KidRow("send", texte.get(R.string.wunsch_schicken_text, wortanfaenge(thema)), subtitle = heuteText(heuteNoch), action = KidAction.SendWish)
        }
        return listOf(zeile)
    }

    /** Gesperrte neue Folge: Wünschen (oder warum nicht) und Zurück. */
    fun folgenZeilen(moeglich: WunschMoeglich, heuteNoch: Int): List<KidRow> = listOf(
        when (moeglich) {
            WunschMoeglich.JA -> KidRow("send", texte.get(R.string.wunsch_wuenschen), subtitle = heuteText(heuteNoch), action = KidAction.SendWish)
            WunschMoeglich.SCHON_GEWUENSCHT -> KidRow("send", texte.get(R.string.wunsch_schon_gewuenscht), subtitle = texte.get(R.string.wunsch_eltern_sehen_es), action = KidAction.Info)
            WunschMoeglich.GRENZE -> KidRow("send", heuteText(0), action = KidAction.Info)
        },
        KidRow("back", texte.get(R.string.wunsch_zurueck), action = KidAction.GoBack)
    )

    /**
     * „Meine Wünsche": zuerst „Etwas wünschen", dann die Wünsche, neueste zuerst. Die Überschrift
     * sagt, wie viele heute noch gehen. Ein erfüllter Wunsch führt zum Inhalt, wenn er sichtbar ist.
     * Bild und Titel eines fremden Videos nur, solange es nicht abgelehnt ist und seine Quelle nicht in
     * [gesperrteKanaele] steht (gesperrt oder „nur für Eltern“) – wie iOS `WishDisplay`.
     */
    fun liste(
        wuensche: List<WishEntity>, visible: List<WhitelistItemEntity>, kanalbilder: Map<String, String>, heuteNoch: Int,
        gesperrteKanaele: Set<String> = emptySet()
    ): List<KidRow> {
        val kopf = heuteText(heuteNoch)
        val neu = KidRow("wish-new", texte.get(R.string.wunsch_etwas_wuenschen), subtitle = texte.get(R.string.wunsch_thema_fuer_eltern), action = KidAction.OpenThemaWunsch, section = kopf)
        return listOf(neu) + wuensche.map { wunsch ->
            val ziel = ziel(wunsch, visible, kanalbilder)
            val status = WunschStatus.from(wunsch.status)
            val zeigtVideo = zeigtFremdesVideo(wunsch, gesperrteKanaele)
            KidRow(
                id = "wish-" + wunsch.id,
                title = if (zeigtVideo) titel(wunsch) else neutralerTitel(wunsch),
                subtitle = statusText(status, ziel != null) + " · " + herkunft(wunsch),
                thumbnailUrl = if (zeigtVideo) wunsch.videoId?.let(Vorschaubilder::fuerVideo) else null,
                action = KidAction.Wish(wunsch.id, ziel),
                section = kopf,
                detail = wunsch.parentReply?.takeIf { it.isNotBlank() && status != WunschStatus.OFFEN }?.let { texte.get(R.string.wunsch_eltern_antwort, it) }
            )
        }
    }

    /** Das Thema ist das eigene Wort des Kindes und bleibt; ein fremdes Video nur bis Ablehnung oder Sperre. */
    fun zeigtFremdesVideo(wunsch: WishEntity, gesperrteKanaele: Set<String>): Boolean {
        if (WunschArt.from(wunsch.kind) == WunschArt.THEMA) return true
        if (WunschStatus.from(wunsch.status) == WunschStatus.ABGELEHNT) return false
        return wunsch.channelId == null || wunsch.channelId !in gesperrteKanaele
    }

    private fun neutralerTitel(wunsch: WishEntity): String = when (WunschArt.from(wunsch.kind)) {
        WunschArt.MEHR_DAVON -> texte.get(R.string.wunsch_art_mehr_davon)
        WunschArt.NEUE_FOLGE -> texte.get(R.string.wunsch_art_eine_neue_folge)
        else -> titel(wunsch)
    }

    fun titel(wunsch: WishEntity): String = when (WunschArt.from(wunsch.kind)) {
        WunschArt.THEMA -> "„${wortanfaenge(wunsch.topic.orEmpty())}“"
        else -> wunsch.videoTitle.orEmpty()
    }

    private fun herkunft(wunsch: WishEntity): String = when (WunschArt.from(wunsch.kind)) {
        WunschArt.THEMA -> texte.get(R.string.wunsch_art_thema)
        WunschArt.MEHR_DAVON -> texte.get(R.string.wunsch_art_mehr_davon)
        WunschArt.NEUE_FOLGE -> texte.get(R.string.wunsch_art_neue_folge)
        null -> wunsch.kind
    }

    /** Die vier Stände, wie das Kind sie liest (ADR 0001). */
    fun statusText(status: WunschStatus?, mitZiel: Boolean): String = when (status) {
        WunschStatus.OFFEN, null -> texte.get(R.string.wunsch_status_wartet)
        WunschStatus.ERFUELLT -> texte.get(if (mitZiel) R.string.wunsch_status_freigegeben else R.string.wunsch_status_erledigt)
        WunschStatus.ABGELEHNT -> texte.get(R.string.wunsch_status_nicht_jetzt)
        WunschStatus.BESPRECHEN -> texte.get(R.string.wunsch_status_besprechen)
    }

    /** Der Weg zum Inhalt: nur, was dieses Profil gerade sehen darf. */
    private fun ziel(wunsch: WishEntity, visible: List<WhitelistItemEntity>, kanalbilder: Map<String, String>): KidAction? {
        if (WunschStatus.from(wunsch.status) != WunschStatus.ERFUELLT) return null
        val inhalt = wunsch.resultContentId ?: wunsch.videoId?.takeIf { wunsch.kind == WunschArt.NEUE_FOLGE.id } ?: return null
        return visible.firstOrNull { it.contentId == inhalt }?.let { KidRows(texte).item(it, kanalbilder).action }
    }

}
