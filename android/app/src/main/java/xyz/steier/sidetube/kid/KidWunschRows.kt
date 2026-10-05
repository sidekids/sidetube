// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

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
internal object KidWunschRows {
    const val NEU = "Neu bei deinen Kanälen"
    const val WUENSCHE = "Wünsche"
    const val WUNSCH_ZEILE_SUCHE = "wish-topic"
    const val MEINE_WUENSCHE = "wishes"

    fun heuteText(heuteNoch: Int): String = when (heuteNoch) {
        0 -> "Heute keine Wünsche mehr – morgen wieder"
        1 -> "Heute noch 1 Wunsch"
        else -> "Heute noch $heuteNoch Wünsche"
    }

    /** Startseite: „Neu bei deinen Kanälen" (gesperrt) und „Meine Wünsche". */
    fun home(folgen: List<NeueFolge>, wuensche: List<WishEntity>, heuteNoch: Int): List<KidRow> {
        val offen = wuensche.count { WunschStatus.from(it.status)?.istOffen == true }
        return folgen.map(::folgenZeile) + KidRow(
            id = MEINE_WUENSCHE, title = "Meine Wünsche",
            subtitle = listOfNotNull(heuteText(heuteNoch), "$offen offen".takeIf { offen > 0 }).joinToString(" · "),
            action = KidAction.OpenWishes, section = WUENSCHE
        )
    }

    fun folgenZeile(folge: NeueFolge) = KidRow(
        id = "neu-" + folge.videoId, title = folge.title,
        subtitle = (if (folge.gewuenscht) "✓ Gewünscht · " else "🔒 Wünschen · ") + folge.channelTitle,
        thumbnailUrl = Vorschaubilder.fuerVideo(folge.videoId),
        action = KidAction.OpenNeueFolge(folge.videoId), section = NEU
    )

    /** In der Suche: der Weg zum Themenwunsch – ohne Treffer als einzige Zeile, sonst nach den Treffern. */
    fun suchZeile(anzeige: String) = KidRow(
        id = WUNSCH_ZEILE_SUCHE, title = "Wunsch an die Eltern",
        subtitle = if (anzeige.isBlank()) "Ein Thema wünschen" else "„${wortanfaenge(anzeige.trim())}“ wünschen",
        action = KidAction.OpenThemaWunsch, section = "Nicht gefunden?"
    )

    /** Themenwunsch: eine Zeile zum Abschicken (oder warum es nicht geht). */
    fun themaZeilen(text: String, moeglich: WunschMoeglich, heuteNoch: Int): List<KidRow> {
        val thema = WunschRegeln.thema(text)
        val zeile = when {
            moeglich == WunschMoeglich.GRENZE -> KidRow("send", heuteText(0), action = KidAction.Info)
            thema.isEmpty() -> KidRow("send", "Erst Buchstaben wählen", subtitle = "Oben mit ◀ ▶ und Mitte", action = KidAction.Info)
            moeglich == WunschMoeglich.SCHON_GEWUENSCHT ->
                KidRow("send", "„${wortanfaenge(thema)}“ ist schon gewünscht", subtitle = "Unter „Meine Wünsche“", action = KidAction.Info)
            else -> KidRow("send", "Wunsch schicken: „${wortanfaenge(thema)}“", subtitle = heuteText(heuteNoch), action = KidAction.SendWish)
        }
        return listOf(zeile)
    }

    /** Gesperrte neue Folge: Wünschen (oder warum nicht) und Zurück. */
    fun folgenZeilen(moeglich: WunschMoeglich, heuteNoch: Int): List<KidRow> = listOf(
        when (moeglich) {
            WunschMoeglich.JA -> KidRow("send", "Wünschen", subtitle = heuteText(heuteNoch), action = KidAction.SendWish)
            WunschMoeglich.SCHON_GEWUENSCHT -> KidRow("send", "✓ Schon gewünscht", subtitle = "Die Eltern sehen es", action = KidAction.Info)
            WunschMoeglich.GRENZE -> KidRow("send", heuteText(0), action = KidAction.Info)
        },
        KidRow("back", "Zurück", action = KidAction.GoBack)
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
        val neu = KidRow("wish-new", "Etwas wünschen", subtitle = "Ein Thema für die Eltern", action = KidAction.OpenThemaWunsch, section = kopf)
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
                detail = wunsch.parentReply?.takeIf { it.isNotBlank() && status != WunschStatus.OFFEN }?.let { "Eltern: „$it“" }
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
        WunschArt.MEHR_DAVON -> "Mehr davon"
        WunschArt.NEUE_FOLGE -> "Eine neue Folge"
        else -> titel(wunsch)
    }

    fun titel(wunsch: WishEntity): String = when (WunschArt.from(wunsch.kind)) {
        WunschArt.THEMA -> "„${wortanfaenge(wunsch.topic.orEmpty())}“"
        else -> wunsch.videoTitle.orEmpty()
    }

    private fun herkunft(wunsch: WishEntity): String = when (WunschArt.from(wunsch.kind)) {
        WunschArt.THEMA -> "Thema"
        WunschArt.MEHR_DAVON -> "Mehr davon"
        WunschArt.NEUE_FOLGE -> "Neue Folge"
        null -> wunsch.kind
    }

    /** Die vier Stände, wie das Kind sie liest (ADR 0001). */
    fun statusText(status: WunschStatus?, mitZiel: Boolean): String = when (status) {
        WunschStatus.OFFEN, null -> "Wartet auf die Eltern"
        WunschStatus.ERFUELLT -> if (mitZiel) "Freigegeben ▶" else "Erledigt"
        WunschStatus.ABGELEHNT -> "Nicht jetzt"
        WunschStatus.BESPRECHEN -> "Sprechen wir drüber"
    }

    /** Der Weg zum Inhalt: nur, was dieses Profil gerade sehen darf. */
    private fun ziel(wunsch: WishEntity, visible: List<WhitelistItemEntity>, kanalbilder: Map<String, String>): KidAction? {
        if (WunschStatus.from(wunsch.status) != WunschStatus.ERFUELLT) return null
        val inhalt = wunsch.resultContentId ?: wunsch.videoId?.takeIf { wunsch.kind == WunschArt.NEUE_FOLGE.id } ?: return null
        return visible.firstOrNull { it.contentId == inhalt }?.let { KidRows.item(it, kanalbilder).action }
    }

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
