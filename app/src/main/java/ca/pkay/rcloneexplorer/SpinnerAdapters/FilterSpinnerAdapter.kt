package ca.pkay.rcloneexplorer.SpinnerAdapters

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import ca.pkay.rcloneexplorer.R

class FilterSpinnerAdapter(context: Context, resource: Int, objects: List<String?>) :
        ArrayAdapter<String?>(context, resource, objects) {

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_spinner_dropdown_item, parent, false)
        val textView = view.findViewById<TextView>(android.R.id.text1)

        // Set special styling for "No Filter" option
        if (getItem(position) == context.getString(R.string.task_edit_filter_nofilter)) {
            textView.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textView.setTypeface(null, Typeface.BOLD)
        } else {
            textView.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textView.setTypeface(null, Typeface.NORMAL)
        }

        textView.text = getItem(position)
        return view
    }
}