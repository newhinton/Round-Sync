package de.schuelken.cloudbridge.settings.language

import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import ca.pkay.rcloneexplorer.R
import java.util.Locale


class LanguagePicker(private val mContext: Context) {

    fun showPicker() {

        val current = getCurrentLocale()
        val dataAdapter =  ArrayAdapter(mContext, R.layout.spinner_dropdown_item, supportedAdapterLocales)

        val builder = AlertDialog.Builder(mContext)
        builder.setTitle(R.string.pref_locale_dlg_title)
        builder.setNegativeButton(R.string.cancel, null)
        builder.setPositiveButton(R.string.select) { dialog, _ ->
            var list = (dialog as AlertDialog).listView
            var lang = list.adapter.getItem(list.checkedItemPosition)
            setLanguage((lang as LocaleAdapter).getLocale())
        }
        builder.setSingleChoiceItems(dataAdapter, supportedLocales.indexOf(current), null)
        builder.show()

    }

    fun getCurrentLocale(): Locale? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mContext.resources.configuration.locales.get(0)
        } else {
            mContext.resources.configuration.locale
        }
    }

    private val supportedLocales: ArrayList<Locale>
        get() {
            var appLocales = arrayListOf<Locale>()
            mContext.resources.getStringArray(R.array.locales).forEach {
                appLocales.add(Locale.forLanguageTag(it))
            }
            return appLocales
        }

    private val supportedAdapterLocales: ArrayList<LocaleAdapter>
        get() {
            var adapterLocales = arrayListOf<LocaleAdapter>()
            supportedLocales.forEach {
            adapterLocales.add(LocaleAdapter(it))
        }
        return adapterLocales
    }

    private fun setLanguage(lang: Locale) {
        val appLocale: LocaleListCompat = LocaleListCompat.create(lang)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

}