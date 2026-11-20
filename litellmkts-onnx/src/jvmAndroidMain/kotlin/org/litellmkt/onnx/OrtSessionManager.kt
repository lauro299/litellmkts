package org.litellmkt.onnx

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton that caches OrtSession and BpeTokenizer instances by modelPath.
 *
 * Sessions are expensive to create (loads the native model into memory), so each
 * unique modelPath is initialized only once for the lifetime of the process.
 *
 * Expected directory layout:
 *   <modelPath>/model.onnx
 *   <modelPath>/tokenizer.json
 */
internal object OrtSessionManager {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessions = ConcurrentHashMap<String, OrtSession>()
    private val tokenizers = ConcurrentHashMap<String, BpeTokenizer>()

    fun getEnv(): OrtEnvironment = env

    fun getSession(modelPath: String, modelName: String = "model.onnx"): OrtSession =
        sessions.getOrPut(modelPath) {
            val modelFile = File(modelPath, modelName)
            require(modelFile.exists()) {
                "model.onnx not found at: ${modelFile.absolutePath}"
            }
            env.createSession(modelFile.absolutePath)
        }

    fun getTokenizer(modelPath: String, tokenizerName: String = "tokenizer.json"): BpeTokenizer =
        tokenizers.getOrPut(modelPath) {
            val tokenizerFile = File(modelPath, tokenizerName)
            require(tokenizerFile.exists()) {
                "tokenizer.json not found at: ${tokenizerFile.absolutePath}"
            }
            BpeTokenizer(tokenizerFile.readText())
        }
}