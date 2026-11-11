package de.schuelken.cloudbridge.settings.preferences

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.util.Log
import androidx.preference.DialogPreference
import ca.pkay.rcloneexplorer.R
import de.schuelken.cloudbridge.extensions.tag
import de.schuelken.cloudbridge.settings.preferences.dialogs.FilesizeDialog
import java.lang.NumberFormatException


class FilesizePreference(context: Context, attrs: AttributeSet): DialogPreference(context, attrs) {

    override fun getDialogLayoutResource() = R.layout.preference_filesize

    override fun onClick() {
        FilesizeDialog(this.context, getPersistedLong(0)) {
            persistLong(it)
            notifyChanged()
        }.show()
    }

    fun getValue(): Long {
        return getPersistedLong(defaultValue)
    }

    private var defaultValue = 0L

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        val defaultValueString = a.getString(index)?: ""

        if(defaultValueString.toLongOrNull() == null) {
            throw NumberFormatException("The provided default value is not valid: $defaultValueString")
        }

        defaultValue = defaultValueString.toLong()
        return defaultValueString
    }
}