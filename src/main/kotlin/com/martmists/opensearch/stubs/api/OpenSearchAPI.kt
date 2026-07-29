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
    val path: String

    suspend fun suggest(query: String): List<OpenSearchSuggestion>
    suspend fun search(query: String): String

    val details: OpenSearchDescription
    val description: String
        get() {
            val details = this.details
            val tagString = details.tags.joinToString(" ")

            require(details.shortName.length in 1..16)
            require(details.description.length in 1..16)
            require(tagString.length in 0..256)
            require((details.longName?.length ?: 1) in 1..256)

            return buildString {
                appendLine("<?xml version=\"1.0\"?>")
                appendLine("<OpenSearchDescription xmlns=\"http://a9.com/-/spec/opensearch/1.1/\" xmlns:moz=\"https://www.mozilla.org/2006/browser/search/\">")
                appendLine("  <ShortName>${details.shortName}</ShortName>")
                appendLine("  <Description>${details.description}</Description>")
                if (details.tags.isNotEmpty()) {
                    appendLine("  <Tags>$tagString</Tags>")
                }
                appendLine("  <Contact>mail@martmists.com</Contact>")
                appendLine("  <Url type=\"text/html\" rel=\"results\" template=\"https://opensearch.martmists.com/${path}/search?q={searchTerms}\" />")
                appendLine("  <Url type=\"application/x-suggestions+json\" rel=\"suggestions\" template=\"https://opensearch.martmists.com/${path}/suggest?q={searchTerms}\" />")
                if (details.longName != null) {
                    appendLine("  <LongName>${details.longName}</LongName>")
                }
                for (image in details.images) {
                    appendLine("  <Image width=\"${image.size}\" height=\"${image.size}\" type=\"${image.mimeType}\">${image.url}</Image>")
                }
                appendLine("  <Developer>Martmists</Developer>")
                appendLine("  <SyndicationRight>limited</SyndicationRight>")
                appendLine("</OpenSearchDescription>")
            }
        }

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
