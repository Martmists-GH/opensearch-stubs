package com.martmists.opensearch.stubs.api

import com.martmists.opensearch.stubs.util.Ratelimiter
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

object ScryfallSearchAPI : OpenSearchAPI {
    override val path = "/scryfall"
    private val ratelimiter = Ratelimiter(2, 500.milliseconds)

    @Serializable
    data class ScryfallList(
        val data: List<ScryfallCard>,
    )

    @Serializable
    data class ScryfallCard(
        val name: String,
        @SerialName("scryfall_uri")
        val uri: String,
        @SerialName("type_line")
        val typeLine: String,
    )

    suspend fun Ratelimiter.search(query: String) = invoke {
        val resp = client.get("https://api.scryfall.com/cards/search") {
            parameter("q", query)
            parameter("unique", "cards")
        }
        resp.body<ScryfallList>().data.map {
            OpenSearchSuggestion(it.name, it.uri, "${it.name} — ${it.typeLine}")
        }
    }

    override suspend fun suggest(query: String): List<OpenSearchSuggestion> {
        return ratelimiter.search(query)
    }

    override suspend fun search(query: String): String {
        return "https://scryfall.com/search?q=" + query.encodeURLParameter()
    }

    override val details = OpenSearchDescription(
        "Scryfall",
        "Scryfall MTG Search",
        listOf(
            OpenSearchDescription.Image(32, "https://scryfall.com/favicon.ico?v=2b086a507095", ContentType.Image.XIcon),
            OpenSearchDescription.Image(192, "https://scryfall.com/icon-196.png?v=2b086a507095", ContentType.Image.PNG),
        ),
        tags = listOf("magic-the-gathering")
    )
}
