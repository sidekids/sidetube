package io.github.degipe.youtubewhitelist.core.export.content

import io.github.degipe.youtubewhitelist.core.common.result.AppResult

/** Kurzbeschreibung eines Startpakets für die Auswahl im Elternbereich. */
data class StarterPack(
    val fileName: String,
    val id: String,
    val title: String,
    val note: String?,
    val videoCount: Int,
    val hasProfilePreset: Boolean
)

data class StarterPackImportResult(
    val added: Int,
    val skipped: Int,
    val presetApplied: Boolean
)

/**
 * Übernimmt kuratierte Startpakete in die Whitelist eines Profils.
 * Bereits vorhandene Videos werden übersprungen, es entsteht also kein zweites Profil
 * und keine Dublette – anders als beim Sicherungs-Import.
 */
interface StarterPackService {
    suspend fun availablePacks(): List<StarterPack>
    suspend fun import(
        fileName: String,
        kidProfileId: String,
        applyPreset: Boolean
    ): AppResult<StarterPackImportResult>
}
