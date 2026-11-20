package org.litellmkt.onnx

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.params.Message

class ChatMLFormatterTest : FunSpec({

    test("formatChat - single user message produces correct ChatML") {
        val params = BaseParamsModel().also { p ->
            p["messages"] = listOf(Message(role = "user", content = "Hello"))
        }
        val result = ChatMLFormatter.formatChat(params)
        result shouldBe "<|user|>\nHello<|end|>\n<|assistant|>\n"
    }

    test("formatChat - system + user messages in order") {
        val params = BaseParamsModel().also { p ->
            p["messages"] = listOf(
                Message(role = "system", content = "You are helpful."),
                Message(role = "user", content = "What is 2+2?")
            )
        }
        val result = ChatMLFormatter.formatChat(params)
        result shouldBe "<|system|>\nYou are helpful.<|end|>\n<|user|>\nWhat is 2+2?<|end|>\n<|assistant|>\n"
    }

    test("formatChat - always ends with assistant prompt") {
        val params = BaseParamsModel().also { p ->
            p["messages"] = listOf(Message(role = "user", content = "Hi"))
        }
        ChatMLFormatter.formatChat(params) shouldEndWith "<|assistant|>\n"
    }

    test("formatChat - empty messages list only emits assistant prompt") {
        val params = BaseParamsModel().also { p ->
            p["messages"] = emptyList<Message>()
        }
        ChatMLFormatter.formatChat(params) shouldBe "<|assistant|>\n"
    }

    test("formatChat - no messages key only emits assistant prompt") {
        val params = BaseParamsModel()
        ChatMLFormatter.formatChat(params) shouldBe "<|assistant|>\n"
    }

    test("formatChat - null content treated as empty string") {
        val params = BaseParamsModel().also { p ->
            p["messages"] = listOf(Message(role = "user", content = null))
        }
        ChatMLFormatter.formatChat(params) shouldBe "<|user|>\n<|end|>\n<|assistant|>\n"
    }

    test("formatChat - full conversation round-trip") {
        val params = BaseParamsModel().also { p ->
            p["messages"] = listOf(
                Message(role = "system", content = "You are a coder."),
                Message(role = "user", content = "Write fizzbuzz."),
                Message(role = "assistant", content = "for i in range(1,101):..."),
                Message(role = "user", content = "Now in Kotlin.")
            )
        }
        val result = ChatMLFormatter.formatChat(params)
        result shouldStartWith "<|system|>\nYou are a coder.<|end|>\n"
        result shouldEndWith "<|assistant|>\n"
        result.lines().count { it.startsWith("<|") } shouldBe 5 // system+user+assistant+user + final assistant
    }

    test("formatCompletion - returns prompt string as-is") {
        val params = BaseParamsModel().also { p ->
            p["prompt"] = "Once upon a time"
        }
        ChatMLFormatter.formatCompletion(params) shouldBe "Once upon a time"
    }

    test("formatCompletion - missing prompt returns empty string") {
        ChatMLFormatter.formatCompletion(BaseParamsModel()) shouldBe ""
    }
})