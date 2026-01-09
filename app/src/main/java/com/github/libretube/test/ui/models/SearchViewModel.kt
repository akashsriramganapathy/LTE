package com.github.libretube.test.ui.models

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.libretube.test.api.MediaServiceRepository
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.db.DatabaseHolder
import com.github.libretube.test.helpers.PreferenceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SearchItem {
    abstract val query: String

    data class History(override val query: String) : SearchItem()
    data class Suggestion(override val query: String) : SearchItem()
}

class SearchViewModel : ViewModel() {
    val searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val liveSuggestions = searchQuery
        .flatMapLatest { query ->
            if (query.isEmpty()) return@flatMapLatest flowOf(emptyList<String>())

            flow {
                if (PreferenceHelper.getBoolean(PreferenceKeys.SEARCH_SUGGESTIONS, true)) {
                    try {
                        val suggestions = withContext(Dispatchers.IO) {
                            MediaServiceRepository.instance.getSuggestions(query)
                        }
                        emit(suggestions)
                    } catch (e: Exception) {
                        Log.e("SearchViewModel", "failed to fetch suggestions", e)
                        emit(emptyList<String>())
                    }
                } else {
                    emit(emptyList<String>())
                }
            }
        }

    private val searchHistory = DatabaseHolder.Database.searchHistoryDao().getAllFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiSuggestions: StateFlow<List<SearchItem>> = combine(
        searchQuery,
        liveSuggestions,
        searchHistory
    ) { query, suggestions, history ->
        if (query.isEmpty()) {
            history.map { SearchItem.History(it.query) }
        } else {
            suggestions.map { SearchItem.Suggestion(it) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setQuery(query: String?) {
        this.searchQuery.value = query ?: ""
    }

    fun deleteHistoryItem(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseHolder.Database.searchHistoryDao().deleteByQuery(query)
        }
    }
}
