package com.example.newsclientapp.data

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class NewsRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    suspend fun loadNews(): List<NewsItem> = withContext(Dispatchers.IO) {
        val results = coroutineScope {
            NewsSource.entries.map { source ->
                async {
                    source to runCatching { loadSourceNews(source) }
                }
            }.awaitAll()
        }

        val errors = mutableListOf<String>()
        val news = results.flatMap { (source, result) ->
            result.getOrElse { error ->
                errors += "${source.displayName}: ${error.message ?: "ошибка загрузки"}"
                emptyList()
            }
        }

        if (news.isEmpty()) {
            val errorMessage = errors.joinToString(separator = "\n")
                .ifBlank { "Источники не вернули новости." }
            throw IOException(errorMessage)
        }

        news
            .distinctBy { it.link }
            .sortedByDescending { it.publishedAtMillis }
    }

    private suspend fun loadSourceNews(source: NewsSource): List<NewsItem> {
        return withTimeout(15_000) {
            val request = Request.Builder()
                .url(source.feedUrl)
                .header("User-Agent", "NewsClientApp/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Сервер вернул код ${response.code}")
                }

                val xml = response.body?.string()
                    ?: throw IOException("Пустой ответ от сервера")

                val parsedItems = parseFeed(xml = xml, source = source)
                if (parsedItems.isEmpty()) {
                    throw IOException("Лента пуста или имеет неподдерживаемый формат")
                }

                parsedItems
            }
        }
    }

    private fun parseFeed(
        xml: String,
        source: NewsSource
    ): List<NewsItem> {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(StringReader(xml))
        }

        val items = mutableListOf<NewsItem>()
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name.lowercase(Locale.ROOT)) {
                    "item" -> readRssItem(parser, source)?.let(items::add)
                    "entry" -> readAtomEntry(parser, source)?.let(items::add)
                }
            }
            eventType = parser.next()
        }

        return items
    }

    private fun readRssItem(
        parser: XmlPullParser,
        source: NewsSource
    ): NewsItem? {
        var title = ""
        var link = ""
        var description = ""
        var pubDate = ""

        while (parser.next() != XmlPullParser.END_TAG || !parser.name.equals("item", ignoreCase = true)) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name.lowercase(Locale.ROOT)) {
                    "title" -> title = parser.nextText()
                    "link" -> link = parser.nextText()
                    "description" -> description = parser.nextText()
                    "pubdate" -> pubDate = parser.nextText()
                    else -> skipTag(parser)
                }
            }
        }

        return createNewsItem(
            source = source,
            title = title,
            description = description,
            link = link,
            rawDate = pubDate
        )
    }

    private fun readAtomEntry(
        parser: XmlPullParser,
        source: NewsSource
    ): NewsItem? {
        var title = ""
        var link = ""
        var description = ""
        var publishedDate = ""

        while (parser.next() != XmlPullParser.END_TAG || !parser.name.equals("entry", ignoreCase = true)) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name.lowercase(Locale.ROOT)) {
                    "title" -> title = parser.nextText()
                    "summary", "content" -> if (description.isBlank()) description = parser.nextText() else skipTag(parser)
                    "published", "updated" -> if (publishedDate.isBlank()) publishedDate = parser.nextText() else skipTag(parser)
                    "link" -> {
                        val href = parser.getAttributeValue(null, "href").orEmpty()
                        if (href.isNotBlank()) {
                            link = href
                        }
                        skipTag(parser)
                    }
                    else -> skipTag(parser)
                }
            }
        }

        return createNewsItem(
            source = source,
            title = title,
            description = description,
            link = link,
            rawDate = publishedDate
        )
    }

    private fun createNewsItem(
        source: NewsSource,
        title: String,
        description: String,
        link: String,
        rawDate: String
    ): NewsItem? {
        if (title.isBlank() || link.isBlank()) {
            return null
        }

        val publishedAtMillis = parseDate(rawDate)

        return NewsItem(
            id = "${source.name}-$link",
            title = cleanText(title),
            description = cleanText(description),
            link = link,
            source = source,
            publishedAtMillis = publishedAtMillis,
            publishedAtLabel = formatDate(publishedAtMillis)
        )
    }

    private fun skipTag(parser: XmlPullParser) {
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
            }
        }
    }

    private fun parseDate(rawDate: String): Long {
        if (rawDate.isBlank()) return 0L

        return runCatching {
            ZonedDateTime.parse(
                rawDate.trim(),
                DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH)
            ).toInstant().toEpochMilli()
        }.recoverCatching {
            java.time.OffsetDateTime.parse(rawDate.trim()).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) return "Дата не указана"

        return DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru"))
            .format(java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()))
    }

    private fun cleanText(rawText: String): String {
        return HtmlCompat.fromHtml(rawText, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
