// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.curation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.steier.sidetube.core.db.CuratedSourceEntity

/** Eintrag aus `content/sources.json`. */
@Serializable
data class SourceDefinition(
    val channelId: String,
    val handle: String? = null,
    val title: String,
    val provider: String = "youtube",
    val trust: String,
    val isNews: Boolean = false,
    val defaultAgeMin: Int = 0,
    val defaultCategory: String? = null,
    val notes: String? = null,
    val verifiedAt: String? = null
) {
    fun toEntity() = CuratedSourceEntity(
        channelId = channelId, handle = handle, title = title, provider = provider,
        trust = trust, isNewsSource = isNews, defaultAgeMin = defaultAgeMin,
        defaultCategory = defaultCategory, notes = notes
    )
}

@Serializable
data class SourceRegistry(
    val version: Int = 1,
    val verifiedAt: String? = null,
    val note: String? = null,
    val sources: List<SourceDefinition> = emptyList()
)

/** Ein Startpaket aus `content/libraries/`. */
@Serializable
data class ContentLibrary(
    val version: Int = 1,
    val createdAt: String? = null,
    val id: String? = null,
    val title: String? = null,
    val note: String? = null,
    val profilePreset: ProfilePreset? = null,
    val channels: List<LibraryChannel> = emptyList(),
    val videos: List<LibraryVideo> = emptyList()
)

@Serializable
data class LibraryVideo(
    val id: String,
    val title: String,
    val channelId: String? = null,
    val channelTitle: String? = null,
    val provider: String? = null,
    val category: String? = null,
    val ageMin: Int = 0,
    val ageMax: Int? = null,
    val isNews: Boolean? = null,
    val newsStatus: String? = null,
    val isShort: Boolean? = null,
    val sourceUrl: String? = null,
    val durationSeconds: Int? = null,
    val note: String? = null
)

@Serializable
data class LibraryChannel(
    val id: String,
    val title: String,
    val provider: String? = null,
    val thumbnailUrl: String? = null,
    val category: String? = null,
    val ageMin: Int? = null,
    val note: String? = null
)

@Serializable
data class ProfilePreset(
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

/** Liefert die gemeinsamen Dateien; getrennt vom Auswerten, damit dieses ohne Android pruefbar bleibt. */
interface ContentSource {
    fun read(path: String): String?
    fun listLibraries(): List<String>
}

/**
 * Zugriff auf die gemeinsamen Kuratierungsdaten. Verbindlich ist `content/` im Repository;
 * die Kopie in den Assets ist ein Bauartefakt. Unbekannte Felder werden ueberlesen, damit die
 * iOS-Fassung das Format erweitern kann, ohne diese hier zu brechen.
 */
class ContentBundle(private val source: ContentSource) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun riskTerms(): RiskTerms =
        source.read("content/risk-terms.json")?.let { json.decodeFromString<RiskTerms>(it) } ?: RiskTerms()

    fun sources(): List<SourceDefinition> =
        source.read("content/sources.json")?.let { json.decodeFromString<SourceRegistry>(it).sources } ?: emptyList()

    fun libraryNames(): List<String> = source.listLibraries()

    fun library(name: String): ContentLibrary? =
        source.read("content/libraries/$name")?.let {
            runCatching { json.decodeFromString<ContentLibrary>(it) }.getOrNull()
        }
}
