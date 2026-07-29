package com.martmists.opensearch.stubs

import com.martmists.opensearch.stubs.api.AlgoliaSearchAPI
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
                        call.respondText(provider.description, ContentType.Application.Xml)
                    }
                }
            }
        }
    }.start(wait = true)
}
