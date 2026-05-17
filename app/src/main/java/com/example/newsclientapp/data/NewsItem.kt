package com.example.newsclientapp.data

data class NewsItem(
    val id: String,
    val title: String,
    val description: String,
    val link: String,
    val source: NewsSource,
    val publishedAtMillis: Long,
    val publishedAtLabel: String
)

enum class NewsSource(
    val displayName: String,
    val feedUrl: String
) {
    HABR(
        displayName = "Хабр",
        feedUrl = "https://habr.com/ru/rss/articles/?fl=ru"
    ),
    LENTA(
        displayName = "Лента.ру",
        feedUrl = "https://lenta.ru/rss/news"
    )
}
