package org.litellmkt.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.results.BaseResultModel
import java.nio.LongBuffer
import kotlin.time.Clock

/**
 * Text-completion (generation) handler backed by an ONNX model loaded from [modelPath].
 *
 * Unlike [OnnxChatHandler], this handler takes a raw prompt string (no chat history)
 * and performs causal language modelling generation.
 *
 * See [OnnxChatHandler] for model file layout and input/output tensor conventions.
 */
class OnnxGenerationHandler(private val modelPath: String) : LocalGenerationHandler {

    override fun stream(params: BaseParamsModel): Flow<BaseResultModel> = flow {
        val startMs = Clock.System.now().toEpochMilliseconds()
        val session = OrtSessionManager.getSession(modelPath)
        val tokenizer = OrtSessionManager.getTokenizer(modelPath)
        val env = OrtSessionManager.getEnv()

        val prompt = ChatMLFormatter.formatCompletion(params)
        val maxNewTokens = (params["max_tokens"] as? Int) ?: 256
        val inputNames = session.inputNames.toSet()
        val outputName = if ("logits" in session.outputNames) "logits" else session.outputNames.first()

        var inputIds = tokenizer.encode(prompt).map { it.toLong() }
        val eosId = tokenizer.eosTokenId?.toLong()
        val generated = mutableListOf<Int>()
        val modelName = "local:${modelPath.substringAfterLast('/')}"

        repeat(maxNewTokens) {
            val nextId = inferNextToken(session, env, inputNames, outputName, inputIds)
            if (eosId != null && nextId == eosId) return@repeat
            generated.add(nextId.toInt())
            inputIds = inputIds + nextId
            val piece = tokenizer.decodeToken(nextId.toInt())
            if (piece.isNotEmpty()) {
                emit(
                    BaseResultModel(
                        model = modelName,
                        createdAt = Clock.System.now().toString(),
                        response = piece,
                        done = false
                    )
                )
            }
            if (eosId != null && nextId == eosId) return@flow
        }

        val fullText = tokenizer.decode(generated)
        emit(
            BaseResultModel(
                model = modelName,
                createdAt = Clock.System.now().toString(),
                response = fullText,
                done = true,
                totalDuration = Clock.System.now().toEpochMilliseconds() - startMs
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun inferNextToken(
        session: OrtSession,
        env: OrtEnvironment,
        inputNames: Set<String>,
        outputName: String,
        inputIds: List<Long>
    ): Long {
        val seqLen = inputIds.size.toLong()
        val shape = longArrayOf(1L, seqLen)
        val ids = inputIds.toLongArray()

        val inputs = buildMap<String, OnnxTensor> {
            if ("input_ids" in inputNames)
                put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape))
            if ("attention_mask" in inputNames)
                put("attention_mask", OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1L }), shape))
            if ("position_ids" in inputNames)
                put("position_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { i -> i.toLong() }), shape))
        }

        return try {
            session.run(inputs).use { results ->
                val logits = results.get(outputName).orElseThrow {
                    IllegalStateException("Output '$outputName' not found in model outputs")
                }.value as Array<Array<FloatArray>>
                greedy(logits[0][ids.size - 1])
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private fun greedy(logits: FloatArray): Long {
        var best = 0
        for (i in 1 until logits.size) if (logits[i] > logits[best]) best = i
        return best.toLong()
    }
}