package ca.pkay.rcloneexplorer

import android.app.Application
import androidx.preference.PreferenceManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class RemoteManagerApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val budgetBytes = prefs.getLong(
            getString(R.string.pref_key_thumbnail_cache_budget),
            104857600L // 100 MB default
        )

        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(budgetBytes)
                    .build()
            }
            .allowRgb565(true)
            .crossfade(true)
            .build()
    }
}
