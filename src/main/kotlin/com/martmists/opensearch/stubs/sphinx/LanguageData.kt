package com.martmists.opensearch.stubs.sphinx

abstract class LanguageData {
    abstract val stopWords: Set<String>
    abstract fun stemWord(word: String): String
    abstract fun containsText(objName: String, title: String): String
}

