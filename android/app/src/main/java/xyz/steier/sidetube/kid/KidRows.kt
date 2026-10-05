// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import xyz.steier.sidetube.core.curation.ContentPolicy
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.model.WhitelistItemType

/**
 * Eine Zeile im Kindermodus – bewusst schlicht: Bild, Titel, was beim Auswählen geschieht.
 * [section] ist die Überschrift, unter der die Zeile steht; die Oberfläche zeigt sie vor der
 * ersten Zeile eines Abschnitts. Überschriften sind keine Fokusstellen – der Fokus springt
 * von der letzten Zeile eines Abschnitts direkt in die erste des nächsten.
 */
data class KidRow(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val isChannel: Boolean = false,
    val action: KidAction,
    val section: String? = null,
    /** Längerer Text unter dem Untertitel (Antwort der Eltern unter „Meine Wünsche"). */
    val detail: String? = null
)

sealed interface KidAction {
    data class OpenChannel(val channelId: String, val title: String) : KidAction
    data class OpenPlaylist(val playlistId: String, val title: String) : KidAction
    data class Play(val videoId: String, val title: String) : KidAction
    /** „Alle Videos": die Mediathek mit Umschalter. */
    data object OpenLibrary : KidAction
    /** Der Umschalter der Mediathek; Auswählen schaltet zum nächsten Bereich weiter. */
    data object NextSegment : KidAction

    // Wünsche (ADR 0001)
    /** „Meine Wünsche". */
    data object OpenWishes : KidAction
    /** Themenwunsch mit dem freien Buchstabenrad. */
    data object OpenThemaWunsch : KidAction
    /** Eine gesperrte neue Folge ansehen und wünschen – nie abspielen. */
    data class OpenNeueFolge(val videoId: String) : KidAction
    /** Auf einem Wunsch-Bildschirm: abschicken. */
    data object SendWish : KidAction
    /** Eine Ebene zurück, als Zeile zum Antippen und Auswählen. */
    data object GoBack : KidAction
    /** Nur Hinweis, Auswählen bewirkt nichts (z. B. Tagesgrenze erreicht). */
    data object Info : KidAction
    /** Ein Wunsch unter „Meine Wünsche"; [ziel] ist der Weg zum freigegebenen Inhalt, sonst `null`. */
    data class Wish(val wishId: String, val ziel: KidAction?) : KidAction
}

/** Zeilen der Wünsche tragen den Fokus nach SideUI (nie gelb); siehe [SideFokus]. */
internal val KidAction.istWunschZeile: Boolean
    get() = this is KidAction.OpenWishes || this is KidAction.OpenThemaWunsch || this is KidAction.OpenNeueFolge ||
        this is KidAction.SendWish || this is KidAction.GoBack || this is KidAction.Info || this is KidAction.Wish

/** Wo das Kind gerade ist. */
sealed interface KidScreen {
    data object Home : KidScreen
    data class Channel(val channelId: String, val title: String) : KidScreen
    data class Playlist(val playlistId: String, val title: String) : KidScreen
    data class Library(val segment: LibrarySegment) : KidScreen
    data object Search : KidScreen
    /** „Meine Wünsche". */
    data object Wishes : KidScreen
    /** Themenwunsch: freies Buchstabenrad, darunter „Wunsch schicken". */
    data object ThemaWunsch : KidScreen
    /** Eine gesperrte neue Folge mit „Wünschen". */
    data class NeueFolgeAnsicht(val folge: xyz.steier.sidetube.core.curation.NeueFolge) : KidScreen
}

/** Bildschirme mit Buchstabenrad: Die Tasten wählen dort zuerst Buchstaben. */
internal val KidScreen.hatRad: Boolean get() = this is KidScreen.Search || this is KidScreen.ThemaWunsch

/** Die drei Bereiche der Mediathek – dieselben Wörter wie auf iOS (`LibraryScreen`). */
enum class LibrarySegment(val title: String, val type: WhitelistItemType) {
    CHANNELS("Kanäle", WhitelistItemType.CHANNEL),
    VIDEOS("Videos", WhitelistItemType.VIDEO),
    PLAYLISTS("Sendungen", WhitelistItemType.PLAYLIST);

    /** Mit einer Taste im Kreis: Kanäle → Videos → Sendungen → Kanäle. */
    fun next(): LibrarySegment = entries[(ordinal + 1) % entries.size]
}

