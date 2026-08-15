package ca.pkay.rcloneexplorer.Settings

import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.AppShortcutsHelper
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.util.FLog
import de.felixnuesse.extract.extensions.TAG
import de.felixnuesse.extract.settings.language.LanguagePicker
import de.felixnuesse.extract.settings.preferences.FilesizePreference
import es.dmoral.toasty.Toasty
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GeneralPreferencesFragment : PreferenceFragmentCompat() {


    private lateinit var sharedPreferences: SharedPreferences


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_general_preferences, rootKey)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        requireActivity().title = getString(R.string.pref_header_general)


        val thumbnailKey = getString(R.string.pref_key_thumbnail_size_limit)
        val thumbnailSizePreference = findPreference(thumbnailKey) as FilesizePreference?
        thumbnailSizePreference?.summaryProvider =
            Preference.SummaryProvider<FilesizePreference> { preference ->
                val size = preference.getValue()
                val sizeMb = (size / 1024 / 1024)
                resources.getString(R.string.pref_thumbnails_size_summary, sizeMb.toFloat())
            }

        val budgetKey = getString(R.string.pref_key_thumbnail_cache_budget)
        val budgetPreference = findPreference(budgetKey) as FilesizePreference?
        budgetPreference?.summaryProvider =
            Preference.SummaryProvider<FilesizePreference> { preference ->
                val size = preference.getValue()
                val sizeMb = (size / 1024 / 1024)
                "$sizeMb MB"
            }

        val clearCachePreference = findPreference(getString(R.string.pref_key_clear_thumbnail_cache)) as Preference?
        clearCachePreference?.setOnPreferenceClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val imageLoader = coil.Coil.imageLoader(requireContext())
                    imageLoader.diskCache?.clear()
                    ca.pkay.rcloneexplorer.data.CacheManager.clearCache(requireContext())
                    withContext(Dispatchers.Main) {
                        imageLoader.memoryCache?.clear()
                        Toasty.success(requireContext(), getString(R.string.thumbnail_cache_cleared), Toast.LENGTH_SHORT, true).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toasty.error(requireContext(), "Failed to clear cache: ${e.localizedMessage}", Toast.LENGTH_SHORT, true).show()
                    }
                }
            }
            true
        }

        val telemetryBudgetKey = getString(R.string.pref_key_telemetry_cache_budget)
        val telemetryBudgetPreference = findPreference(telemetryBudgetKey) as FilesizePreference?
        telemetryBudgetPreference?.summaryProvider =
            Preference.SummaryProvider<FilesizePreference> { preference ->
                val size = preference.getValue()
                val sizeMb = (size / 1024 / 1024)
                "$sizeMb MB"
            }

        val clearTelemetryCachePreference = findPreference(getString(R.string.pref_key_clear_telemetry_cache)) as Preference?
        clearTelemetryCachePreference?.setOnPreferenceClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    ca.pkay.rcloneexplorer.data.RemoteTelemetryCacheRepository.clear(requireContext())
                    ca.pkay.rcloneexplorer.data.DirectoryCacheRepository.clear(requireContext())
                    withContext(Dispatchers.Main) {
                        Toasty.success(requireContext(), getString(R.string.telemetry_cache_cleared), Toast.LENGTH_SHORT, true).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toasty.error(requireContext(), "Failed to clear cache: ${e.localizedMessage}", Toast.LENGTH_SHORT, true).show()
                    }
                }
            }
            true
        }

        // Setup Default File Openers
        setupDefaultOpeners()

        val shortcutsPreference = findPreference("AppShortcutTempKey") as Preference?
        shortcutsPreference?.setOnPreferenceClickListener {
            showAppShortcutDialog()
            true
        }

        val languagePreference = findPreference("languagePickerTempKey") as Preference?
        languagePreference?.setSummary(LanguagePicker(requireContext()).getCurrentLocale()?.displayLanguage)
        languagePreference?.setOnPreferenceClickListener {
            LanguagePicker(requireContext()).showPicker()
            true
        }

    }

    private fun showThumbnailSizeDialog(preference: Preference) {
        //Todo: Migrate this to dedicated dialog
        val builder = AlertDialog.Builder(requireContext())

        val thumbnailSizeEdit = EditText(context)
        thumbnailSizeEdit.inputType = InputType.TYPE_CLASS_NUMBER
        val size = sharedPreferences.getLong(
            requireContext().getString(R.string.pref_key_thumbnail_size_limit),
            resources.getInteger(R.integer.default_thumbnail_size_limit).toLong()
        )
        thumbnailSizeEdit.setText((size / (1024 * 1024.0)).toString())

        builder.setTitle(R.string.pref_thumbnails_dlg_title)
        builder.setView(thumbnailSizeEdit)

        builder.setNegativeButton(R.string.cancel, null)
        builder.setPositiveButton(R.string.select) { _: DialogInterface?, _: Int ->
            val sizeString = thumbnailSizeEdit.text.toString()
            val size1: Long
            val sizeMb: Double
            try {
                sizeMb = sizeString.toDouble()
                size1 = (sizeMb * 1024 * 1024).toLong()
            } catch (e: NumberFormatException) {
                FLog.e(TAG(), "showThumbnailSizeDialog: invalid size", e)
                return@setPositiveButton
            }
            sharedPreferences.edit().putLong(getString(R.string.pref_key_thumbnail_size_limit), size1).apply()
            preference.setSummary(resources.getString(R.string.pref_thumbnails_size_summary, sizeMb))

        }

        builder.show()
    }

    private fun showAppShortcutDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
            requireContext()
        )
        val appShortcuts = sharedPreferences.getStringSet(
            getString(R.string.shared_preferences_app_shortcuts),
            HashSet()
        )

        val builder = AlertDialog.Builder(
            requireContext()
        )
        builder.setTitle(R.string.app_shortcuts_settings_dialog_title)

        val rclone = Rclone(context)
        val remotes = ArrayList(rclone.remotes)
        RemoteItem.prepareDisplay(context, remotes)
        remotes.sortWith { a: RemoteItem, b: RemoteItem -> a.displayName.compareTo(b.displayName) }
        val options = arrayOfNulls<CharSequence>(remotes.size)
        var i = 0
        for (remoteItem in remotes) {
            options[i++] = remoteItem.displayName
        }

        val userSelected = ArrayList<String>()
        val checkedItems = BooleanArray(options.size)
        i = 0
        for (item in remotes) {
            val s = item.name.toString()
            val hash = AppShortcutsHelper.getUniqueIdFromString(s)
            if (appShortcuts?.contains(hash) == true) {
                userSelected.add(item.name.toString())
                checkedItems[i] = true
            }
            i++
        }

        builder.setMultiChoiceItems(
            options,
            checkedItems
        ) { _: DialogInterface?, which: Int, isChecked: Boolean ->
            if (userSelected.size >= 4 && isChecked) {
                Toasty.info(
                    requireContext(),
                    getString(R.string.app_shortcuts_max_toast),
                    Toast.LENGTH_SHORT,
                    true
                ).show()
                //((AlertDialog)dialog).getListView().setItemChecked(which, false); This doesn't work
            }
            if (isChecked) {
                userSelected.add(options[which].toString())
            } else {
                userSelected.remove(options[which].toString())
            }
        }

        builder.setNegativeButton(R.string.cancel, null)
        builder.setPositiveButton(R.string.select) { _: DialogInterface?, _: Int ->
            setAppShortcuts(
                remotes,
                userSelected
            )
        }

        builder.show()
    }

    private fun setAppShortcuts(
        remoteItems: ArrayList<RemoteItem>,
        initialShortcuts: ArrayList<String>
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return
        }

        val appShortcuts = if (initialShortcuts.size > 4) {
            ArrayList(initialShortcuts.subList(0, 4))
        } else {
            initialShortcuts
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
            requireContext()
        )
        val editor = sharedPreferences.edit()
        val savedAppShortcutIds = sharedPreferences.getStringSet(
            getString(R.string.shared_preferences_app_shortcuts),
            emptySet()
        ) ?: emptySet()
        val updatedAppShortcutIDds: MutableSet<String> = HashSet(savedAppShortcutIds)

        // Remove app shortcuts first
        val appShortcutIds = ArrayList<String>()
        for (s in appShortcuts) {
            appShortcutIds.add(AppShortcutsHelper.getUniqueIdFromString(s))
        }
        val removedIds: MutableList<String> = ArrayList(savedAppShortcutIds)
        removedIds.removeAll(appShortcutIds)
        if (removedIds.isNotEmpty()) {
            AppShortcutsHelper.removeAppShortcutIds(context, removedIds)
        }

        updatedAppShortcutIDds.removeAll(removedIds.toSet())

        // add new app shortcuts
        for (appShortcut in appShortcuts) {
            val id = AppShortcutsHelper.getUniqueIdFromString(appShortcut)
            if (updatedAppShortcutIDds.contains(id)) {
                continue
            }

            var remoteItem: RemoteItem? = null
            for (item in remoteItems) {
                if (item.name == appShortcut) {
                    remoteItem = item
                    break
                }
            }
            if (remoteItem == null) {
                continue
            }

            AppShortcutsHelper.addRemoteToAppShortcuts(context, remoteItem, id)
            updatedAppShortcutIDds.add(id)
        }

        editor.putStringSet(
            getString(R.string.shared_preferences_app_shortcuts),
            updatedAppShortcutIDds
        )
        editor.apply()
    }

    private fun setupDefaultOpeners() {
        val imageKey = getString(R.string.pref_key_default_image_opener)
        val videoKey = getString(R.string.pref_key_default_video_opener)
        val audioKey = getString(R.string.pref_key_default_audio_opener)
        val docKey = getString(R.string.pref_key_default_doc_opener)
        val resetKey = getString(R.string.pref_key_reset_default_openers)

        val imagePref = findPreference<Preference>(imageKey)
        val videoPref = findPreference<Preference>(videoKey)
        val audioPref = findPreference<Preference>(audioKey)
        val docPref = findPreference<Preference>(docKey)
        val resetPref = findPreference<Preference>(resetKey)

        fun updateOpenerSummaries() {
            val ctx = context ?: return
            imagePref?.summary = ca.pkay.rcloneexplorer.util.DefaultOpenerHelper.getOpenerSummary(ctx, imageKey)
            videoPref?.summary = ca.pkay.rcloneexplorer.util.DefaultOpenerHelper.getOpenerSummary(ctx, videoKey)
            audioPref?.summary = ca.pkay.rcloneexplorer.util.DefaultOpenerHelper.getOpenerSummary(ctx, audioKey)
            docPref?.summary = ca.pkay.rcloneexplorer.util.DefaultOpenerHelper.getOpenerSummary(ctx, docKey)
        }

        updateOpenerSummaries()

        imagePref?.setOnPreferenceClickListener {
            ca.pkay.rcloneexplorer.util.DefaultOpenerHelper.showAppPickerDialog(
                requireContext(), imageKey, getString(R.string.pref_default_image_opener_title), "image/*"
            ) { updateOpenerSummaries() }
            true
        }

        videoPref?.setOnPreferenceClickListener {
            ca.pkay.rcloneexplorer.util.DefaultOpenerHelper.showAppPickerDialog(
                requireContext(), videoKey, getString(R.string.pref_default_video_opener_title), "video/*"
            ) { updateOpenerSummaries() }
            true
        }

        audioPref?.setOnPreferenceClickListener {
            ca.pkay.rcloneexplorer.util.DefaultOpenerHelper.showAppPickerDialog(
                requireContext(), audioKey, getString(R.string.pref_default_audio_opener_title), "audio/*"
            ) { updateOpenerSummaries() }
            true
        }

        docPref?.setOnPreferenceClickListener {
            ca.pkay.rcloneexplorer.util.DefaultOpenerHelper.showAppPickerDialog(
                requireContext(), docKey, getString(R.string.pref_default_doc_opener_title), "application/pdf"
            ) { updateOpenerSummaries() }
            true
        }

        resetPref?.setOnPreferenceClickListener {
            ca.pkay.rcloneexplorer.util.DefaultOpenerHelper.resetAllOpeners(requireContext())
            updateOpenerSummaries()
            Toasty.success(requireContext(), getString(R.string.default_openers_reset_success), Toast.LENGTH_SHORT, true).show()
            true
        }
    }

}