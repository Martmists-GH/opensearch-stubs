package com.martmists.opensearch.stubs.api

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import kotlin.collections.iterator

class SphinxDocsSearchAPI(
    override val path: String,
    private val baseUrl: String,
    detailsFactory: () -> OpenSearchDescription,
) : TrieBasedOpenSearchAPI("${baseUrl}/search.html?q=") {
    override val details = detailsFactory()

    override fun collectSearchData(): Flow<OpenSearchSuggestion> = flow {
        val searchIndexUrl = "$baseUrl/searchindex.js"
        val responseText = client.get(searchIndexUrl).bodyAsText()

        val jsonString = extractJsonFromSearchIndex(responseText) ?: return@flow
        val root = Json.parseToJsonElement(jsonString).jsonObject

        val docnames = root["docnames"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val titles = root["titles"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val terms = root["terms"]?.jsonObject ?: JsonObject(emptyMap())

        for ((term, docReferences) in terms) {
            val matchedDocIndices = when (docReferences) {
                is JsonArray -> {
                    docReferences.mapNotNull { it.jsonPrimitive.content.toIntOrNull() }
                }

                is JsonObject -> {
                    docReferences.keys.mapNotNull { it.toIntOrNull() }
                }

                else -> emptyList()
            }

            for (docIdx in matchedDocIndices) {
                if (docIdx in docnames.indices) {
                    val docPath = docnames[docIdx]
                    val pageTitle = titles.getOrNull(docIdx) ?: docPath
                    val url = "$baseUrl/$docPath.html"

                    emit(OpenSearchSuggestion(term, url, pageTitle))
                }
            }
        }

        for ((idx, title) in titles.withIndex()) {
            if (idx in docnames.indices) {
                val docPath = docnames[idx]
                val url = "$baseUrl/$docPath.html"

                emit(OpenSearchSuggestion(title, url, title))
            }
        }
    }

    private fun extractJsonFromSearchIndex(jsContent: String): String? {
        val startIndex = jsContent.indexOf('{')
        val endIndex = jsContent.lastIndexOf('}')
        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            return null
        }
        return jsContent.substring(startIndex, endIndex + 1)
    }

    companion object {
        val PYTHON = SphinxDocsSearchAPI("/python", "https://docs.python.org/3") {
            OpenSearchDescription(
                "Python 3",
                "Python 3 Documentation",
                listOf(
                    OpenSearchDescription.Image(16, "https://docs.python.org/3/_static/py.svg", ContentType.Image.PNG)  // NOTE: This is how python.org specifies it?
                ),
                tags = listOf("python", "official")
            )
        }
        val NUMPY = SphinxDocsSearchAPI("/numpy", "https://numpy.org/doc/stable/") {
            OpenSearchDescription(
                "NumPy",
                "NumPy Documentation",
                listOf(
                    OpenSearchDescription.Image(48, "https://numpy.org/doc/stable/_static/favicon.ico", ContentType.Image.XIcon)
                ),
                tags = listOf("python")
            )
        }
        val SCIPY = SphinxDocsSearchAPI("/scipy", "https://docs.scipy.org/doc/scipy") {
            OpenSearchDescription(
                "SciPy",
                "SciPy Documentation",
                listOf(
                    OpenSearchDescription.Image(64, "https://docs.scipy.org/doc/scipy/_static/favicon.ico", ContentType.Image.XIcon)
                ),
                tags = listOf("python")
            )
        }
    }
}
