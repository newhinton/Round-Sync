package de.schuelken.cloudbridge.settings.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import ca.pkay.rcloneexplorer.R


/**
 * https://stackoverflow.com/a/70795847
 */


class ButtonPreference(context: Context, attrs: AttributeSet): Preference(context, attrs) {

    private lateinit var holder: PreferenceViewHolder
    private var clickListener: View.OnClickListener? = null
    private var text: String? = null

    init {
        widgetLayoutResource = R.layout.preference_button;
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        this.holder = holder
        updateViews()
    }

    fun setButtonOnClick(listener: View.OnClickListener) {
        clickListener = listener
        updateViews()
    }

    fun setButtonText(text: String) {
        this.text = text
        updateViews()
    }

    private fun updateViews() {
        if(this::holder.isInitialized) {
            val button = holder.itemView.findViewById<Button>(R.id.preference_button)
            button.text = text
            button.contentDescription = text
            button.setOnClickListener(clickListener)
        }
    }
}