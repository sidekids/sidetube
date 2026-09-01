package io.github.degipe.youtubewhitelist.core.export.content

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Liefert die Startpaket-Dateien; getrennt vom Dienst, damit er ohne Android testbar bleibt. */
interface StarterPackSource {
    fun listLibraries(): List<String>
    fun read(fileName: String): String?
}

@Singleton
class AssetStarterPackSource @Inject constructor(
    @ApplicationContext private val context: Context
) : StarterPackSource {

    override fun listLibraries(): List<String> =
        runCatching { context.assets.list(LIBRARY_DIR)?.filter { it.endsWith(".json") }.orEmpty() }
            .getOrDefault(emptyList())
            .sorted()

    override fun read(fileName: String): String? =
        runCatching { context.assets.open("$LIBRARY_DIR/$fileName").bufferedReader().use { it.readText() } }
            .getOrNull()

    private companion object {
        const val LIBRARY_DIR = "content/libraries"
    }
}
