package org.litellmkt.onnx

import kotlinx.serialization.json.Json

/**
 * Byte-level BPE tokenizer compatible with HuggingFace tokenizer.json format.
 *
 * Supports the encoding used by GPT-2, Phi-3, Mistral, and LLaMA family models.
 * Special tokens (e.g. <|system|>, <|end|>, <s>, </s>) are handled before BPE.
 */
internal class BpeTokenizer(tokenizerJsonContent: String) {

    private val json = Json { ignoreUnknownKeys = true }

    private val config: TokenizerConfig =
        json.decodeFromString(tokenizerJsonContent)

    private val vocab: Map<String, Int> = config.model.vocab
    private val reverseVocab: Map<Int, String> = vocab.entries.associate { (k, v) -> v to k }

    private val mergeRanks: Map<Pair<String, String>, Int> =
        config.model.merges.mapIndexed { index, rule ->
            val sep = rule.indexOf(' ')
            require(sep > 0) { "Malformed merge rule: $rule" }
            Pair(rule.substring(0, sep), rule.substring(sep + 1)) to index
        }.toMap()

    private val addedTokensById: Map<Int, String> =
        config.addedTokens.associate { it.id to it.content }

    private val addedTokensByContent: Map<String, Int> =
        config.addedTokens.associate { it.content to it.id }

    // GPT-2 byte-to-unicode encoder: maps every byte 0-255 to a printable char
    private val byteEncoder: Map<Int, Char> = buildByteEncoder()
    private val byteDecoder: Map<Char, Int> = byteEncoder.entries.associate { (k, v) -> v to k }

    // Common EOS token names across model families
    val eosTokenId: Int? by lazy {
        listOf("<|endoftext|>", "</s>", "<|end|>", "<eos>", "[EOS]", "<|im_end|>")
            .firstNotNullOfOrNull { addedTokensByContent[it] ?: vocab[it] }
    }

    // GPT-2 style pre-tokenizer (handles contractions, words, numbers, punctuation)
    private val preTokenRegex = Regex(
        """'(?i:[sdmt]|ll|ve|re)|[^\r\n\p{L}\p{N}]?\p{L}+|\p{N}{1,3}| ?[^\s\p{L}\p{N}]+[\r\n]*|\s*[\r\n]+|\s+(?!\S)|\s+"""
    )

    // Sorted longest-first to correctly match multi-char special tokens
    private val specialTokenRegex: Regex? =
        if (addedTokensByContent.isEmpty()) null
        else Regex(
            addedTokensByContent.keys
                .sortedByDescending { it.length }
                .joinToString("|") { Regex.escape(it) }
        )

    fun encode(text: String, addBos: Boolean = false): List<Int> {
        val result = mutableListOf<Int>()
        if (addBos) {
            (addedTokensByContent["<s>"] ?: vocab["<s>"])?.let { result.add(it) }
        }
        result.addAll(encodeWithSpecialTokens(text))
        return result
    }

    private fun encodeWithSpecialTokens(text: String): List<Int> {
        val regex = specialTokenRegex ?: return encodeText(text)
        val result = mutableListOf<Int>()
        var cursor = 0
        for (match in regex.findAll(text)) {
            if (match.range.first > cursor) {
                result.addAll(encodeText(text.substring(cursor, match.range.first)))
            }
            addedTokensByContent[match.value]?.let { result.add(it) }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            result.addAll(encodeText(text.substring(cursor)))
        }
        return result
    }

    private fun encodeText(text: String): List<Int> {
        val result = mutableListOf<Int>()
        for (match in preTokenRegex.findAll(text)) {
            val byteChars = match.value.toByteArray(Charsets.UTF_8)
                .map { byteEncoder[it.toInt() and 0xFF]?.toString()   }
                .filterNotNull()
            /*val byteChars = match.value.toByteArray(Charsets.UTF_8)
                .mapNotNull { byteEncoder[it.toInt() and 0xFF]?.toString() }*/
            result.addAll(applyBpe(byteChars).mapNotNull { vocab[it] })
        }
        return result
    }

    private fun applyBpe(tokens: List<String>): List<String> {
        if (tokens.size <= 1) return tokens
        var current = tokens.toMutableList()
        while (current.size > 1) {
            var bestIdx = -1
            var bestRank = Int.MAX_VALUE
            for (i in 0 until current.size - 1) {
                val rank = mergeRanks[Pair(current[i], current[i + 1])] ?: continue
                if (rank < bestRank) { bestRank = rank; bestIdx = i }
            }
            if (bestIdx < 0) break
            val merged = current[bestIdx] + current[bestIdx + 1]
            current = (current.subList(0, bestIdx) + merged + current.subList(bestIdx + 2, current.size)).toMutableList()
        }
        return current
    }

    /**
     * Decodes a single token id to its text representation.
     * Returns empty string for special tokens (they are control tokens, not text).
     */
    fun decodeToken(id: Int): String {
        if (id in addedTokensById) return ""
        val token = reverseVocab[id] ?: return ""
        val bytes = token.mapNotNull { byteDecoder[it]?.toByte() }.toByteArray()
        return String(bytes, Charsets.UTF_8)
    }

    /** Decodes a list of token ids to a single string. */
    fun decode(ids: List<Int>): String {
        val byteBuffer = mutableListOf<Byte>()
        val result = StringBuilder()
        for (id in ids) {
            if (id in addedTokensById) {
                flushBytes(byteBuffer, result)
                continue
            }
            val token = reverseVocab[id] ?: continue
            token.mapNotNullTo(byteBuffer) { byteDecoder[it]?.toByte() }
        }
        flushBytes(byteBuffer, result)
        return result.toString()
    }

    private fun flushBytes(buf: MutableList<Byte>, out: StringBuilder) {
        if (buf.isNotEmpty()) {
            out.append(String(buf.toByteArray(), Charsets.UTF_8))
            buf.clear()
        }
    }

    private fun buildByteEncoder(): Map<Int, Char> {
        val bs = mutableListOf<Int>()
        val cs = mutableListOf<Int>()
        // Printable ASCII
        for (i in '!'.code..'~'.code) { bs.add(i); cs.add(i) }
        // Extended Latin printable range
        for (i in '¡'.code..'¬'.code) { bs.add(i); cs.add(i) }
        for (i in '®'.code..'ÿ'.code) { bs.add(i); cs.add(i) }
        // Map remaining bytes to unused unicode codepoints starting at 256
        var n = 0
        for (b in 0..255) {
            if (b !in bs) { bs.add(b); cs.add(256 + n); n++ }
        }
        return bs.zip(cs.map { it.toChar() }).toMap()
    }
}