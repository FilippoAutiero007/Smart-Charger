package com.example.smartdisplay

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class NewsData(
    val articles: List<NewsArticle> = emptyList(),
    val totalResults: Int = 0
)

data class NewsArticle(
    val title: String,
    val description: String = "",
    val source: String = "",
    val url: String = "",
    val publishedAt: String = ""
)

object NewsService {
    private const val API_KEY = "255701c9dac5418c89a83c0c50dac61a"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var cachedNews: NewsData? = null
    private var lastFetch: Long = 0
    private const val CACHE_DURATION = 30 * 60 * 1000L

    fun getCached(): NewsData? {
        if (System.currentTimeMillis() - lastFetch < CACHE_DURATION) {
            return cachedNews
        }
        return null
    }

    fun fetchTopHeadlines(
        country: String = "it",
        category: String = "",
        pageSize: Int = 5
    ): NewsData? {
        try {
            val url = buildString {
                append("https://newsapi.org/v2/top-headlines?country=$country&pageSize=$pageSize&apiKey=$API_KEY")
                if (category.isNotBlank()) append("&category=$category")
            }
            return fetch(url)
        } catch (_: Exception) {
            return cachedNews
        }
    }

    fun fetchEverything(
        query: String,
        from: String = "",
        sortBy: String = "popularity",
        pageSize: Int = 5
    ): NewsData? {
        try {
            val url = buildString {
                append("https://newsapi.org/v2/everything?q=$query&pageSize=$pageSize&sortBy=$sortBy&apiKey=$API_KEY")
                if (from.isNotBlank()) append("&from=$from")
            }
            return fetch(url)
        } catch (_: Exception) {
            return cachedNews
        }
    }

    private fun fetch(url: String): NewsData? {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return null
        val json = JSONObject(body)

        if (json.optString("status") != "ok") return null

        val articlesArr = json.optJSONArray("articles") ?: JSONArray()
        val articles = mutableListOf<NewsArticle>()
        for (i in 0 until articlesArr.length()) {
            val obj = articlesArr.optJSONObject(i) ?: continue
            val source = obj.optJSONObject("source")?.optString("name", "") ?: ""
            articles.add(NewsArticle(
                title = obj.optString("title", ""),
                description = obj.optString("description", ""),
                source = source,
                url = obj.optString("url", ""),
                publishedAt = obj.optString("publishedAt", "")
            ))
        }

        val data = NewsData(articles, json.optInt("totalResults", 0))
        cachedNews = data
        lastFetch = System.currentTimeMillis()
        return data
    }
}
