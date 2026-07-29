package com.martmists.opensearch.stubs.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

data class OpenSearchDescription(
    val shortName: String,
    val description: String,
    val images: List<Image> = emptyList(),
    val longName: String? = null,
    val tags: List<String> = emptyList(),
) {
    data class Image private constructor(
        val size: Int,
        val url: String,
        val mimeType: String,
    ) {
        constructor(size: Int, url: String, type: ContentType) : this(size, url, type.toString())
    }

    companion object {
        fun favicon(url: String) = Image(16, url, ContentType.Image.XIcon)
    }
}

data class OpenSearchSuggestion(
    val suggestion: String,
    val url: String,
    val description: String = suggestion,
)

interface OpenSearchAPI {
    /**
     * The route at which this endpoint is hosted.
     */
    val path: String

    /**
     * Return a list of suggestions for the given query.
     */
    suspend fun suggest(query: String): List<OpenSearchSuggestion>

    /**
     * Provide the matching page URL for the given query if possible.
     * Else, provide the search page with the query string applied.
     */
    suspend fun search(query: String): String

    /**
     * The description of the OpenSearch endpoint. Used for XML generation only.
     */
    val details: OpenSearchDescription

    val client: HttpClient
        get() = OpenSearchAPI.client

    companion object {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }
}
