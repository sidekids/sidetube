// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.model

/**
 * Begriffe der Kuratierung. Die Bezeichner entsprechen den gemeinsamen Daten unter `content/`
 * und der iOS-Fassung, damit ein Startpaket auf beiden Plattformen dasselbe bedeutet.
 */
enum class AgeBand(val id: String, val minimumAge: Int) {
    PRESCHOOL("preschool", 3),
    EARLY("early", 6),
    KIDS("kids", 9),
    TWEEN("tween", 12);

    companion object {
        fun from(id: String?): AgeBand? = entries.firstOrNull { it.id == id }
    }
}

enum class ContentCategory(val id: String, val minimumAge: Int) {
    KNOWLEDGE("knowledge", 0),
    MEDIA_LITERACY("mediaLiteracy", 7),
    NEWS("news", 8),
    STORY("story", 0),
    MUSIC("music", 0),
    CRAFT("craft", 0),
    MANGA_DRAWING("mangaDrawing", 8),
    ANIME_MANGA("animeManga", 12),
    SPORT("sport", 0);

    companion object {
        fun from(id: String?): ContentCategory? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Wie weit einer Quelle vertraut wird. Nur [TRUSTED_CHILD_SOURCE] erlaubt das Stoebern im
 * ganzen Kanal; alle anderen Stufen zeigen ausschliesslich einzeln freigegebene Videos.
 */
enum class SourceTrust(val id: String) {
    TRUSTED_CHILD_SOURCE("trustedChildSource"),
    TRUSTED_SERIES("trustedSeries"),
    PER_VIDEO_REVIEW("perVideoReview"),
    PARENT_ONLY("parentOnly"),
    BLOCKED("blocked");

    val allowsChannelBrowsing: Boolean get() = this == TRUSTED_CHILD_SOURCE

    companion object {
        fun from(id: String?): SourceTrust? = entries.firstOrNull { it.id == id }
    }
}

/** Neue Inhalte sind nie freigegeben; die Eltern entscheiden. */
enum class ApprovalStatus(val id: String) {
    DISCOVERED("discovered"),
    REVIEW_REQUIRED("reviewRequired"),
    APPROVED("approved"),
    REJECTED("rejected"),
    EXPIRED_REVIEW("expiredReview");

    val isVisibleToChild: Boolean get() = this == APPROVED

    companion object {
        fun from(id: String?): ApprovalStatus? = entries.firstOrNull { it.id == id }
    }
}

enum class NewsStatus(val id: String) {
    SAFE("safe"), PARENT_REVIEW("parentReview"), SENSITIVE("sensitive");

    companion object {
        fun from(id: String?): NewsStatus? = entries.firstOrNull { it.id == id }
    }
}

enum class SensitiveTopic(val id: String) {
    SEXUAL("sexual"), VIOLENCE("violence"), DEATH("death"), HORROR("horror"), WAR("war"),
    DISASTER("disaster"), CRIME("crime"), FEAR("fear"), POLITICS("politics"),
    ADULT_MEDIA("adultMedia"), COARSE_LANGUAGE("coarseLanguage"), ADVERTISING("advertising");

    companion object {
        fun from(id: String?): SensitiveTopic? = entries.firstOrNull { it.id == id }
    }
}

enum class ContentProvider(val id: String) {
    YOUTUBE("youtube"), PEERTUBE("peertube");

    companion object {
        fun from(id: String?): ContentProvider = entries.firstOrNull { it.id == id } ?: YOUTUBE
    }
}

enum class WhitelistItemType { CHANNEL, VIDEO, PLAYLIST }
