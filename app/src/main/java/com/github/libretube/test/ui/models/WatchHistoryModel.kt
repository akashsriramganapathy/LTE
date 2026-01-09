package com.github.libretube.test.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.db.DatabaseHelper
import com.github.libretube.test.db.DatabaseHolder
import com.github.libretube.test.db.obj.WatchHistoryItem
import com.github.libretube.test.extensions.toMillis
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.util.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WatchHistoryModel : ViewModel() {
    private val _watchHistory = MutableStateFlow<List<WatchHistoryItem>>(emptyList())
    val watchHistory = _watchHistory.asStateFlow()

    private val _selectedStatus = MutableStateFlow(
        PreferenceHelper.getInt(PreferenceKeys.SELECTED_HISTORY_STATUS_FILTER, 0)
    )
    val selectedStatus = _selectedStatus.asStateFlow()

    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode = _isMultiSelectMode.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<String>>(emptySet())
    val selectedItems = _selectedItems.asStateFlow()

    private var currentPage = 1
    private var isLoading = false

    val filteredWatchHistory: StateFlow<List<WatchHistoryItem>> = combine(
        _watchHistory,
        _selectedStatus
    ) { history, filter ->
        if (filter == 0) history
        else {
            val result = mutableListOf<WatchHistoryItem>()
            for (item in history) {
                if (item.shouldIncludeByFilter(filter)) {
                    result.add(item)
                }
            }
            result
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Pre-grouped history for UI to avoid heavy calculations in Composable
    val groupedWatchHistory: StateFlow<Map<String, List<WatchHistoryItem>>> = filteredWatchHistory
        .map { history ->
            history.groupBy { 
                TextUtils.formatRelativeDate(it.uploadDate?.toMillis() ?: 0L).toString() 
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    var selectedStatusFilter: Int
        get() = _selectedStatus.value
        set(value) {
            PreferenceHelper.putInt(PreferenceKeys.SELECTED_HISTORY_STATUS_FILTER, value)
            _selectedStatus.value = value
        }

    private suspend fun WatchHistoryItem.shouldIncludeByFilter(filter: Int): Boolean {
        // filter: 1 = continue, 2 = finished
        return when (filter) {
            1 -> DatabaseHelper.filterByWatchStatus(this)
            2 -> DatabaseHelper.filterByWatchStatus(this, false)
            else -> true
        }
    }

    fun fetchNextPage() = viewModelScope.launch(Dispatchers.IO) {
        if (isLoading) return@launch
        isLoading = true

        val newHistory = withContext(Dispatchers.IO) {
            DatabaseHelper.getWatchHistoryPage(currentPage, HISTORY_PAGE_SIZE)
        }

        if (newHistory.isNotEmpty()) {
            val currentList = _watchHistory.value.toMutableList()
            currentList.addAll(newHistory)
            _watchHistory.value = currentList
            currentPage++
        }
        
        isLoading = false
    }

    fun removeFromHistory(watchHistoryItem: WatchHistoryItem) =
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseHolder.Database.watchHistoryDao().delete(watchHistoryItem)
            _watchHistory.value = _watchHistory.value.filter { it != watchHistoryItem }
            _selectedItems.value = _selectedItems.value - watchHistoryItem.videoId
        }

    fun toggleMultiSelectMode() {
        _isMultiSelectMode.value = !_isMultiSelectMode.value
        if (!_isMultiSelectMode.value) {
            _selectedItems.value = emptySet()
        }
    }

    fun toggleItemSelection(videoId: String) {
        val currentSelected = _selectedItems.value.toMutableSet()
        if (currentSelected.contains(videoId)) {
            currentSelected.remove(videoId)
        } else {
            currentSelected.add(videoId)
        }
        _selectedItems.value = currentSelected
    }

    fun deleteSelectedItems() = viewModelScope.launch(Dispatchers.IO) {
        val toDelete = _selectedItems.value
        val itemsToDelete = _watchHistory.value.filter { toDelete.contains(it.videoId) }
        
        itemsToDelete.forEach {
            DatabaseHolder.Database.watchHistoryDao().delete(it)
        }

        _watchHistory.value = _watchHistory.value.filter { !toDelete.contains(it.videoId) }
        _selectedItems.value = emptySet()
        _isMultiSelectMode.value = false
    }

    companion object {
        private const val HISTORY_PAGE_SIZE = 15
    }
}
