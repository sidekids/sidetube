// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.curation

import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.model.AgeBand
import xyz.steier.sidetube.core.model.ApprovalStatus
import xyz.steier.sidetube.core.model.ContentCategory
import xyz.steier.sidetube.core.model.ContentProvider
import xyz.steier.sidetube.core.model.NewsStatus
import xyz.steier.sidetube.core.model.SourceTrust
import xyz.steier.sidetube.core.model.WhitelistItemType

/** Warum ein Inhalt sichtbar ist oder nicht – der Grund gehoert in die Eltern-Ansicht. */
sealed interface Visibility {
    data object Visible : Visibility
    data class Hidden(val reason: String) : Visibility

    val isVisible: Boolean get() = this is Visible
}

/**
 * Die eine Stelle, die entscheidet, was ein Kind sieht.
 *
 * Bewusst als reine Funktion ohne Datenbank und ohne Oberflaeche: Diese Regel traegt die
 * Sicherheit der App, sie muss vollstaendig pruefbar sein und darf nicht in einer Ansicht
 * nachgebaut werden.
 */
object ContentPolicy {

    /** Apply source/profile rules to a transient channel result; never persist an approval. */
    fun canBrowseCandidate(
        candidate: WhitelistItemEntity,
        profile: KidProfileEntity,
        source: CuratedSourceEntity?,
        priorDecision: WhitelistItemEntity?,
        risk: RiskAssessment
    ): Boolean {
        if (!allowsChannelBrowsing(source)) return false
        if (priorDecision != null) return isVisible(priorDecision, profile, source)
        if (risk.isHardBlocked || risk.requiresReview) return false
        return isVisible(candidate.copy(
            approvalStatus = "approved", ageMin = source?.defaultAgeMin ?: 0,
            category = source?.defaultCategory, isNews = source?.isNewsSource == true,
            isLive = risk.isLive, isShort = risk.isShort
        ), profile, source)
    }

    /**
     * Eine neue Folge fuer „Neu bei deinen Kanaelen" (ADR 0001): Das Kind sieht Bild und Titel,
     * **abspielen kann es sie nicht** – sie ist nur wuenschbar. Deshalb nur bei der Stufe
     * „Vertrauenswuerdige Reihe" (bei einer Kinderquelle ist der Kanal ohnehin stoeberbar, bei
     * allen anderen Stufen sieht das Kind nichts Ungepruefte), nie bei einer gesperrten Quelle,
     * nie mit einer vorhandenen Elternentscheidung, nie mit Risikotreffer, Short oder Livestream, nie
     * aus einer Nachrichtenquelle. Alter und Kategorie der Quelle gelten wie bei einer Freigabe.
     */
    fun canShowLockedNewEpisode(
        candidate: WhitelistItemEntity,
        profile: KidProfileEntity,
        source: CuratedSourceEntity?,
        priorDecision: WhitelistItemEntity?,
        risk: RiskAssessment
    ): Boolean {
        if (source == null || SourceTrust.from(source.trust) != SourceTrust.TRUSTED_SERIES) return false
        // Nachrichten zeigen schon im Titel Belastendes; als ungepruefte Vorschau nie (wie iOS).
        if (source.isNewsSource) return false
        if (priorDecision != null) return false
        if (risk.isHardBlocked || risk.requiresReview || candidate.isShort) return false
        return isVisible(candidate.copy(
            approvalStatus = ApprovalStatus.APPROVED.id, ageMin = source.defaultAgeMin,
            category = source.defaultCategory, isNews = false,
            isLive = risk.isLive, isShort = false
        ), profile, source)
    }

