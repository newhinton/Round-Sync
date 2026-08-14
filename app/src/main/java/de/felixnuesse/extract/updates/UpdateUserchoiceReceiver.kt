package de.felixnuesse.extract.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.BuildConfig
import ca.pkay.rcloneexplorer.R
import de.felixnuesse.extract.extensions.tag
import de.felixnuesse.extract.notifications.AppUpdateNotification
import java.io.File
import java.io.FileOutputStream
import java.net.URL


class UpdateUserchoiceReceiver : BroadcastReceiver() {

    companion object {
        var ACTION_IGNORE = "ACTION_IGNORE"
        var ACTION_DOWNLOAD = "ACTION_DOWNLOAD"
        var IGNORE_VERSION_EXTRA = "IGNORE_VERSION_EXTRA"
    }
    override fun onReceive(context: Context, intent: Intent) {

        val preferenceManager = PreferenceManager.getDefaultSharedPreferences(context)
        if(intent.action == ACTION_IGNORE) {
            Log.e(tag(), "Ignore current update!")
            val key = context.getString(R.string.pref_key_app_update_dismiss_current_update)
            preferenceManager.edit().putString(key, intent.getStringExtra(IGNORE_VERSION_EXTRA)).apply()
            AppUpdateNotification(context).cancelNotification()
        }

        if(intent.action == ACTION_DOWNLOAD) {
            val versionKey = context.getString(R.string.pref_key_app_updates_found_update_for_version)
            val version = preferenceManager.getString(versionKey,"")?: ""

            if(version.isNotEmpty()) {
                val tag = if (version.startsWith("v")) version else "v$version"
                val cleanVersion = version.removePrefix("v")
                val downloadUrl = "https://github.com/neubofy/Remote-Manager/releases/download/$tag/RemoteManager_v$cleanVersion.apk"
                downloadAndInstall(
                    URL(downloadUrl),
                    context,
                    cleanVersion
                )
            }
            AppUpdateNotification(context).cancelNotification()
        }
    }

    private fun downloadAndInstall(url: URL, context: Context, version: String) {
        Log.d(tag(), "Download url: $url")
        Thread {
            try {
                val dir = context.externalCacheDir ?: context.cacheDir
                val target = File(dir, "RemoteManager_v$version.apk")
                if (target.exists()) {
                    target.delete()
                }
                url.openStream().use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }

                val fileUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", target)

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(tag(), "Error downloading update", e)
            }
        }.start()
    }
}