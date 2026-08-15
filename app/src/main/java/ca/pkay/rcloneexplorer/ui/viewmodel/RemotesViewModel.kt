package ca.pkay.rcloneexplorer.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.InteractiveRunner
import ca.pkay.rcloneexplorer.InteractiveRunner.Step
import ca.pkay.rcloneexplorer.InteractiveRunner.StringAction
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.RemoteConfig.OauthHelper
import ca.pkay.rcloneexplorer.RemoteConfig.OauthHelper.InitOauthStep
import ca.pkay.rcloneexplorer.RemoteConfig.OauthHelper.OauthFinishStep
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
    val isRefreshing: Boolean = false,
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
        val cachedQuotas = ca.pkay.rcloneexplorer.data.RemoteTelemetryCacheRepository.getAll(application)
        _uiState.update { it.copy(storageQuotas = cachedQuotas) }
        loadRemotes()
    }

    fun loadRemotes(force: Boolean = false) {
        viewModelScope.launch {
            val hasExisting = _uiState.value.remotes.isNotEmpty()
            val cachedQuotas = ca.pkay.rcloneexplorer.data.RemoteTelemetryCacheRepository.getAll(getApplication())
            _uiState.update {
                it.copy(
                    isLoading = !hasExisting || force,
                    isRefreshing = hasExisting && !force,
                    storageQuotas = if (cachedQuotas.isNotEmpty()) cachedQuotas else it.storageQuotas,
                    errorMessage = null
                )
            }
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
                    isRefreshing = false,
                    remotes = list,
                    displayRemotes = filterRemotes(list, it.searchQuery)
                )
            }
        }
    }

    fun refresh() {
        loadRemotes(force = false)
    }

    fun fetchStorageQuota(remote: RemoteItem) {
        // If already cached, make sure it's in UI state
        val cached = ca.pkay.rcloneexplorer.data.RemoteTelemetryCacheRepository.get(getApplication(), remote.name)
        if (cached != null && !_uiState.value.storageQuotas.containsKey(remote.name)) {
            _uiState.update { it.copy(storageQuotas = it.storageQuotas + (remote.name to cached)) }
        }

        if (_uiState.value.loadingQuotas.contains(remote.name)) return

        _uiState.update {
            it.copy(loadingQuotas = it.loadingQuotas + remote.name)
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    rclone.aboutRemote(remote)
                } catch (e: Exception) {
                    FLog.e(TAG, "Error fetching quota for ${remote.name}", e)
                    null
                }
            }

            if (result != null && !result.hasFailed()) {
                ca.pkay.rcloneexplorer.data.RemoteTelemetryCacheRepository.put(getApplication(), remote.name, result)
            }

            _uiState.update {
                val updatedQuotas = if (result != null) it.storageQuotas + (remote.name to result) else it.storageQuotas
                it.copy(
                    storageQuotas = updatedQuotas,
                    loadingQuotas = it.loadingQuotas - remote.name
                )
            }
        }
    }

    fun togglePinRemote(remote: RemoteItem) {
        val stringSet = prefs.getStringSet(
            getApplication<Application>().getString(R.string.shared_preferences_pinned_remotes),
            HashSet()
        )
        val pinned = HashSet(stringSet ?: emptySet())
        if (remote.isPinned) {
            pinned.remove(remote.name)
            remote.pin(false)
        } else {
            pinned.add(remote.name)
            remote.pin(true)
        }
        prefs.edit().putStringSet(
            getApplication<Application>().getString(R.string.shared_preferences_pinned_remotes),
            pinned
        ).apply()

        loadRemotes()
    }

    fun deleteRemote(remote: RemoteItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            withContext(Dispatchers.IO) {
                try {
                    rclone.deleteRemote(remote.name)
                    ca.pkay.rcloneexplorer.data.RemoteTelemetryCacheRepository.remove(getApplication(), remote.name)
                    ca.pkay.rcloneexplorer.data.DirectoryCacheRepository.invalidateRemote(remote.name)
                } catch (e: Exception) {
                    FLog.e(TAG, "Error deleting remote", e)
                }
            }
            _uiState.update { it.copy(infoMessage = "Deleted remote \"${remote.displayName}\"") }
            loadRemotes()
        }
    }

    fun reconnectRemote(remote: RemoteItem, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(infoMessage = "Re-authenticating ${remote.displayName}...") }
            val success = withContext(Dispatchers.IO) {
                try {
                    val process = rclone.reconnectRemote(remote)
                    if (process != null) {
                        val start = Step("y/n> ", StringAction("y"))
                        val postOauth = start.addFollowing("y/n> ", "y")
                            .addFollowing(InitOauthStep(context))
                            .addFollowing(OauthFinishStep())

                        if (RemoteItem.ONEDRIVE == remote.type) {
                            postOauth.addFollowing("OneDrive Personal or Business", "onedrive")
                                .addFollowing("Chose drive to use:> ", "0")
                                .addFollowing("y/n> ", "y")
                        }

                        val runner = InteractiveRunner(start, { e ->
                            FLog.e(TAG, "OAuth recipe error for ${remote.typeReadable}", e)
                            process.destroy()
                        }, process)
                        OauthHelper.registerRunner(runner)
                        runner.runSteps()
                        val exitCode = process.waitFor()
                        exitCode == 0
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    FLog.e(TAG, "Failed reconnecting remote", e)
                    false
                }
            }
            if (success) {
                _uiState.update { it.copy(infoMessage = "Successfully re-authenticated ${remote.displayName}") }
            } else {
                _uiState.update { it.copy(infoMessage = "Re-authentication cancelled or failed") }
            }
            loadRemotes(force = true)
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
