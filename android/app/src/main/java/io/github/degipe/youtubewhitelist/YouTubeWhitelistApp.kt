package io.github.degipe.youtubewhitelist

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class YouTubeWhitelistApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                    .build()
            }
            // Thumbnails have no transparency: half the memory per bitmap and a
            // faster decode on the small, low-density Sidephone screen.
            .bitmapConfig(Bitmap.Config.RGB_565)
            // A thumbnail URL always points at the same image — serve it from
            // the cache instead of revalidating it over the network.
            .respectCacheHeaders(false)
            // Show the cached image immediately instead of fading it in.
            .crossfade(false)
            .build()
    }
}
