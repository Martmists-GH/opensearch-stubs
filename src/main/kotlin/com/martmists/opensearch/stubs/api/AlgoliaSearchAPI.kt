package com.martmists.opensearch.stubs.api

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addAll
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class AlgoliaSearchAPI(
    override val path: String,
    private val baseUrl: String,
    private val searchResultsUrl: String,
    private val searchApiUrl: String,
    private val auth: Pair<String, String>,
    private val facets: List<String> = emptyList(),
    detailsFactory: () -> OpenSearchDescription,
) : OpenSearchAPI {
    override val details = detailsFactory()

    @Serializable
    private data class SearchResults(
        val hits: List<Item>
    ) {
        @Serializable
        data class Item(
            val pageTitle: String,
            val mainTitle: String,
            val url: String,
            val breadcrumbs: String = "",
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun fetchResults(query: String): SearchResults {
        val res = client.post(searchApiUrl) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(buildJsonObject {
                put("query", query)
                putJsonArray("attributesToRetrieve") {
                    add("pageTitle")
                    add("url")
                    add("breadcrumbs")
                    add("mainTitle")
                }
                if (facets.isNotEmpty()) {
                    putJsonArray("facetFilters") {
                        addAll(facets)
                    }
                }
                header("x-algolia-application-id", auth.first)
                header("x-algolia-api-key", auth.second)
            }.toString())
        }
        return res.body<SearchResults>()
    }

    fun fullUrl(url: String): String = if (url.startsWith("http")) url else "$baseUrl/${url.trimStart('/')}"

    override suspend fun search(query: String): String {
        val res = fetchResults(query)

        if (res.hits.size == 1) {
            return fullUrl(res.hits.first().url)
        }

        return res.hits.firstOrNull {
            it.pageTitle == query
        }?.url?.let(::fullUrl) ?: (searchResultsUrl + query.encodeURLParameter())
    }

    override suspend fun suggest(query: String): List<OpenSearchSuggestion> {
        return fetchResults(query).hits.map {
            OpenSearchSuggestion(
                it.pageTitle,
                if (it.url.startsWith("http")) it.url else "$baseUrl/${it.url}",
                buildString {
                    if (it.breadcrumbs.isNotBlank()) {
                        append(it.breadcrumbs)
                        append(" > ")
                    }
                    if (it.mainTitle != it.pageTitle) {
                        append(it.mainTitle)
                        append(" > ")
                    }
                    append(it.pageTitle)
                }
            )
        }
    }

    companion object {
        val KOTLIN = AlgoliaSearchAPI(
            "/kotlin",
            "https://kotlinlang.org",
            "https://kotlinlang.org/docs/home.html?s=full&q=",
            "https://7961pkyrxv-dsn.algolia.net/1/indexes/prod_KOTLINLANG_WEBHELP/query",
            "7961PKYRXV" to "1bfad5fdbae302b33d844ed1b43ec4d5",
        ) {
            OpenSearchDescription(
                "Kotlin",
                "Kotlin Documentation",
                listOf(
                    OpenSearchDescription.Image(16, "https://kotlinlang.org/assets/images/favicon.svg?v2", ContentType.Image.SVG),
                    OpenSearchDescription.Image(32, "https://kotlinlang.org/assets/images/favicon.ico?v2", ContentType.Image.XIcon),
                ),
                tags = listOf("kotlin", "official", "multiplatform", "compose")
            )
        }

        val EXPOSED = AlgoliaSearchAPI(
            "/exposed",
            "https://www.jetbrains.com/",
            "https://www.jetbrains.com/help/exposed/home.html?s=full&q=",
            "https://ohiv241qet-dsn.algolia.net/1/indexes/prod_JETBRAINSCOM_HELP/query",
            "OHIV241QET" to "8c2683cac2d71d547b55183297abb506",
            listOf("product:exposed")
        ) {
            OpenSearchDescription(
                "Exposed",
                "Jetbrains Exposed Documentation",
                listOf(
                    OpenSearchDescription.Image(64, "https://www.jetbrains.com/favicon.ico?r=1234", ContentType.Image.XIcon),
                ),
                tags = listOf("kotlin", "official", "exposed")
            )
        }

        val KTOR = AlgoliaSearchAPI(
            "/ktor",
            "https://ktor.io/docs/welcome.html",
            "https://ktor.io/docs/welcome.html?s=full&q=",
            "https://ohiv241qet-dsn.algolia.net/1/indexes/prod_JETBRAINSCOM_HELP/query",
            "OHIV241QET" to "8c2683cac2d71d547b55183297abb506",
            listOf("product:docs")
        ) {
            OpenSearchDescription(
                "Ktor",
                "Ktor Documentation",
                listOf(
                    OpenSearchDescription.Image(16, "https://resources.jetbrains.com/storage/products/company/brand/logos/Ktor_icon.png", ContentType.Image.PNG),
                ),
                tags = listOf("kotlin", "official", "ktor")
            )
        }

        val COMPOSE_MULTIPLATFORM = AlgoliaSearchAPI(
            "/composemp",
            "https://kotlinlang.org",
            "https://kotlinlang.org/api/compose-multiplatform/?s=full&q=",
            "https://7961pkyrxv-dsn.algolia.net/1/indexes/compose-multiplatform/query",
            "7961PKYRXV" to "1bfad5fdbae302b33d844ed1b43ec4d5",
            listOf("product:docs")
        ) {
            OpenSearchDescription(
                "Ktor",
                "Ktor Documentation",
                listOf(
                    OpenSearchDescription.Image(16, "https://resources.jetbrains.com/storage/products/company/brand/logos/Ktor_icon.png", ContentType.Image.PNG),
                ),
                tags = listOf("kotlin", "official", "ktor")
            )
        }
    }
}
