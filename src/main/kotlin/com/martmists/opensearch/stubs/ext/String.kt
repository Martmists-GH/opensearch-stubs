package com.martmists.opensearch.stubs.ext

private val NODE_TAGS = arrayOf(
    "code",
    "span",
).map {
    Regex("<$it[^>]+?>(.+?)</$it>")
}

fun String.removeHtml(): String {
    var res = this
    do {
        val last = res
        for (pattern in NODE_TAGS) {
            res = pattern.replace(res, "$1")
        }
    } while (last != res)
    return res
}
