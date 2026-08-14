package ca.pkay.rcloneexplorer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.util.FLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.HashSet

data class RemotesUiState(
    val remotes: List<RemoteItem> = emptyList(),
    val displayRemotes: List<RemoteItem> = emptyList(),
    val storageQuotas: Map<String, Rclone.AboutResult> = emptyMap(),
    val loadingQuotas: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null
)

class RemotesViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "RemotesVM"
    private val rclone = Rclone(application)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)

    private val _uiState = MutableStateFlow(RemotesUiState())
    val uiState: StateFlow<RemotesUiState> = _uiState.asStateFlow()

    init {
        loadRemotes()
    }

    fun loadRemotes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val list = withContext(Dispatchers.IO) {
                try {
                    val rawList = rclone.remotes ?: emptyList()
                    val prepared = RemoteItem.prepareDisplay(getApplication(), rawList)
                    Collections.sort(prepared)
                    prepared
                } catch (e: Exception) {
                    FLog.e(TAG, "Failed loading remotes", e)
                    emptyList()
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    remotes = list,
                    displayRemotes = filterRemotes(list, it.searchQuery)
                )
            }
        }
    }

    fun fetchStorageQuota(remote: RemoteItem) {
        val remoteName = remote.name
        if (_uiState.value.loadingQuotas.contains(remoteName)) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(loadingQuotas = it.loadingQuotas + remoteName)
            }

            val result = withContext(Dispatchers.IO) {
                try {
                    rclone.aboutRemote(remote)
                } catch (e: Exception) {
                    FLog.e(TAG, "Failed fetching about for ${remote.name}", e)
                    null
                }
            }

            _uiState.update { state ->
                val newMap = state.storageQuotas.toMutableMap()
                if (result != null && !result.hasFailed()) {
                    newMap[remoteName] = result
                }
                state.copy(
                    loadingQuotas = state.loadingQuotas - remoteName,
                    storageQuotas = newMap
                )
            }
        }
    }

    fun togglePin(remote: RemoteItem) {
        val pinnedKey = getApplication<Application>().getString(R.string.shared_preferences_pinned_remotes)
        val currentPinned = prefs.getStringSet(pinnedKey, HashSet())?.toMutableSet() ?: mutableSetOf()

        if (remote.isPinned) {
            currentPinned.remove(remote.name)
            remote.pin(false)
        } else {
            currentPinned.add(remote.name)
            remote.pin(true)
        }
        prefs.edit().putStringSet(pinnedKey, currentPinned).apply()
        loadRemotes()
    }

    fun deleteRemote(remote: RemoteItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            withContext(Dispatchers.IO) {
                try {
                    rclone.deleteRemote(remote.name)
                } catch (e: Exception) {
                    FLog.e(TAG, "Error deleting remote", e)
                }
            }
            _uiState.update { it.copy(infoMessage = "Deleted remote \"${remote.displayName}\"") }
            loadRemotes()
        }
    }

    fun reconnectRemote(remote: RemoteItem) {
        viewModelScope.launch {
            val process = withContext(Dispatchers.IO) {
                rclone.reconnectRemote(remote)
            }
            if (process != null) {
                _uiState.update { it.copy(infoMessage = "Reconnected ${remote.displayName}") }
            } else {
                _uiState.update { it.copy(errorMessage = "Could not reconnect remote") }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                displayRemotes = filterRemotes(it.remotes, query)
            )
        }
    }

    fun toggleSearch() {
        _uiState.update {
            val next = !it.isSearching
            it.copy(
                isSearching = next,
                searchQuery = if (next) it.searchQuery else "",
                displayRemotes = if (next) it.displayRemotes else it.remotes
            )
        }
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    private fun filterRemotes(list: List<RemoteItem>, query: String): List<RemoteItem> {
        if (query.isBlank()) return list
        return list.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.typeReadable?.contains(query, ignoreCase = true) == true
        }
    }
}
