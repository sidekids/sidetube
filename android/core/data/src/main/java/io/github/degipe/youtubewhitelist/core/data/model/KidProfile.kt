package io.github.degipe.youtubewhitelist.core.data.model

import io.github.degipe.youtubewhitelist.core.data.bedtime.BedtimeSettings

data class KidProfile(
    val id: String,
    val parentAccountId: String,
    val name: String,
    val avatarUrl: String?,
    val dailyLimitMinutes: Int?,
    val sleepPlaylistId: String?,
    val createdAt: Long,
    val bedtime: BedtimeSettings = BedtimeSettings()
)
