package ca.pkay.rcloneexplorer.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.Items.FileItem
import ca.pkay.rcloneexplorer.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

data class AppChoice(
    val title: String,
    val packageName: String?,
    val icon: Drawable?
)

object DefaultOpenerHelper {

    const val OP_ASK = "ask"
    const val OP_SYSTEM_DEFAULT = "system_default"

    fun getCategoryMime(fileItem: FileItem): String {
        val mime = fileItem.mimeType ?: ""
        return when {
            mime.startsWith("image/") -> "image/*"
            mime.startsWith("video/") -> "video/*"
            mime.startsWith("audio/") -> "audio/*"
            mime.contains("pdf") -> "application/pdf"
            mime.contains("text") || mime.contains("document") -> "text/plain"
            else -> "*/*"
        }
    }

    fun getPrefKeyForMime(mime: String, context: Context): String {
        return when {
            mime.startsWith("image/") -> context.getString(R.string.pref_key_default_image_opener)
            mime.startsWith("video/") -> context.getString(R.string.pref_key_default_video_opener)
            mime.startsWith("audio/") -> context.getString(R.string.pref_key_default_audio_opener)
            else -> context.getString(R.string.pref_key_default_doc_opener)
        }
    }

    fun getOpenerPackage(context: Context, mime: String): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = getPrefKeyForMime(mime, context)
        val value = prefs.getString(key, OP_ASK) ?: OP_ASK
        return if (value == OP_ASK) null else value
    }

    fun getOpenerSummary(context: Context, prefKey: String): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val value = prefs.getString(prefKey, OP_ASK) ?: OP_ASK
        if (value == OP_ASK) {
            return context.getString(R.string.default_opener_ask_every_time)
        }
        if (value == OP_SYSTEM_DEFAULT) {
            return context.getString(R.string.default_opener_system_default)
        }
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(value, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            context.getString(R.string.default_opener_ask_every_time)
        }
    }

    fun showAppPickerDialog(
        context: Context,
        prefKey: String,
        title: String,
        mimeType: String,
        onSelected: () -> Unit
    ) {
        val pm = context.packageManager
        val queryIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://dummy/file"), mimeType)
        }
        val resolveInfos = pm.queryIntentActivities(queryIntent, PackageManager.MATCH_DEFAULT_ONLY)

        val choices = mutableListOf<AppChoice>()
        choices.add(AppChoice(context.getString(R.string.default_opener_ask_every_time), OP_ASK, null))
        choices.add(AppChoice(context.getString(R.string.default_opener_system_default), OP_SYSTEM_DEFAULT, null))

        val seenPackages = mutableSetOf<String>()
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && !seenPackages.contains(pkg)) {
                seenPackages.add(pkg)
                val appLabel = info.loadLabel(pm).toString()
                val appIcon = info.loadIcon(pm)
                choices.add(AppChoice(appLabel, pkg, appIcon))
            }
        }

        val adapter = object : ArrayAdapter<AppChoice>(context, android.R.layout.select_dialog_item, choices) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.select_dialog_item, parent, false)
                val tv = row.findViewById<TextView>(android.R.id.text1)
                val item = getItem(position)
                tv.text = item?.title
                if (item?.icon != null) {
                    val icon = item.icon
                    icon.setBounds(0, 0, 72, 72)
                    tv.setCompoundDrawables(icon, null, null, null)
                    tv.compoundDrawablePadding = 24
                } else {
                    tv.setCompoundDrawables(null, null, null, null)
                }
                return row
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setAdapter(adapter) { _, which ->
                val selected = choices[which]
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                prefs.edit().putString(prefKey, selected.packageName ?: OP_ASK).apply()
                onSelected()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun resetAllOpeners(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putString(context.getString(R.string.pref_key_default_image_opener), OP_ASK)
            .putString(context.getString(R.string.pref_key_default_video_opener), OP_ASK)
            .putString(context.getString(R.string.pref_key_default_audio_opener), OP_ASK)
            .putString(context.getString(R.string.pref_key_default_doc_opener), OP_ASK)
            .apply()
    }

    fun launchWithConfiguredOpener(
        fragment: Fragment,
        intent: Intent,
        chooserTitle: String,
        fileItem: FileItem,
        requestCode: Int? = null,
        onUnknownFallback: (() -> Unit)? = null
    ) {
        val ctx = fragment.context ?: return
        val pm = ctx.packageManager
        val categoryMime = getCategoryMime(fileItem)
        val openerPkg = getOpenerPackage(ctx, categoryMime)

        // If file type is unknown or no app resolves it, fallback to universal */*
        if (intent.resolveActivity(pm) == null && intent.type != "*/*") {
            val uri = intent.data
            if (uri != null) {
                intent.setDataAndType(uri, "*/*")
            }
        }

        try {
            if (openerPkg != null && openerPkg != OP_SYSTEM_DEFAULT) {
                intent.setPackage(openerPkg)
                if (requestCode != null) {
                    fragment.startActivityForResult(intent, requestCode)
                } else {
                    fragment.startActivity(intent)
                }
            } else if (openerPkg == OP_SYSTEM_DEFAULT) {
                if (requestCode != null) {
                    fragment.startActivityForResult(intent, requestCode)
                } else {
                    fragment.startActivity(intent)
                }
            } else {
                val chooser = Intent.createChooser(intent, chooserTitle)
                if (requestCode != null) {
                    fragment.startActivityForResult(chooser, requestCode)
                } else {
                    fragment.startActivity(chooser)
                }
            }
        } catch (e: Exception) {
            // If designated app fails or uninstalled, try wildcard chooser
            try {
                intent.setPackage(null)
                val uri = intent.data
                if (uri != null) intent.setDataAndType(uri, "*/*")
                val chooser = Intent.createChooser(intent, chooserTitle)
                if (requestCode != null) {
                    fragment.startActivityForResult(chooser, requestCode)
                } else {
                    fragment.startActivity(chooser)
                }
            } catch (e2: Exception) {
                // If even chooser fails, call user-fallback dialog (e.g. OpenAsDialog)
                onUnknownFallback?.invoke()
            }
        }
    }
}
