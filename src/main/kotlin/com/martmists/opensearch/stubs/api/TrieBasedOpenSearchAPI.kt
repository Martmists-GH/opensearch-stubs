package com.martmists.opensearch.stubs.api

import com.martmists.opensearch.stubs.util.AdaptiveTrie
import com.martmists.opensearch.stubs.util.suspendLazy
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.Flow

abstract class TrieBasedOpenSearchAPI(private val searchUrl: String) : OpenSearchAPI {
    abstract fun collectSearchData(): Flow<OpenSearchSuggestion>

    private val searchData = suspendLazy {
        val out = AdaptiveTrie<OpenSearchSuggestion>(4)
        collectSearchData().collect {
            out.insert(it.suggestion, it)
        }
        out
    }

    override suspend fun suggest(query: String, language: String): List<OpenSearchSuggestion> {
        return searchData.get().searchByPrefix(query)
    }

    override suspend fun search(query: String, language: String): String {
        val found = searchData.get().searchByPrefix(query)
        if (found.size == 1) {
            return found.first().url
        }

        return searchUrl + query.encodeURLParameter()
    }
}
