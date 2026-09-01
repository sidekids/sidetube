package io.github.degipe.youtubewhitelist.core.export.content

import kotlinx.serialization.Serializable

/**
 * Aufbau der gemeinsamen Kuratierungsdaten aus dem Ordner `content/`.
 * Verbindlich sind die Dateien im iOS-Repo; hierher kommen sie über `scripts/sync-content.sh android`
 * nach `assets/content` und sind deshalb nicht versioniert.
 */
@Serializable
data class ContentLibrary(
    val version: Int = 1,
    val createdAt: String? = null,
    val id: String? = null,
    val title: String? = null,
    val note: String? = null,
    val profilePreset: ContentProfilePreset? = null,
    val videos: List<ContentVideo> = emptyList()
)

@Serializable
data class ContentVideo(
    val id: String,
    val title: String,
    val channelId: String? = null,
    val channelTitle: String? = null,
    val category: String? = null,
    val ageMin: Int? = null,
    val note: String? = null
)

/** Vorschlag für das Profil; die Ruhezeit trägt dieselben Feldnamen wie die Datenbank. */
@Serializable
data class ContentProfilePreset(
    val ageBand: String? = null,
    val allowNews: Boolean? = null,
    val allowManga: Boolean? = null,
    val allowMangaEntertainment: Boolean? = null,
    val allowShorts: Boolean? = null,
    val autoplayNext: Boolean? = null,
    val bedtimeEnabled: Boolean? = null,
    val bedtimeStartMinutes: Int? = null,
    val bedtimeEndMinutes: Int? = null,
    val bedtimeWeekendOffsetMinutes: Int? = null
)