    /**
     * Ein Video aus einer freigegebenen Playlist. Die Freigabe der Playlist traegt ihre Videos –
     * wie auf iOS (`PlaylistModel`) –, aber nicht blind, denn der Besitzer kann jederzeit Videos
     * hinzufuegen: Eine eigene Elternentscheidung zum Video geht vor, Risikotreffer, Shorts und
     * Livestreams bleiben draussen (auch wenn nur der Feed sie als Short bzw. angekuendigt kennt:
     * [candidate] traegt dann `isShort`/`isLive`), Alter, Kategorie und Nachrichtenregeln der
     * Playlist gelten fuer jedes Video, und eine gesperrte Quelle sperrt – die der Playlist wie die
     * des Videos.
     *
     * **Eigene Freigabe noetig** (ADR 0002): wenn der Kanal des Videos die Stufe „Nur einzeln
     * gepruefte Videos" hat, und wenn der Kanal unbekannt ist ([candidate] ohne `sourceChannelId`,
     * etwa ein Zwischenspeicher von vor Schema 3) – dann laesst sich weder Sperre noch Stufe pruefen.
     * Ein Kanal ohne Quelle (nie eingestuft) wird von der Playlist-Freigabe getragen.
     * Wie bei Kanaelen wird daraus nie eine gespeicherte Freigabe.
     */
    fun canPlayFromPlaylist(
        candidate: WhitelistItemEntity,
        profile: KidProfileEntity,
        playlist: WhitelistItemEntity,
        playlistSource: CuratedSourceEntity?,
        videoSource: CuratedSourceEntity?,
        priorDecision: WhitelistItemEntity?,
        risk: RiskAssessment
    ): Boolean {
        if (playlist.type != WhitelistItemType.PLAYLIST.name) return false
        if (!isVisible(playlist, profile, playlistSource)) return false
        if (priorDecision != null) {
            return priorDecision.type == WhitelistItemType.VIDEO.name &&
                isVisible(priorDecision, profile, videoSource ?: playlistSource)
        }
        if (candidate.sourceChannelId.isNullOrBlank()) return false
        if (SourceTrust.from(videoSource?.trust) == SourceTrust.PER_VIDEO_REVIEW) return false
        if (risk.isHardBlocked || risk.requiresReview) return false
        val inherited = candidate.copy(
            approvalStatus = ApprovalStatus.APPROVED.id, ageMin = playlist.ageMin, ageMax = playlist.ageMax,
            category = playlist.category, isNews = playlist.isNews, newsStatus = playlist.newsStatus,
            isLive = risk.isLive || candidate.isLive, isShort = risk.isShort || candidate.isShort
        )
        if (!isVisible(inherited, profile, playlistSource)) return false
        return videoSource == null || isVisible(inherited, profile, videoSource)
    }

    fun evaluate(
        item: WhitelistItemEntity,
        profile: KidProfileEntity,
        source: CuratedSourceEntity?
    ): Visibility {
        val provider = ContentProvider.from(item.provider)
        if (provider == ContentProvider.PEERTUBE && source == null) {
            return Visibility.Hidden("PeerTube-Instanz nicht freigegeben")
        }
        when (SourceTrust.from(source?.trust)) {
            SourceTrust.BLOCKED -> return Visibility.Hidden("Quelle gesperrt")
            SourceTrust.PARENT_ONLY -> return Visibility.Hidden("Quelle nur für Eltern")
            else -> Unit
        }
        if (ApprovalStatus.from(item.approvalStatus) != ApprovalStatus.APPROVED) {
            return Visibility.Hidden("nicht freigegeben")
        }

        val age = AgeBand.from(profile.ageBand)?.minimumAge ?: AgeBand.KIDS.minimumAge
        if (item.ageMin > age) return Visibility.Hidden("Mindestalter ${item.ageMin}")
        item.ageMax?.let { if (age > it) return Visibility.Hidden("Höchstalter $it") }
        if (item.isLive) return Visibility.Hidden("Livestream")
        if (item.isShort && !profile.allowShorts) return Visibility.Hidden("Short")

        ContentCategory.from(item.category)?.let { category ->
            if (category.minimumAge > age) return Visibility.Hidden("Kategorie ab ${category.minimumAge}")
            if (category == ContentCategory.MANGA_DRAWING && !profile.allowManga) {
                return Visibility.Hidden("Manga deaktiviert")
            }
            if (category == ContentCategory.ANIME_MANGA && !(profile.allowManga && profile.allowMangaEntertainment)) {
                return Visibility.Hidden("Anime & Manga deaktiviert")
            }
            if (category == ContentCategory.NEWS && !profile.allowNews) {
                return Visibility.Hidden("Nachrichten deaktiviert")
            }
        }

        if (item.isNews) {
            if (!profile.allowNews) return Visibility.Hidden("Nachrichten deaktiviert")
            when (NewsStatus.from(item.newsStatus)) {
                NewsStatus.SENSITIVE, NewsStatus.PARENT_REVIEW ->
                    return Visibility.Hidden("Nachricht muss geprüft werden")
                else -> Unit
            }
        }
        return Visibility.Visible
    }

    fun isVisible(item: WhitelistItemEntity, profile: KidProfileEntity, source: CuratedSourceEntity?): Boolean =
        evaluate(item, profile, source).isVisible

    /** Belastende Nachrichten stehen nicht auf der Startseite, auch wenn sie freigegeben sind. */
    fun isHomeHighlightable(item: WhitelistItemEntity): Boolean =
        !(item.isNews && NewsStatus.from(item.newsStatus) != NewsStatus.SAFE)

    /** Ob im ganzen Kanal gestoebert werden darf – nur bei einer vertrauenswuerdigen Kinderquelle. */
    fun allowsChannelBrowsing(source: CuratedSourceEntity?): Boolean =
        SourceTrust.from(source?.trust)?.allowsChannelBrowsing == true
}
