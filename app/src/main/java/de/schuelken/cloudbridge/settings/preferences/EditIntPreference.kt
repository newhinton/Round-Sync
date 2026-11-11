package de.schuelken.cloudbridge.settings.preferences

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import java.lang.NumberFormatException

class EditIntPreference(context: Context, attrs: AttributeSet): EditTextPreference(context, attrs) {
    override fun persistString(value: String?): Boolean {
        if(value == null) {
            return false
        }
        return persistInt(Integer.valueOf(value))
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        text = getPersistedInt(this.defaultValue).toString()
    }

    private var defaultValue = 0

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        val defaultValueString = a.getString(index)?: ""

        if(defaultValueString.toIntOrNull() == null) {
            throw NumberFormatException("The provided default value is not valid: $defaultValueString")
        }

        defaultValue = Integer.valueOf(defaultValueString)
        return defaultValueString
    }


}