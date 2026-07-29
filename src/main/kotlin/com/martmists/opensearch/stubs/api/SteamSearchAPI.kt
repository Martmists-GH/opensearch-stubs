package com.martmists.opensearch.stubs.api

import com.martmists.opensearch.stubs.steamapi.CStoreQuery_SearchSuggestions_Request
import com.martmists.opensearch.stubs.steamapi.CStoreQuery_SearchSuggestions_Response
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.io.encoding.Base64

object SteamSearchAPI : OpenSearchAPI {
    override val path = "/steam"

    override suspend fun suggest(query: String): List<OpenSearchSuggestion> {
        val arr = CStoreQuery_SearchSuggestions_Request.newBuilder().apply {
            setSearchTerm(query)
            contextBuilder.apply {
                setLanguage("english")
                setCountryCode("US")
            }
            dataRequestBuilder.apply {
                setIncludeBasicInfo(true)
                setIncludeLinks(true)
            }
        }.build().toByteArray()

        val queryString = Base64.encode(arr)
        val resp = client.get("https://api.steampowered.com/IStoreQueryService/SearchSuggestions/v1") {
            parameter("input_protobuf_encoded", queryString)
        }

        val res = CStoreQuery_SearchSuggestions_Response.parseFrom(resp.bodyAsBytes())
        return res.storeItemsList.map {
            OpenSearchSuggestion(
                it.name,
                "https://store.steampowered.com/" + it.storeUrlPath,
                "${it.name} by ${it.basicInfo.developersList.joinToString { it.name } }"
            )
        }
    }

    override suspend fun search(query: String): String {
        val results = suggest(query)

        if (results.size == 1) {
            return results.first().url
        }

        return "https://store.steampowered.com/search?term=" + query.encodeURLParameter()
    }

    override val details = OpenSearchDescription(
        "Steam",
        "Steam Store",
        listOf(
            OpenSearchDescription.Image(256, "https://store.steampowered.com/favicon.ico", ContentType.Image.XIcon)
        )
    )

}
