package com.reinchat.app.net

/**
 * Clipboard / rich text often brings invisible characters into API keys.
 * Normalize harmless lookalikes instead of rejecting an otherwise valid key.
 */
fun normalizeApiKey(raw: String): String {
    val mapped = buildString(raw.length) {
        raw.forEach { ch ->
            when (ch) {
                '\u00A0', '\u2007', '\u202F' -> append(' ')
                '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2015', '\u2212' -> append('-')
                '\uFF3F' -> append('_')
                '\uFF0D' -> append('-')
                '\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF', '\u00AD',
                '\u2028', '\u2029', '\u180E' -> Unit
                else -> append(ch)
            }
        }
    }
    // API keys are opaque ASCII tokens. Whitespace, Chinese characters,
    // full-width punctuation and emoji can only come from a rich-text copy
    // or a stray IME character; they cannot be sent in an HTTP header.
    return mapped.filter { it.code in 33..126 }
}

/** Returns a user-facing problem for characters that cannot be sent in a header. */
fun apiKeyHeaderProblem(key: String): String? {
    val index = key.indexOfFirst { it.code !in 33..126 }
    if (index < 0) return null
    val ch = key[index]
    return "API Key 第 ${index + 1} 个字符不可用于 HTTP 请求头（U+%04X）。请重新复制纯文本 Key，避免全角字符、表情或隐藏符号。"
        .format(ch.code)
}
