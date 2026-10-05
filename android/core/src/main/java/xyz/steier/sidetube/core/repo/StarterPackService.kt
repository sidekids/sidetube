// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.repo

import xyz.steier.sidetube.core.curation.ContentBundle
import xyz.steier.sidetube.core.curation.ContentLibrary
import xyz.steier.sidetube.core.curation.ProfilePreset
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.model.ContentProvider
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.provider.ContentDraft
import xyz.steier.sidetube.core.provider.YouTubeThumbnails

data class StarterPack(
    val fileName: String,
    val id: String,
    val title: String,
    val note: String?,
    val videoCount: Int,
    val channelCount: Int,
    val hasProfilePreset: Boolean
)

data class StarterPackResult(val added: Int, val skipped: Int, val blocked: Int, val presetApplied: Boolean)

/**
 * Uebernimmt ein kuratiertes Startpaket in ein Profil. Die Eintraege gehen denselben Weg wie
 * jeder andere Zugang: Sie kommen zur Pruefung, nicht direkt zum Kind.
 */
class StarterPackService(
    private val bundle: ContentBundle,
    private val curation: CurationRepository,
    private val profiles: ProfileRepository
) {

    fun available(): List<StarterPack> = bundle.libraryNames().mapNotNull { name ->
        val library = bundle.library(name) ?: return@mapNotNull null
        StarterPack(
            fileName = name,
            id = library.id ?: name.removeSuffix(".json"),
            title = library.title ?: name.removeSuffix(".json"),
            note = library.note,
            videoCount = library.videos.size,
            channelCount = library.channels.size,
            hasProfilePreset = library.profilePreset != null
        )
    }

    suspend fun import(fileName: String, profile: KidProfileEntity, applyPreset: Boolean): StarterPackResult {
        val library = bundle.library(fileName) ?: return StarterPackResult(0, 0, 0, false)
        var added = 0
        var skipped = 0
        var blocked = 0

        for (channel in library.channels) {
            val draft = ContentDraft(
                type = WhitelistItemType.CHANNEL,
                contentId = channel.id,
                title = channel.title,
                thumbnailUrl = channel.thumbnailUrl.orEmpty(),
                channelTitle = channel.title,
                provider = ContentProvider.from(channel.provider),
                sourceChannelId = channel.id
            )
            when (add(draft, profile.id)) {
                Outcome.ADDED -> added++
                Outcome.SKIPPED -> skipped++
                Outcome.BLOCKED -> blocked++
            }
        }

        for (video in library.videos) {
            val draft = ContentDraft(
                type = WhitelistItemType.VIDEO,
                contentId = video.id,
                title = video.title,
                thumbnailUrl = if (ContentProvider.from(video.provider) == ContentProvider.YOUTUBE)
                    YouTubeThumbnails.url(video.id, 320) else "",
                channelTitle = video.channelTitle,
                provider = ContentProvider.from(video.provider),
                sourceChannelId = video.channelId,
                sourceUrl = video.sourceUrl,
                durationSeconds = video.durationSeconds
            )
            when (add(draft, profile.id)) {
                Outcome.ADDED -> added++
                Outcome.SKIPPED -> skipped++
                Outcome.BLOCKED -> blocked++
            }
        }

        val preset = library.profilePreset.takeIf { applyPreset }
        val applied = preset != null && apply(preset, profile)
        return StarterPackResult(added, skipped, blocked, applied)
    }

    private enum class Outcome { ADDED, SKIPPED, BLOCKED }

    private suspend fun add(draft: ContentDraft, profileId: String): Outcome = try {
        curation.discover(draft, profileId, actor = "Startpaket")
        Outcome.ADDED
    } catch (_: DiscoverError.Duplicate) {
        Outcome.SKIPPED
    } catch (_: DiscoverError.BlockedSource) {
        Outcome.BLOCKED
    }

    private suspend fun apply(preset: ProfilePreset, profile: KidProfileEntity): Boolean {
        val updated = profile.copy(
            ageBand = preset.ageBand ?: profile.ageBand,
            allowNews = preset.allowNews ?: profile.allowNews,
            allowManga = preset.allowManga ?: profile.allowManga,
            allowMangaEntertainment = preset.allowMangaEntertainment ?: profile.allowMangaEntertainment,
            allowShorts = preset.allowShorts ?: profile.allowShorts,
            autoplayNext = preset.autoplayNext ?: profile.autoplayNext,
            bedtimeEnabled = preset.bedtimeEnabled ?: profile.bedtimeEnabled,
            bedtimeStartMinutes = preset.bedtimeStartMinutes ?: profile.bedtimeStartMinutes,
            bedtimeEndMinutes = preset.bedtimeEndMinutes ?: profile.bedtimeEndMinutes,
            bedtimeWeekendOffsetMinutes = preset.bedtimeWeekendOffsetMinutes ?: profile.bedtimeWeekendOffsetMinutes
        )
        if (updated == profile) return false
        profiles.update(updated)
        return true
    }
}
