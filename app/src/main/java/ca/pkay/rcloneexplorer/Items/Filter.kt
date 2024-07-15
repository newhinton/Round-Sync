package ca.pkay.rcloneexplorer.Items

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import org.json.JSONObject

@Serializable
data class Filter(var id: Long) {
    @JsonNames("name") var title = ""

    private var filters = ""

    override fun toString(): String {
        return "$title: $filters"
    }

    companion object {
        var TABLE_NAME = "filter_table"
        var COLUMN_NAME_ID = "filter_id"
        var COLUMN_NAME_TITLE = "filter_title"
        var COLUMN_NAME_FILTERS = "filter_filters"

        fun fromString(json: String): Filter {
            return Json.decodeFromString(json)
        }
    }

    fun asJSON(): JSONObject {
        return JSONObject(Json.encodeToString(this))
    }

    fun setFiltersRaw(filters: String)
    {
        this.filters = filters;
    }
    fun getFiltersRaw(): String
    {
        return filters;
    }
    fun getFilters(): ArrayList<FilterEntry>
    {
        val filters = ArrayList<FilterEntry>()
        val f = this.filters.split(System.lineSeparator())
        for (filter in f) {
            if (filter == "") continue
            val filterType = if(filter.substring(0, 1) == "+") FilterEntry.FILTER_INCLUDE else FilterEntry.FILTER_EXCLUDE
            val filterText = filter.substring(1)
            filters.add(FilterEntry(filterType, filterText))
        }
        return filters;
    }
    fun setFilters(filterEntries: List<FilterEntry>)
    {
        var filters = ""

        for (filter in filterEntries) {
            filters += if(filter.filterType == FilterEntry.FILTER_INCLUDE) "+" else "-"
            filters += filter.filter
            filters += System.lineSeparator()
        }
        this.filters = filters
    }
}