package com.martmists.opensearch.stubs.api

import com.martmists.opensearch.stubs.ext.removeHtml
import com.martmists.opensearch.stubs.sphinx.EnglishLanguageData
import com.martmists.opensearch.stubs.sphinx.LanguageData
import com.martmists.opensearch.stubs.util.suspendLazy
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import kotlin.math.round

private typealias ReferenceList = @Serializable(with=IntAsListSerializer::class) List<Int>

private class IntAsListSerializer : JsonTransformingSerializer<List<Int>>(ListSerializer(Int.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement = element as? JsonArray ?: JsonArray(listOf(element))
}

class SphinxDocsSearchAPI(
    override val path: String,
    private val baseUrl: String,
    private val numpyScoring: Boolean = true,
    private val scorer: Scorer = Scorer(),
    private val languages: Map<String, LanguageData> = DEFAULT_LANGUAGES,
    detailsFactory: () -> OpenSearchDescription,
) : OpenSearchAPI {
    data class Scorer(
        val objNameMatch: Int = 11,
        val objPartialMatch: Int = 6,
        val objPrio: Map<Int, Int> = mapOf(
            0 to 15,
            1 to 5,
            2 to -5
        ),
        val objPrioDefault: Int = 0,
        val title: Int = 15,
        val partialTitle: Int = 7,
        val term: Int = 5,
        val partialTerm: Int = 2,
    )

    @Serializable
    data class SearchIndex(
        @SerialName("alltitles")
        val allTitles: Map<String, List<List<JsonPrimitive>>>,
        @SerialName("docnames")
        val docNames: List<String>,
        @SerialName("envversion")
        val envVersion: Map<String, Int>,
        val filenames: List<String>,
        @SerialName("indexentries")
        val indexEntries: Map<String, List<List<JsonPrimitive>>>,
        val objects: Map<String, List<List<JsonPrimitive>>>,
        @SerialName("objnames")
        val objNames: Map<String, List<String>>,
        @SerialName("objtypes")
        val objTypes: Map<String, String>,
        val terms: Map<String, ReferenceList>,
        val titles: List<String>,
        @SerialName("titleterms")
        val titleTerms: Map<String, ReferenceList>,
    )

    data class SearchQuery(
        val query: String,
        val searchTerms: Set<String>,
        val excludedTerms: Set<String>,
        val objectTerms: Set<String>,
    )

    private val searchData = suspendLazy {
        val searchIndexUrl = "$baseUrl/searchindex.js"
        val responseText = client.get(searchIndexUrl).bodyAsText()

        val startIndex = responseText.indexOf('{')
        val endIndex = responseText.lastIndexOf('}')
        val jsonString = responseText.substring(startIndex, endIndex + 1)

        val tmp = Json.decodeFromString<SearchIndex>(jsonString)
        tmp.copy(titles = tmp.titles.map { it.removeHtml() })
    }

    override val details = detailsFactory()

    private val objectPattern = Regex("\\W+")
    private fun parseQuery(query: String, lang: LanguageData): SearchQuery {
        val terms = mutableSetOf<String>()
        val excluded = mutableSetOf<String>()
        val objs = objectPattern.split(query.lowercase()).filter { it.isNotBlank() }.toSet()

        objectPattern.split(query).forEach { queryTerm ->
            val queryTermLower = queryTerm.lowercase()

            if (lang.stopWords.contains(queryTermLower) || queryTerm.all { it.isDigit() }) {
                return@forEach
            }

            val word = lang.stemWord(queryTermLower)
            if (word.startsWith('-')) {
                excluded.add(word.substringAfter('-'))
            } else {
                terms.add(word)
            }
        }

        return SearchQuery(query, terms, excluded, objs)
    }

    private suspend fun performSearch(query: SearchQuery, lang: LanguageData): List<OpenSearchSuggestion> {
        logger.info("performSearch: $query")
        val data = searchData.get()

        val queryLower = query.query.lowercase()
        val normalResults = mutableListOf<Pair<OpenSearchSuggestion, Float>>()
        val nonMainIndexResults = mutableListOf<Pair<OpenSearchSuggestion, Float>>()

        for ((title, foundTitles) in data.allTitles) {
            if (queryLower in title.lowercase().trim() && queryLower.length >= title.length / 2) {
                for ((fileJson, idJson) in foundTitles) {
                    val (file, id) = fileJson.int to idJson.contentOrNull
                    val score = round((if (numpyScoring) 100f else scorer.title.toFloat()) * queryLower.length / title.length)
                    val boost = if (data.titles[file] == title) 1 else 0
                    normalResults.add(OpenSearchSuggestion(
                        if (data.titles[file] != title) "${data.titles[file]} > $title" else title,
                        "$baseUrl/${data.docNames[file]}.html${id?.let { "#$it" } ?: ""}",
                    ) to score + boost)
                }
            }
        }

        for ((entry, foundEntries) in data.indexEntries) {
            if (queryLower in entry && queryLower.length >= entry.length / 2) {
                for ((fileJson, idJson, isMainJson) in foundEntries) {
                    val (file, id, isMain) = Triple(fileJson.int, idJson.contentOrNull, isMainJson.boolean)
                    val score = round(100f * queryLower.length / entry.length)
                    val target = if (isMain) normalResults else nonMainIndexResults
                    target.add(OpenSearchSuggestion(
                        data.titles[file],
                        "$baseUrl/${data.docNames[file]}.html${id?.let { "#$it" } ?: ""}",
                    ) to score)
                }
            }
        }

        logger.info("Results so far: $normalResults")

        for (obj in query.objectTerms) {
            normalResults.addAll(objectSearch(obj, query.objectTerms, lang))
        }

        normalResults.addAll(termsSearch(query.searchTerms, query.excludedTerms))

        val results = if (numpyScoring) {
            (nonMainIndexResults + normalResults).sortedWith(::sortByScoreThenName)
        } else {
            normalResults.sortWith(::sortByScoreThenName)
            nonMainIndexResults.sortWith(::sortByScoreThenName)
            nonMainIndexResults + normalResults
        }

        val seen = mutableSetOf<OpenSearchSuggestion>()
        return results.asReversed().fold(mutableListOf()) { acc, (result, score) ->
            logger.info("${result.suggestion} => $score")
            if (result !in seen) {
                acc.add(result)
                seen.add(result)
            }
            acc
        }
    }

    private fun sortByScoreThenName(a: Pair<OpenSearchSuggestion, Float>, b: Pair<OpenSearchSuggestion, Float>): Int {
        val (a, aScore) = a
        val (b, bScore) = b

        return when {
            aScore == bScore -> {
                val aTitle = a.suggestion.lowercase()
                val bTitle = b.suggestion.lowercase()
                when {
                    aTitle == bTitle -> 0
                    aTitle > bTitle -> -1
                    else -> 1
                }
            }
            aScore > bScore -> 1
            else -> -1
        }
    }

    private suspend fun objectSearch(obj: String, objectTerms: Set<String>, lang: LanguageData): List<Pair<OpenSearchSuggestion, Float>> {
        val data = searchData.get()
        val results = data.objects.flatMap { (prefix, matches) ->
            matches.mapNotNull { match ->
                val name = match[4].content
                val fullName = prefix.takeIf(String::isNotBlank)?.let { "$it.$name" } ?: name
                val fullNameLower = fullName.lowercase()
                if (obj !in fullNameLower) return@mapNotNull null

                var score = 0f
                val parts = fullNameLower.split('.')

                if (fullNameLower == obj || parts.last() == obj) {
                    score += scorer.objNameMatch
                } else if (obj in parts.last()) {
                    score += scorer.objPartialMatch
                }

                val objName = data.objNames[match[1].int.toString()]!![2]
                logger.info(match.toString())
                val title = data.titles[match[0].int]
                logger.info(title)

                val otherTerms = objectTerms - obj
                if (otherTerms.isNotEmpty()) {
                    val haystack = "$prefix $name $objName $title".lowercase()
                    if (otherTerms.any { it in haystack }) return@mapNotNull null
                }

                var anchor = match[3].content
                if (anchor.isBlank()) {
                    anchor = fullName
                } else if (anchor == "-") {
                    anchor = "${data.objNames[match[1].int.toString()]!![1]}-$fullName"
                }
                val descr = lang.containsText(objName, title)
                score += scorer.objPrio[match[2].int] ?: scorer.objPrioDefault

                OpenSearchSuggestion(
                    fullName,
                    "$baseUrl/${data.docNames[match[0].int]}.html#$anchor",
                    "$fullName ($descr)"
                ) to score
            }
        }
        return results
    }

    private suspend fun termsSearch(searchTerms: Set<String>, excludedTerms: Set<String>): List<Pair<OpenSearchSuggestion, Float>> {
        val data = searchData.get()
        val scoreMap = mutableMapOf<Int, MutableMap<String, Float>>()
        val fileMap = mutableMapOf<Int, MutableSet<String>>()

        for (word in searchTerms) {
            val files = mutableListOf<Int>()
            val arr = mutableListOf(
                data.terms[word] to scorer.term,
                data.titleTerms[word] to scorer.title,
            )

            if (word.length > 2) {
                if (word !in data.terms) {
                    for ((term, files) in data.terms) {
                        if (word in term) {
                            arr.add(files to scorer.partialTerm)
                        }
                    }
                }

                if (word !in data.titleTerms) {
                    for ((term, files) in data.titleTerms) {
                        if (word in term) {
                            arr.add(files to scorer.partialTitle)
                        }
                    }
                }
            }

            if (arr.all { it.first == null }) continue

            for (record in arr) {
                val recordFiles = record.first ?: continue
                if (recordFiles.isEmpty()) continue
                files.addAll(recordFiles)

                for (file in recordFiles) {
                    scoreMap.getOrPut(file, ::mutableMapOf)[word] = record.second.toFloat()
                }
            }

            for (file in files) {
                fileMap.getOrPut(file, ::mutableSetOf).add(word)
            }
        }

        val results = mutableListOf<Pair<OpenSearchSuggestion, Float>>()
        for ((file, wordList) in fileMap) {
            val filtered = searchTerms.filter { it.length > 2 }
            if (wordList.size != searchTerms.size && wordList.size != filtered.size) continue

            if (excludedTerms.any { term ->
                file in (data.terms[term] ?: emptyList()) ||
                file in (data.titleTerms[term] ?: emptyList())
            }) continue

            val score = wordList.maxOf { scoreMap[file]!![it]!! }
            results.add(OpenSearchSuggestion(
                data.titles[file],
                "$baseUrl/${data.docNames[file]}.html",
            ) to score)
        }
        return results
    }

    override suspend fun suggest(query: String, language: String): List<OpenSearchSuggestion> {
        val lang = languages[language] ?: EnglishLanguageData
        val q = parseQuery(query, lang)
        val res = performSearch(q, lang)

        return res
    }

    override suspend fun search(query: String, language: String): String {
        val lang = languages[language] ?: EnglishLanguageData
        val q = parseQuery(query, lang)
        val res = performSearch(q, lang)

        if (res.size == 1) {
            return res.first().url
        }

        return "$baseUrl/search.html?q=" + query.encodeURLParameter()
    }

    companion object {
        val DEFAULT_LANGUAGES = mutableMapOf(
            "en" to EnglishLanguageData
        )

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
        val NUMPY = SphinxDocsSearchAPI("/numpy", "https://numpy.org/doc/stable", numpyScoring = true) {
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
