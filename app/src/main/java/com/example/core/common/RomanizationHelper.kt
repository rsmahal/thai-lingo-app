package com.example.core.common

import com.example.domain.Vocabulary

fun getRomanizedText(text: String, vocabulary: List<Vocabulary>): String {
    if (text.isEmpty()) return ""
    if (!text.any { it in '\u0E00'..'\u0E7F' }) return text
    
    val clean = text.trim()
    val directMatch = vocabulary.firstOrNull { it.thai.trim() == clean }
    if (directMatch != null) return directMatch.romanization
    
    val tokens = text.split(Regex("(?<=[\\s|])|(?=[\\s|])"))
    val mappedTokens = tokens.map { token ->
        if (token.any { it in '\u0E00'..'\u0E7F' }) {
            val tokenMatch = vocabulary.firstOrNull { it.thai.trim() == token.trim() }
            tokenMatch?.romanization ?: token
        } else {
            token
        }
    }
    return mappedTokens.joinToString("").replace("|", " ")
}
