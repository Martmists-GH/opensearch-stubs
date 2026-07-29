package com.martmists.opensearch.stubs.api

import com.martmists.opensearch.stubs.util.AdaptiveTrie
import io.ktor.http.encodeURLParameter
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.Flow

abstract class TrieBasedOpenSearchAPI(private val searchUrl: String) : OpenSearchAPI {
    abstract fun collectSearchData(): Flow<OpenSearchSuggestion>

    private val searchData = AdaptiveTrie<OpenSearchSuggestion>(4)
    private val hasSearchData = atomic(false)

    private suspend fun tryCollectSearchData() {
        if (hasSearchData.compareAndSet(expect = false, update = true)) {
            collectSearchData().collect {
                searchData.insert(it.suggestion, it)
            }
        }
    }

    override suspend fun suggest(query: String): List<OpenSearchSuggestion> {
        tryCollectSearchData()
        return searchData.searchByPrefix(query)
    }

    override suspend fun search(query: String): String {
        tryCollectSearchData()

        val found = searchData.searchByPrefix(query)
        if (found.size == 1) {
            return found.first().url
        }

        return searchUrl + query.encodeURLParameter()
    }
}
