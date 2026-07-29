package com.martmists.opensearch.stubs.sphinx

object EnglishLanguageData : LanguageData() {
    override val stopWords = setOf("a", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into", "is", "it", "near", "no", "not", "of", "on", "or", "such", "that", "the", "their", "then", "there", "these", "they", "this", "to", "was", "will", "with")

    private val step2List = mapOf(
        "ational" to "ate",
        "tional" to "tion",
        "enci" to "ence",
        "anci" to "ance",
        "izer" to "ize",
        "bli" to "ble",
        "alli" to "al",
        "entli" to "ent",
        "eli" to "e",
        "ousli" to "ous",
        "ization" to "ize",
        "ation" to "ate",
        "ator" to "ate",
        "alism" to "al",
        "iveness" to "ive",
        "fulness" to "ful",
        "ousness" to "ous",
        "aliti" to "al",
        "iviti" to "ive",
        "biliti" to "ble",
        "logi" to "log"
    )

    private val step3List = mapOf(
        "icate" to "ic",
        "ative" to "",
        "alize" to "al",
        "iciti" to "ic",
        "ical" to "ic",
        "ful" to "",
        "ness" to ""
    )

    // Regex building blocks
    private const val C_CHAR = "[^aeiou]"
    private const val V_CHAR = "[aeiouy]"
    private const val C_SEQ = "$C_CHAR[^aeiouy]*"
    private const val V_SEQ = "$V_CHAR[aeiou]*"

    // Measure rules
    private val mgr0 = Regex("^($C_SEQ)?$V_SEQ$C_SEQ")
    private val meq1 = Regex("^($C_SEQ)?$V_SEQ$C_SEQ($V_SEQ)?$")
    private val mgr1 = Regex("^($C_SEQ)?$V_SEQ$C_SEQ$V_SEQ$C_SEQ")
    private val sV = Regex("^($C_SEQ)?$V_CHAR")
    private val reCVC = Regex("^$C_SEQ$V_CHAR[^aeiouwxy]$")

    // Suffix matching patterns
    private val step1a1 = Regex("^(.+?)(ss|i)es$")
    private val step1a2 = Regex("^(.+?)([^s])s$")

    private val step1b1 = Regex("^(.+?)eed$")
    private val step1b2 = Regex("^(.+?)(ed|ing)$")
    private val step1bSuffixAtBlIz = Regex("(at|bl|iz)$")
    private val step1bDoubleConsonant = Regex("([^aeiouylsz])\\1$")

    private val step1c = Regex("^(.+?)y$")

    private val step2Regex = Regex(
        "^(.+?)(ational|tional|enci|anci|izer|bli|alli|entli|eli|ousli|ization|ation|ator|alism|iveness|fulness|ousness|aliti|iviti|biliti|logi)$"
    )

    private val step3Regex = Regex("^(.+?)(icate|ative|alize|iciti|ical|ful|ness)$")

    private val step4Regex1 = Regex("^(.+?)(al|ance|ence|er|ic|able|ible|ant|ement|ment|ent|ou|ism|ate|iti|ous|ive|ize)$")
    private val step4Regex2 = Regex("^(.+?)(s|t)(ion)$")

    private val step5EndingE = Regex("^(.+?)e$")
    private val step5EndingLL = Regex("ll$")

    override fun stemWord(word: String): String {
        if (word.length < 3) return word

        val firstChar = word[0]

        var w = if (firstChar == 'y') {
            firstChar.uppercaseChar() + word.substring(1)
        } else {
            word
        }

        (step1a1.matchEntire(w) ?: step1a2.matchEntire(w))?.let { match ->
            val stem = match.groupValues[1]
            val suffix = match.groupValues[2]
            w = stem + suffix
        }

        var step1bMatched = false

        step1b1.matchEntire(w)?.let { match ->
            step1bMatched = true
            val stem = match.groupValues[1]
            if (mgr0.containsMatchIn(stem)) {
                w = w.dropLast(1)
            }
        }

        if (!step1bMatched) {
            step1b2.matchEntire(w)?.let { match ->
                val stem = match.groupValues[1]
                if (sV.containsMatchIn(stem)) {
                    w = stem
                    if (step1bSuffixAtBlIz.containsMatchIn(w)) {
                        w += "e"
                    } else if (step1bDoubleConsonant.containsMatchIn(w)) {
                        w = w.dropLast(1)
                    } else if (reCVC.containsMatchIn(w)) {
                        w += "e"
                    }
                }
            }
        }

        step1c.matchEntire(w)?.let { match ->
            val stem = match.groupValues[1]
            if (sV.containsMatchIn(stem)) {
                w = "${stem}i"
            }
        }

        step2Regex.matchEntire(w)?.let { match ->
            val stem = match.groupValues[1]
            val suffix = match.groupValues[2]
            if (mgr0.containsMatchIn(stem)) {
                w = stem + step2List[suffix]
            }
        }

        step3Regex.matchEntire(w)?.let { match ->
            val stem = match.groupValues[1]
            val suffix = match.groupValues[2]
            if (mgr0.containsMatchIn(stem)) {
                w = stem + step3List[suffix]
            }
        }

        var step4Matched = false
        step4Regex1.matchEntire(w)?.let { match ->
            step4Matched = true
            val stem = match.groupValues[1]
            if (mgr1.containsMatchIn(stem)) {
                w = stem
            }
        }

        if (!step4Matched) {
            step4Regex2.matchEntire(w)?.let { match ->
                val stem = match.groupValues[1] + match.groupValues[2]
                if (mgr1.containsMatchIn(stem)) {
                    w = stem
                }
            }
        }

        step5EndingE.matchEntire(w)?.let { match ->
            val stem = match.groupValues[1]
            if (mgr1.containsMatchIn(stem) || (meq1.matches(stem) && !reCVC.containsMatchIn(stem))) {
                w = stem
            }
        }

        if (step5EndingLL.containsMatchIn(w) && mgr1.containsMatchIn(w)) {
            w = w.dropLast(1)
        }

        if (firstChar == 'y') {
            w = firstChar.lowercaseChar() + w.substring(1)
        }

        return w
    }

    override fun containsText(objName: String, title: String) = "$objName, in $title"
}
