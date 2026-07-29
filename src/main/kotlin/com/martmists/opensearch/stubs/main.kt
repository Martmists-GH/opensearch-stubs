package com.martmists.opensearch.stubs

import com.martmists.opensearch.stubs.api.AlgoliaSearchAPI
import com.martmists.opensearch.stubs.api.OpenSearchAPI
import com.martmists.opensearch.stubs.api.PokemonDBSearchAPI
import com.martmists.opensearch.stubs.api.ScryfallSearchAPI
import com.martmists.opensearch.stubs.api.SphinxDocsSearchAPI
import com.martmists.opensearch.stubs.api.SteamSearchAPI
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.*
import io.ktor.server.cio.CIO
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.identity
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.path
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlin.collections.emptyList

@Serializable
private data class ProviderDetails(
    val search: String,
    val suggestions: String,
    val description: String,
) {
    companion object {
        fun make(path: String) = ProviderDetails("$path/search", "$path/suggest", "$path/opensearch.xml")
    }
}

private val descriptionCache = mutableMapOf<OpenSearchAPI, String>()

@OptIn(ExperimentalSerializationApi::class)
fun main() {
    val providers = listOf(
        PokemonDBSearchAPI,
        ScryfallSearchAPI,
        SteamSearchAPI,
        SphinxDocsSearchAPI.PYTHON,
        SphinxDocsSearchAPI.NUMPY,
        SphinxDocsSearchAPI.SCIPY,
        AlgoliaSearchAPI.KOTLIN,
        AlgoliaSearchAPI.EXPOSED,
        AlgoliaSearchAPI.KTOR,
        AlgoliaSearchAPI.COMPOSE_MULTIPLATFORM,
    )

    embeddedServer(CIO, port = 29010, host = "0.0.0.0") {
        install(Compression) {
            gzip()
            deflate()
            identity {
                priority = 0.01
            }
        }

        install(ContentNegotiation) {
            json()
        }

        routing {
            get("/") {
                call.respond(providers.map { ProviderDetails.make(it.path) })
            }

            for (provider in providers) {
                route(provider.path) {
                    get("/search") {
                        val query = call.parameters["q"] ?: ""
                        val outUrl = provider.search(query)
                        call.respondRedirect(outUrl)
                    }

                    get("/suggest") {
                        val query = call.parameters["q"] ?: return@get call.respond(emptyList<Int>())
                        val suggestions = provider.suggest(query).take(10)
                        call.respond(buildJsonArray {
                            add(JsonPrimitive(query))
                            addJsonArray {
                                addAll(suggestions.map { JsonPrimitive(it.suggestion) })
                            }
                            addJsonArray {
                                addAll(suggestions.map { JsonPrimitive(it.description) })
                            }
                            addJsonArray {
                                addAll(suggestions.map { JsonPrimitive(it.url) })
                            }
                        })
                    }

                    get("opensearch.xml") {
                        val desc = descriptionCache.getOrPut(provider) {
                            val details = provider.details
                            val tagString = details.tags.joinToString(" ")

                            require(details.shortName.length in 1..16)
                            require(details.description.length in 1..16)
                            require(tagString.length in 0..256)
                            require((details.longName?.length ?: 1) in 1..256)

                            buildString {
                                appendLine("<?xml version=\"1.0\"?>")
                                appendLine("<OpenSearchDescription xmlns=\"http://a9.com/-/spec/opensearch/1.1/\" xmlns:moz=\"https://www.mozilla.org/2006/browser/search/\">")
                                appendLine("  <ShortName>${details.shortName}</ShortName>")
                                appendLine("  <Description>${details.description}</Description>")
                                if (details.tags.isNotEmpty()) {
                                    appendLine("  <Tags>$tagString</Tags>")
                                }
                                appendLine("  <Contact>mail@martmists.com</Contact>")
                                appendLine("  <Url type=\"text/html\" rel=\"results\" template=\"https://opensearch.martmists.com/${provider.path}/search?q={searchTerms}\" />")
                                appendLine("  <Url type=\"application/x-suggestions+json\" rel=\"suggestions\" template=\"https://opensearch.martmists.com/${provider.path}/suggest?q={searchTerms}\" />")
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

                        call.respondText(desc, ContentType.Application.Xml)
                    }
                }
            }
        }
    }.start(wait = true)
}
