package ca.pkay.rcloneexplorer.RecyclerViewAdapters

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import androidx.recyclerview.widget.RecyclerView
import ca.pkay.rcloneexplorer.Items.FilterEntry
import ca.pkay.rcloneexplorer.R
import de.felixnuesse.extract.extensions.tag


class FilterEntryRecyclerViewAdapter(
    private val mFilterEntries: ArrayList<FilterEntry>,
    private val mContext: Context
) : RecyclerView.Adapter<FilterEntryRecyclerViewAdapter.ViewHolder>() {
    private var view: View? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_filter_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val selectedFilterEntry = mFilterEntries[position]

        holder.filterText.setText(selectedFilterEntry.filter)
        holder.filterText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                selectedFilterEntry.filter = s.toString()
            }

            override fun afterTextChanged(s: Editable) {}
        })

        val spinnerAdapter = ArrayAdapter(
            holder.itemView.context,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf(
                mContext.getString(R.string.filter_type_include),
                mContext.getString(R.string.filter_type_exclude)
            )
        )
        holder.filterTypeSpinner.adapter = spinnerAdapter
        holder.filterTypeSpinner.setSelection(selectedFilterEntry.filterType)
        holder.filterTypeSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    pos: Int,
                    id: Long
                ) {
                    selectedFilterEntry.filterType = pos
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        holder.filterDelete.setOnClickListener { v: View ->
            removeItem(selectedFilterEntry)
        }
    }

    private fun removeItem(filterEntry: FilterEntry) {
        val index = mFilterEntries.indexOf(filterEntry)
        if (index >= 0) {
            mFilterEntries.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    override fun getItemCount(): Int {
        return mFilterEntries.size
    }

    class ViewHolder internal constructor(val view: View?) : RecyclerView.ViewHolder(
        view!!
    ) {
        val filterTypeSpinner: Spinner = view!!.findViewById(R.id.filter_entry_filter_type)
        val filterText: EditText = view!!.findViewById(R.id.filter_entry_filter_text)
        val filterDelete: ImageButton = view!!.findViewById(R.id.filter_entry_delete)
    }
}
