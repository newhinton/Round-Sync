package ca.pkay.rcloneexplorer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class BookmarkItem(
    val remoteName: String,
    val path: String,
    val label: String
)

class BookmarksManager(private val context: Context) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val PREF_KEY_BOOKMARKS = "rm_remote_bookmarks"

    private val _bookmarksFlow = MutableStateFlow<List<BookmarkItem>>(emptyList())
    val bookmarksFlow: StateFlow<List<BookmarkItem>> = _bookmarksFlow.asStateFlow()

    init {
        loadBookmarks()
    }

    fun loadBookmarks(): List<BookmarkItem> {
        val jsonStr = prefs.getString(PREF_KEY_BOOKMARKS, "[]") ?: "[]"
        val list = mutableListOf<BookmarkItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val remoteName = obj.optString("remoteName", "")
                val path = obj.optString("path", "")
                val label = obj.optString("label", path.substringAfterLast('/', path))
                if (remoteName.isNotEmpty()) {
                    list.add(BookmarkItem(remoteName, path, label))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _bookmarksFlow.value = list
        return list
    }

    fun addBookmark(remoteName: String, path: String, label: String? = null): Boolean {
        val list = loadBookmarks().toMutableList()
        val displayLabel = if (label.isNullOrBlank()) {
            if (path == "//$remoteName" || path.isEmpty() || path == "/") remoteName
            else path.trimEnd('/').substringAfterLast('/')
        } else label

        val existing = list.indexOfFirst { it.remoteName == remoteName && it.path == path }
        if (existing >= 0) {
            list[existing] = BookmarkItem(remoteName, path, displayLabel)
        } else {
            list.add(BookmarkItem(remoteName, path, displayLabel))
        }
        saveBookmarks(list)
        return true
    }

    fun removeBookmark(remoteName: String, path: String): Boolean {
        val list = loadBookmarks().toMutableList()
        val removed = list.removeAll { it.remoteName == remoteName && it.path == path }
        if (removed) {
            saveBookmarks(list)
        }
        return removed
    }

    fun isBookmarked(remoteName: String, path: String): Boolean {
        return loadBookmarks().any { it.remoteName == remoteName && it.path == path }
    }

    private fun saveBookmarks(list: List<BookmarkItem>) {
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("remoteName", item.remoteName)
            obj.put("path", item.path)
            obj.put("label", item.label)
            jsonArray.put(obj)
        }
        prefs.edit().putString(PREF_KEY_BOOKMARKS, jsonArray.toString()).apply()
        _bookmarksFlow.value = list
    }
}