/**
 * Baut die Zeilen von Startseite und Mediathek aus dem, was ohnehin sichtbar ist. Rein und ohne
 * Datenbank: Welche Einträge sichtbar sind, hat [ContentPolicy] vorher entschieden.
 */
internal object KidRows {
    const val RECENT = "Zuletzt geschaut"
    const val CHANNELS = "Kanäle"
    const val PLAYLISTS = "Sendungen"
    const val VIDEOS = "Videos"

    /** Auf dem kleinen Schirm nur die neuesten Videos; alle stehen in der Mediathek. */
    const val HOME_VIDEO_LIMIT = 4
    const val RECENT_LIMIT = 3
    const val LIBRARY_ROW_ID = "library"
    const val SEGMENTS_ROW_ID = "segments"

    /**
     * Startseite: Zuletzt geschaut · Kanäle · Sendungen · Videos · „Alle Videos". Leere
     * Abschnitte entfallen. Belastende Nachrichten stehen nie auf der Startseite, nur in der
     * Mediathek (wie iOS `isHomeHighlightable`).
     */
    fun home(visible: List<WhitelistItemEntity>, recent: List<KidRow>, kanalbilder: Map<String, String>): List<KidRow> {
        val channels = visible.filter { it.type == WhitelistItemType.CHANNEL.name }
        val playlists = visible.filter { it.type == WhitelistItemType.PLAYLIST.name }
        val videos = visible.filter { it.type == WhitelistItemType.VIDEO.name && ContentPolicy.isHomeHighlightable(it) }
            .take(HOME_VIDEO_LIMIT)
        val rows = mutableListOf<KidRow>()
        rows += recent.map { it.copy(section = RECENT) }
        rows += channels.map { item(it, kanalbilder, CHANNELS) }
        rows += playlists.map { item(it, kanalbilder, PLAYLISTS) }
        rows += videos.map { item(it, kanalbilder, VIDEOS) }
        if (visible.isNotEmpty()) rows += KidRow(
            id = LIBRARY_ROW_ID, title = "Alle Videos", subtitle = "Kanäle · Videos · Sendungen",
            action = KidAction.OpenLibrary, section = VIDEOS
        )
        return rows
    }

    /** Mediathek: zuerst der Umschalter, dann alles Sichtbare des gewählten Bereichs. */
    fun library(visible: List<WhitelistItemEntity>, segment: LibrarySegment, kanalbilder: Map<String, String>): List<KidRow> =
        listOf(KidRow(id = SEGMENTS_ROW_ID, title = segment.title, action = KidAction.NextSegment)) +
            visible.filter { it.type == segment.type.name }.map { item(it, kanalbilder) }

    fun item(item: WhitelistItemEntity, kanalbilder: Map<String, String>, section: String? = null): KidRow {
        val type = WhitelistItemType.entries.firstOrNull { it.name == item.type } ?: WhitelistItemType.VIDEO
        return KidRow(
            id = item.id,
            title = item.title,
            subtitle = item.channelTitle?.takeIf { it != item.title }
                ?: if (type == WhitelistItemType.PLAYLIST) "Sendung" else null,
            thumbnailUrl = Vorschaubilder.adresse(item) ?: kanalbilder[item.contentId],
            isChannel = type == WhitelistItemType.CHANNEL,
            action = when (type) {
                WhitelistItemType.CHANNEL -> KidAction.OpenChannel(item.contentId, item.title)
                WhitelistItemType.PLAYLIST -> KidAction.OpenPlaylist(item.contentId, item.title)
                WhitelistItemType.VIDEO -> KidAction.Play(item.contentId, item.title)
            },
            section = section
        )
    }

    /**
     * Videos aus Kanal- und Playlist-Feeds; die Bildadresse dort zeigt auf einen Server, den der
     * Lader nicht anfragt. Aus der Kennung gebildet trägt auch der Zwischenspeicher.
     */
    fun video(videoId: String, title: String, channel: String?, idPrefix: String = "") = KidRow(
        id = idPrefix + videoId, title = title, subtitle = channel?.takeIf { it.isNotBlank() },
        thumbnailUrl = Vorschaubilder.fuerVideo(videoId), action = KidAction.Play(videoId, title)
    )
}
