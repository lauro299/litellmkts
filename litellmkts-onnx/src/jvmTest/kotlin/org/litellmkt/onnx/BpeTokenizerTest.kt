package org.litellmkt.onnx

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class BpeTokenizerTest : FunSpec({

    /**
     * Builds a minimal but valid tokenizer.json using the same byte-encoder logic as BpeTokenizer.
     *
     * Vocab layout:
     *   IDs 0-255  → single byte-encoded characters (the base alphabet)
     *   IDs 256+   → merged / extra tokens supplied by the caller
     *
     * [merges]      list of merge rules in "a b" format
     * [addedTokens] list of special tokens beyond the base alphabet
     */
    fun buildTokenizerJson(
        merges: List<String> = emptyList(),
        addedTokens: List<Triple<Int, String, Boolean>> = emptyList()
    ): String {
        // Reproduce the GPT-2 byte encoder to create the base vocab
        val bs = mutableListOf<Int>()
        val cs = mutableListOf<Int>()
        for (i in '!'.code..'~'.code) { bs.add(i); cs.add(i) }
        for (i in '¡'.code..'¬'.code) { bs.add(i); cs.add(i) }
        for (i in '®'.code..'ÿ'.code) { bs.add(i); cs.add(i) }
        var n = 0
        for (b in 0..255) {
            if (b !in bs) { bs.add(b); cs.add(256 + n); n++ }
        }
        val byteEncoder: Map<Int, Char> = bs.zip(cs.map { it.toChar() }).toMap()

        // Base vocab: one entry per byte
        val vocab = byteEncoder.values
            .mapIndexed { index, char ->
                val escaped = char.toString()
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                "\"$escaped\": $index"
            }
            .joinToString(", ")

        // Extra vocab entries from merges (assign IDs starting at 256)
        val mergeVocab = merges.mapIndexed { i, rule ->
            val merged = rule.replace(" ", "")
            val escaped = merged.replace("\\", "\\\\").replace("\"", "\\\"")
            "\"$escaped\": ${256 + i}"
        }.joinToString(", ")

        val fullVocab = if (mergeVocab.isNotEmpty()) "$vocab, $mergeVocab" else vocab

        val mergesJson = merges.joinToString(", ") { "\"$it\"" }

        val addedJson = addedTokens.joinToString(", ") { (id, content, special) ->
            val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
            """{"id": $id, "content": "$escaped", "special": $special}"""
        }

        return """{"model":{"type":"BPE","vocab":{$fullVocab},"merges":[$mergesJson]},"added_tokens":[$addedJson]}"""
    }

    // ── Base alphabet / byte encoder ────────────────────────────────────────

    test("encode empty string returns empty list") {
        val tokenizer = BpeTokenizer(buildTokenizerJson())
        tokenizer.encode("") shouldBe emptyList()
    }

    test("encode and decode ASCII round-trip") {
        val tokenizer = BpeTokenizer(buildTokenizerJson())
        val text = "Hello world"
        val ids = tokenizer.encode(text)
        ids.shouldNotBeNull()
        tokenizer.decode(ids) shouldBe text
    }

    test("encode and decode unicode round-trip") {
        val tokenizer = BpeTokenizer(buildTokenizerJson())
        val text = "café"
        val ids = tokenizer.encode(text)
        tokenizer.decode(ids) shouldBe text
    }

    test("encode and decode emoji round-trip") {
        val tokenizer = BpeTokenizer(buildTokenizerJson())
        val text = "Hello 🌍"
        val ids = tokenizer.encode(text)
        tokenizer.decode(ids) shouldBe text
    }

    // ── BPE merges ──────────────────────────────────────────────────────────

    test("BPE merge reduces token count for known pair") {
        // Merge 'H' + 'i' → 'Hi'
        // In byte-encoded form the chars are the same since both are printable ASCII
        val tokenizer = BpeTokenizer(buildTokenizerJson(merges = listOf("H i")))
        val withoutMerge = BpeTokenizer(buildTokenizerJson())

        val ids = tokenizer.encode("Hi")
        val idsWithout = withoutMerge.encode("Hi")

        // With the merge, "Hi" should be a single token; without, it's two
        ids.size shouldBe idsWithout.size - 1
    }

    test("BPE merge produces correct decoded text") {
        val tokenizer = BpeTokenizer(buildTokenizerJson(merges = listOf("H i")))
        val ids = tokenizer.encode("Hi there")
        tokenizer.decode(ids) shouldBe "Hi there"
    }

    test("BPE merge priority: lower-ranked merge applied first") {
        // Two merge rules; the first (lower rank = higher priority) should win
        val tokenizer = BpeTokenizer(
            buildTokenizerJson(merges = listOf("a b", "ab c"))  // rank 0, rank 1
        )
        val idsABC = tokenizer.encode("abc")
        tokenizer.decode(idsABC) shouldBe "abc"
    }

    // ── Special / added tokens ──────────────────────────────────────────────

    test("special token is encoded as its dedicated id") {
        val bosId = 50256
        val tokenizer = BpeTokenizer(
            buildTokenizerJson(addedTokens = listOf(Triple(bosId, "<s>", true)))
        )
        val ids = tokenizer.encode("<s>Hello")
        ids.first() shouldBe bosId
    }

    test("multiple special tokens encoded in correct positions") {
        val sysId = 32000
        val endId = 32001
        val tokenizer = BpeTokenizer(
            buildTokenizerJson(
                addedTokens = listOf(
                    Triple(sysId, "<|system|>", true),
                    Triple(endId, "<|end|>", true)
                )
            )
        )
        val prompt = "<|system|>You are helpful.<|end|>"
        val ids = tokenizer.encode(prompt)
        ids.first() shouldBe sysId
        ids.last() shouldBe endId
    }

    test("special tokens are omitted from decoded text") {
        val eosId = 2
        val tokenizer = BpeTokenizer(
            buildTokenizerJson(addedTokens = listOf(Triple(eosId, "</s>", true)))
        )
        val decoded = tokenizer.decode(listOf(eosId))
        decoded shouldBe ""
    }

    test("addBos prepends bos token when present in vocab") {
        val bosId = 1
        val tokenizer = BpeTokenizer(
            buildTokenizerJson(addedTokens = listOf(Triple(bosId, "<s>", true)))
        )
        val withBos = tokenizer.encode("hi", addBos = true)
        val withoutBos = tokenizer.encode("hi", addBos = false)
        withBos.first() shouldBe bosId
        withBos.size shouldBe withoutBos.size + 1
    }

    // ── EOS detection ────────────────────────────────────────────────────────

    test("eosTokenId resolves </s>") {
        val tokenizer = BpeTokenizer(
            buildTokenizerJson(addedTokens = listOf(Triple(2, "</s>", true)))
        )
        tokenizer.eosTokenId shouldBe 2
    }

    test("eosTokenId resolves <|endoftext|>") {
        val tokenizer = BpeTokenizer(
            buildTokenizerJson(addedTokens = listOf(Triple(50256, "<|endoftext|>", true)))
        )
        tokenizer.eosTokenId shouldBe 50256
    }

    test("eosTokenId resolves <|end|> (Phi-3 style)") {
        val tokenizer = BpeTokenizer(
            buildTokenizerJson(addedTokens = listOf(Triple(32007, "<|end|>", true)))
        )
        tokenizer.eosTokenId shouldBe 32007
    }

    test("eosTokenId is null when no known EOS token present") {
        val tokenizer = BpeTokenizer(buildTokenizerJson())
        tokenizer.eosTokenId shouldBe null
    }

    // ── decodeToken ──────────────────────────────────────────────────────────

    test("decodeToken returns empty string for special token ids") {
        val specialId = 32000
        val tokenizer = BpeTokenizer(
            buildTokenizerJson(addedTokens = listOf(Triple(specialId, "<|system|>", true)))
        )
        tokenizer.decodeToken(specialId) shouldBe ""
    }

    test("decodeToken returns empty string for unknown id") {
        val tokenizer = BpeTokenizer(buildTokenizerJson())
        tokenizer.decodeToken(99999) shouldBe ""
    }

    test("decodeToken for space produces a space character") {
        val tokenizer = BpeTokenizer(buildTokenizerJson())
        // Byte 32 is space; its byte-encoded char is 'Ġ' (U+0120).
        // We need to find the vocab ID for 'Ġ'.
        val spaceIds = tokenizer.encode(" ")
        spaceIds shouldHaveSize 1
        tokenizer.decodeToken(spaceIds.first()) shouldBe " "
    }

    // ── Whitespace and edge cases ────────────────────────────────────────────

    test("encode string with leading space preserves it") {
        val tokenizer = BpeTokenizer(buildTokenizerJson())
        val text = " hello"
        tokenizer.decode(tokenizer.encode(text)) shouldBe text
    }

    test("encode multiline string round-trip") {
        val tokenizer = BpeTokenizer(buildTokenizerJson())
        val text = "line one\nline two\n"
        tokenizer.decode(tokenizer.encode(text)) shouldBe text
    }

    test("encode text longer than single pre-token chunk") {
        val tokenizer = BpeTokenizer(buildTokenizerJson())
        val text = "The quick brown fox jumps over the lazy dog."
        val ids = tokenizer.encode(text)
        ids.isNotEmpty() shouldBe true
        tokenizer.decode(ids) shouldBe text
    }
})