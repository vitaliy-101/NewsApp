package com.example.newsclientapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newsclientapp.data.NewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NewsViewModel : ViewModel() {
    private val repository = NewsRepository()

    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    init {
        loadNews()
    }

    fun loadNews() {
        _uiState.value = NewsUiState.Loading

        viewModelScope.launch {
            runCatching { repository.loadNews() }
                .onSuccess { news ->
                    _uiState.value = NewsUiState.Success(news)
                }
                .onFailure { error ->
                    _uiState.value = NewsUiState.Error(
                        error.message ?: "Произошла ошибка при загрузке новостей."
                    )
                }
        }
    }
}
