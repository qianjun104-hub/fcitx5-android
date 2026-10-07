/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input.voice

import java.util.UUID

/** A conservative rejection gate, not proof that a language model preserved meaning. */
object ConservativeCleanup {
    private val number = "[<>≤≥＋+\\-−]?\\s*\\d+(?:[.．]\\d+)?(?:\\s*[/～~\\-—至]\\s*\\d+(?:[.．]\\d+)?)?"
    private val latin = "[A-Za-z][A-Za-z0-9._+\\-/]*"
    private val chineseNumber = "[零〇一二两三四五六七八九十百千万亿]+"
    private val qualifiers = listOf(
        "左侧", "右侧", "双侧", "左", "右", "双", "阴性", "阳性", "阴", "阳",
        "没有", "未见", "否认", "不能", "不要", "不", "未", "无", "非",
        "可能", "疑似", "考虑", "排除", "拟", "既往", "目前", "曾", "术前", "术后"
    )

    data class ProtectedText(val source: String, val masked: String, val tokens: List<Pair<String, String>>)

    fun protect(source: String, glossary: String): ProtectedText {
        val terms = glossary.split(',', '，', ';', '；', '\n')
            .map(String::trim).filter(String::isNotEmpty).filter { it.length <= 80 }.take(200)
        val fixed = (terms + qualifiers).distinct().sortedByDescending(String::length).map(Regex::escape)
        val matcher = Regex((fixed + number + latin + chineseNumber).joinToString("|"))
        val nonce = UUID.randomUUID().toString().take(8)
        val tokens = mutableListOf<Pair<String, String>>()
        val masked = matcher.replace(source) { match ->
            val marker = "⟦V_${nonce}_${tokens.size}⟧"
            tokens.add(marker to match.value)
            marker
        }
        return ProtectedText(source, masked, tokens)
    }

    fun restoreIfSafe(protected: ProtectedText, response: String): String? {
        val candidate = response.trim()
        if (candidate.isBlank()) return null
        val returnedTokens = Regex("⟦V_[^⟧]+⟧").findAll(candidate).map { it.value }.toList()
        if (returnedTokens != protected.tokens.map { it.first }) return null
        var restored = candidate
        for ((marker, original) in protected.tokens) restored = restored.replace(marker, original)
        val originalLetters = letters(protected.source)
        val cleanedLetters = letters(restored)
        if (cleanedLetters.isEmpty()) return null
        if (cleanedLetters.length < originalLetters.length * 0.45) return null
        // Only punctuation, paragraphing and deletions are allowed; reject added words,
        // reordered clauses, answers and fabricated medical explanations.
        var at = 0
        for (ch in cleanedLetters) {
            at = originalLetters.indexOf(ch, at)
            if (at < 0) return null
            at++
        }
        // Compare critical facts again after unmasking, including newly added tokens.
        val critical = Regex(listOf(number, latin, chineseNumber, qualifiers.sortedByDescending(String::length).joinToString("|", transform = Regex::escape)).joinToString("|"))
        fun facts(text: String) = critical.findAll(text).map { it.value.filterNot(Char::isWhitespace) }.toList()
        if (facts(protected.source) != facts(restored)) return null
        return restored
    }

    private fun letters(text: String) = text.filter { it.isLetterOrDigit() }
}
