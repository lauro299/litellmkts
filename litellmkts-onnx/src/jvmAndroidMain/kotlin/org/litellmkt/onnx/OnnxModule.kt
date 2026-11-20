package org.litellmkt.onnx

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Returns a Koin [Module] that registers [OnnxChatHandler] and [OnnxGenerationHandler]
 * as singletons bound to [LocalChatHandler] and [LocalGenerationHandler].
 *
 * Usage:
 * ```kotlin
 * startKoin {
 *     modules(
 *         getLitellmktModule(),
 *         getOnnxModule(modelPath = "/data/models/phi3-mini"),
 *         module { single(named("baseUrl")) { "http://unused" } }
 *     )
 * }
 * ```
 *
 * The OrtSession and BpeTokenizer for [modelPath] are created lazily on first use
 * and reused for all subsequent calls (singleton per modelPath).
 *
 * If you need multiple local models simultaneously, call this function once per
 * modelPath and combine the modules:
 * ```kotlin
 * modules(getOnnxModule("/models/phi3"), getOnnxModule("/models/mistral"))
 * ```
 * Note: only one binding per interface type is supported by default Koin. Use
 * named qualifiers if you need both at the same time.
 */
fun getOnnxModule(modelPath: String): Module = module {
    single<LocalChatHandler> { OnnxChatHandler(modelPath) }
    single<LocalGenerationHandler> { OnnxGenerationHandler(modelPath) }
}