package de.schuelken.cloudbridge.settings.preferences.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import ca.pkay.rcloneexplorer.R
import com.google.android.material.textfield.TextInputEditText


class FilesizeDialog(context: Context, private var filesizeBytes: Long, private var resultCallback: (input: Long) -> Unit): Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.preference_filesize)

        window?.setLayout(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)

        val dropdown = (findViewById<View>(R.id.sizeSelectorAutocomplete) as AutoCompleteTextView)
        val textview = (findViewById<View>(R.id.filesizeTextview) as TextInputEditText)
        val ok = (findViewById<View>(R.id.ok) as TextView)
        val cancel = (findViewById<View>(R.id.cancel) as TextView)

        textview.setText(filesizeBytes.toString())

        dropdown.setText("B")
        dropdown.setAdapter(
            ArrayAdapter(
                context,
                android.R.layout.simple_dropdown_item_1line,
                context.resources.getStringArray(R.array.filesize_selector)
            )
        )

        ok.setOnClickListener {
            var size = textview.text.toString().toLong()
            val selector = dropdown.text.toString()

            when(selector) {
                "KB" -> size *= 1024
                "MB" -> size *= (1024 * 1024)
            }

            resultCallback(size)
            dismiss()
        }

        cancel.setOnClickListener {
            dismiss()
        }

    }

}