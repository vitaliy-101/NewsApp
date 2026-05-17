package com.example.newsclientapp.ui

import com.example.newsclientapp.data.NewsItem

sealed interface NewsUiState {
    data object Loading : NewsUiState
    data class Success(val news: List<NewsItem>) : NewsUiState
    data class Error(val message: String) : NewsUiState
}
