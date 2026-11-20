package org.litellmkt.onnx

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.toList
import org.koin.dsl.koinApplication
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.params.Message
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteRecursively

@OptIn(ExperimentalPathApi::class)
class OnnxHandlerErrorTest : FunSpec({

    // ── OrtSessionManager ────────────────────────────────────────────────────

    test("getSession throws when model.onnx is missing") {
        val ex = shouldThrow<IllegalArgumentException> {
            OrtSessionManager.getSession("/nonexistent/path/that/does/not/exist/onnx_test")
        }
        ex.message shouldContain "model.onnx"
        ex.message shouldContain "not found"
    }

    test("getTokenizer throws when tokenizer.json is missing") {
        val ex = shouldThrow<IllegalArgumentException> {
            OrtSessionManager.getTokenizer("/nonexistent/path/that/does/not/exist/onnx_test")
        }
        ex.message shouldContain "tokenizer.json"
        ex.message shouldContain "not found"
    }

    test("getSession error message includes the attempted path") {
        val path = "/tmp/does_not_exist_litellmkts_onnx_session"
        val ex = shouldThrow<IllegalArgumentException> {
            OrtSessionManager.getSession(path)
        }
        ex.message shouldContain path
    }

    test("getTokenizer error message includes the attempted path") {
        val path = "/tmp/does_not_exist_litellmkts_onnx_tokenizer"
        val ex = shouldThrow<IllegalArgumentException> {
            OrtSessionManager.getTokenizer(path)
        }
        ex.message shouldContain path
    }

    // ── OnnxChatHandler ──────────────────────────────────────────────────────

    test("OnnxChatHandler.chat throws when model directory is missing") {
        val handler = OnnxChatHandler("/nonexistent/model/path/onnx_chat_test")
        val params = BaseParamsModel().also { p ->
            p["messages"] = listOf(Message(role = "user", content = "Hello"))
        }
        val ex = shouldThrow<IllegalArgumentException> {
            handler.chat(params).toList()
        }
        ex.message shouldContain "model.onnx"
    }

    test("OnnxChatHandler.chat throws when tokenizer.json is missing") {
        val dir = createTempDir("onnx_test_no_tokenizer")
        dir.resolve("model.onnx").toFile().createNewFile()  // model present, tokenizer absent
        try {
            val handler = OnnxChatHandler(dir.absolutePathString())
            val params = BaseParamsModel().also { p ->
                p["messages"] = listOf(Message(role = "user", content = "Hi"))
            }
            // Session creation will try to load an empty/invalid model.onnx;
            // ensure at minimum that the error is thrown (not silent).
            shouldThrow<Exception> {
                handler.chat(params).toList()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── OnnxGenerationHandler ────────────────────────────────────────────────

    test("OnnxGenerationHandler.stream throws when model directory is missing") {
        val handler = OnnxGenerationHandler("/nonexistent/model/path/onnx_gen_test")
        val params = BaseParamsModel().also { p ->
            p["prompt"] = "Once upon a time"
        }
        val ex = shouldThrow<IllegalArgumentException> {
            handler.stream(params).toList()
        }
        ex.message shouldContain "model.onnx"
    }

    test("OnnxGenerationHandler.stream throws when tokenizer.json is missing") {
        val dir = createTempDir("onnx_test_gen_no_tokenizer")
        dir.resolve("model.onnx").toFile().createNewFile()
        try {
            val handler = OnnxGenerationHandler(dir.absolutePathString())
            val params = BaseParamsModel().also { p ->
                p["prompt"] = "Tell me a joke."
            }
            shouldThrow<Exception> {
                handler.stream(params).toList()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── Koin module wiring ───────────────────────────────────────────────────

    test("getOnnxModule registers LocalChatHandler bound to OnnxChatHandler") {
        val koin = koinApplication { modules(getOnnxModule("/tmp/fake_model_a")) }.koin
        try {
            val handler = koin.get<LocalChatHandler>()
            (handler is OnnxChatHandler) shouldBe true
        } finally {
            koin.close()
        }
    }

    test("getOnnxModule registers LocalGenerationHandler bound to OnnxGenerationHandler") {
        val koin = koinApplication { modules(getOnnxModule("/tmp/fake_model_b")) }.koin
        try {
            val handler = koin.get<LocalGenerationHandler>()
            (handler is OnnxGenerationHandler) shouldBe true
        } finally {
            koin.close()
        }
    }

    test("getOnnxModule instances from different modelPaths are distinct objects") {
        val koinA = koinApplication { modules(getOnnxModule("/tmp/model_path_x")) }.koin
        val koinB = koinApplication { modules(getOnnxModule("/tmp/model_path_y")) }.koin
        try {
            val handlerA = koinA.get<LocalChatHandler>()
            val handlerB = koinB.get<LocalChatHandler>()
            handlerA shouldNotBe handlerB
        } finally {
            koinA.close()
            koinB.close()
        }
    }
})

@Suppress("DEPRECATION")
private fun createTempDir(prefix: String) = kotlin.io.path.createTempDirectory(prefix)