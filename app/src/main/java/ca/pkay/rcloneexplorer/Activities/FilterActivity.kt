package ca.pkay.rcloneexplorer.Activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.size
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ca.pkay.rcloneexplorer.Database.DatabaseHandler
import ca.pkay.rcloneexplorer.Items.Filter
import ca.pkay.rcloneexplorer.Items.FilterEntry
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.RecyclerViewAdapters.FilterEntryRecyclerViewAdapter
import ca.pkay.rcloneexplorer.util.ActivityHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import es.dmoral.toasty.Toasty
import jp.wasabeef.recyclerview.animators.LandingAnimator

class FilterActivity : AppCompatActivity() {


    private lateinit var rcloneInstance: Rclone
    private lateinit var dbHandler: DatabaseHandler

    private lateinit var filterTitle: EditText
    private lateinit var filterList: RecyclerView


    private var existingFilter: Filter? = null
    private var filters: ArrayList<FilterEntry> = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityHelper.applyTheme(this)
        setContentView(R.layout.activity_filter)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        filterTitle = findViewById(R.id.filter_title_textfield)
        filterList = findViewById(R.id.filter_filterlist)
        val newFilterEntry = findViewById<Button>(R.id.filter_add_filterentry_button)
        newFilterEntry.setOnClickListener {
            filters.add(FilterEntry(FilterEntry.FILTER_EXCLUDE, ""))
            filterList.adapter?.notifyItemInserted(filterList.size)
        }


        rcloneInstance = Rclone(this)
        dbHandler = DatabaseHandler(this)
        val extras = intent.extras
        val filterId: Long
        if (extras != null) {
            filterId = extras.getLong(ID_EXTRA)
            if (filterId != 0L) {
                existingFilter = dbHandler.getFilter(filterId)
                if (existingFilter == null) {
                    Toasty.error(
                            this,
                            this.resources.getString(R.string.filteractivity_filter_not_found)
                    ).show()
                    finish()
                }
            }
        }
        val fab = findViewById<FloatingActionButton>(R.id.saveButton)
        fab.setOnClickListener {
            if (existingFilter == null) {
                saveFilter()
            } else {
                persistFilterChanges()
            }
        }

        if(existingFilter != null) {
            filters = existingFilter!!.getFilters()
        }
        filterTitle.setText(existingFilter?.title)
        prepareFilterList()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun persistFilterChanges() {
        val updatedFilter = getFilterValues(existingFilter!!.id)
        if (updatedFilter != null) {
            dbHandler.updateFilter(updatedFilter)
            val resultIntent = Intent()
            resultIntent.putExtra(SAVED_FILTER_ID_EXTRA, updatedFilter.id)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun saveFilter() {
        val newFilter = getFilterValues(0)
        if (newFilter != null) {
            val filter = dbHandler.createFilter(newFilter)
            val resultIntent = Intent()
            resultIntent.putExtra(SAVED_FILTER_ID_EXTRA, filter.id)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun getFilterValues(id: Long): Filter? {
        val filterToPopulate = Filter(id)
        filterToPopulate.title = filterTitle.text.toString()
        filterToPopulate.setFilters(filters)
        if (filterTitle.text.toString() == "") {
            Toasty.error(
                    this.applicationContext,
                    getString(R.string.filter_data_validation_error_no_title),
                    Toast.LENGTH_SHORT,
                    true
            ).show()
            return null
        }
        return filterToPopulate
    }
    private fun prepareFilterList() {
        val adapter = FilterEntryRecyclerViewAdapter(filters, this)
        filterList.layoutManager = LinearLayoutManager(this)
        filterList.itemAnimator = LandingAnimator()
        filterList.adapter = adapter
    }
    companion object {
        const val ID_EXTRA = "FILTER_EDIT_ID"
        const val SAVED_FILTER_ID_EXTRA = "filterId"
    }
}