package io.github.degipe.youtubewhitelist.core.export.content

import io.github.degipe.youtubewhitelist.core.common.R
import io.github.degipe.youtubewhitelist.core.common.model.WhitelistItemType
import io.github.degipe.youtubewhitelist.core.common.result.AppResult
import io.github.degipe.youtubewhitelist.core.common.ui.UiMessage
import io.github.degipe.youtubewhitelist.core.database.dao.KidProfileDao
import io.github.degipe.youtubewhitelist.core.database.dao.WhitelistItemDao
import io.github.degipe.youtubewhitelist.core.database.entity.WhitelistItemEntity
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StarterPackServiceImpl @Inject constructor(
    private val source: StarterPackSource,
    private val whitelistItemDao: WhitelistItemDao,
    private val kidProfileDao: KidProfileDao
) : StarterPackService {

    // Die Kuratierungsdaten tragen mehr Felder, als die Android-Fassung heute auswertet.
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun availablePacks(): List<StarterPack> =
        source.listLibraries().mapNotNull { fileName ->
            val library = parse(fileName) ?: return@mapNotNull null
            StarterPack(
                fileName = fileName,
                id = library.id ?: fileName.removeSuffix(".json"),
                title = library.title ?: fileName.removeSuffix(".json"),
                note = library.note,
                videoCount = library.videos.size,
                hasProfilePreset = library.profilePreset != null
            )
        }

    override suspend fun import(
        fileName: String,
        kidProfileId: String,
        applyPreset: Boolean
    ): AppResult<StarterPackImportResult> {
        val library = parse(fileName)
            ?: return AppResult.Error(
                message = "starter pack not readable: $fileName",
                uiMessage = UiMessage(R.string.error_import_failed)
            )
        return try {
            var added = 0
            var skipped = 0
            for (video in library.videos) {
                if (whitelistItemDao.findByYoutubeId(kidProfileId, video.id) != null) {
                    skipped++
                    continue
                }
                whitelistItemDao.insert(
                    WhitelistItemEntity(
                        id = UUID.randomUUID().toString(),
                        kidProfileId = kidProfileId,
                        type = WhitelistItemType.VIDEO,
                        youtubeId = video.id,
                        title = video.title,
                        thumbnailUrl = thumbnailUrl(video.id),
                        channelTitle = video.channelTitle
                    )
                )
                added++
            }

            val preset = library.profilePreset.takeIf { applyPreset }
            val presetApplied = preset != null && applyPreset(kidProfileId, preset)
            AppResult.Success(StarterPackImportResult(added, skipped, presetApplied))
        } catch (e: Exception) {
            AppResult.Error(
                message = e.message ?: "starter pack import failed",
                uiMessage = UiMessage(R.string.error_import_failed)
            )
        }
    }

    /** Übernimmt nur die Ruhezeit; die übrigen Vorgaben hat die Android-Fassung noch nicht. */
    private suspend fun applyPreset(kidProfileId: String, preset: ContentProfilePreset): Boolean {
        val profile = kidProfileDao.getProfileByIdOnce(kidProfileId) ?: return false
        val updated = profile.copy(
            bedtimeEnabled = preset.bedtimeEnabled ?: profile.bedtimeEnabled,
            bedtimeStartMinutes = preset.bedtimeStartMinutes ?: profile.bedtimeStartMinutes,
            bedtimeEndMinutes = preset.bedtimeEndMinutes ?: profile.bedtimeEndMinutes,
            bedtimeWeekendOffsetMinutes = preset.bedtimeWeekendOffsetMinutes
                ?: profile.bedtimeWeekendOffsetMinutes
        )
        if (updated == profile) return false
        kidProfileDao.update(updated)
        return true
    }

    private fun parse(fileName: String): ContentLibrary? {
        val raw = source.read(fileName) ?: return null
        return runCatching { json.decodeFromString<ContentLibrary>(raw) }.getOrNull()
    }

    private fun thumbnailUrl(videoId: String) = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
}
