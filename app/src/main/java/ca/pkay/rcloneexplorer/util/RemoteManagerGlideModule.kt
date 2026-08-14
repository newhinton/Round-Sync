package ca.pkay.rcloneexplorer.util

import android.content.Context
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.R
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

@GlideModule
class RemoteManagerGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val cacheBudgetBytes = prefs.getLong(
            context.getString(R.string.pref_key_thumbnail_cache_budget),
            104857600L // 100 MB default
        )

        builder.setDiskCache(
            InternalCacheDiskCacheFactory(
                context,
                "remote_thumbnail_cache",
                cacheBudgetBytes
            )
        )

        builder.setMemoryCache(LruResourceCache(20L * 1024L * 1024L))
        builder.setDefaultRequestOptions(
            RequestOptions().format(DecodeFormat.PREFER_RGB_565)
        )
    }

    override fun isManifestParsingEnabled(): Boolean = false
}
