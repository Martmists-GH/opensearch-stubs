package com.martmists.opensearch.stubs.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale.getDefault

object PokemonDBSearchAPI : TrieBasedOpenSearchAPI("https://pokemondb.net/search?q=") {
    override val path = "/pokemondb"

    override fun collectSearchData(): Flow<OpenSearchSuggestion> = flow {
        val res = client.get("https://pokemondb.net/json/searchdata")
        val content = res.body<Map<String, List<List<String>>>>()
        for ((group, pages) in content) {
            for ((desc, path) in pages) {
                val page = OpenSearchSuggestion(
                    desc, "https://pokemondb.net/$group/$path", "$desc (${
                    group.replace('-', ' ').replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString()
                    }
                }")
                emit(page)
            }
        }
    }

    override val details = OpenSearchDescription(
        "PokemonDB",
        "PokemonDB Search",
        listOf(
            OpenSearchDescription.favicon("https://pokemondb.net/favicon.ico"),
        ),
        longName = "Pokémon Database Search",
        tags = listOf("pokemon")
    )
}
