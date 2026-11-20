package org.litellmkt.onnx

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.params.Message
import org.litellmkt.results.BaseResultModel
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.nio.ShortBuffer
import kotlin.time.Clock

/**
 * Chat handler backed by an ONNX model loaded from [modelPath].
 *
 * Supports two model formats — selected via [OnnxModelConfig.kvCache]:
 *
 * **Simple (no KV-cache):** inputs = input_ids, attention_mask, position_ids.
 *   The full prompt is re-fed every token. Suitable for small/distilled models.
 *
 * **Merged decoder (KV-cache):** inputs include past_key_values.{i}.key/value,
 *   outputs include present.{i}.key/value. Used by Phi-3, Mistral, LLaMA ONNX exports.
 *   The prompt is prefilled once; decode loop feeds one token at a time.
 *
 * @param modelPath Directory that contains the model and tokenizer files.
 * @param config    Model configuration (file names, KV-cache strategy, etc).
 *                  Use [OnnxModelConfig] for common options or pass a custom instance.
 */
class OnnxChatHandler(
    private val modelPath: String,
    private val config: OnnxModelConfig = OnnxModelConfig(),
) : LocalChatHandler {

    /**
     * Convenience constructor for simple cases without KV-cache configuration.
     */
    constructor(
        modelPath: String,
        modelName: String = "model.onnx",
        tokenizerName: String = "tokenizer.json",
    ) : this(modelPath, OnnxModelConfig(modelName = modelName, tokenizerName = tokenizerName))

    override fun chat(params: BaseParamsModel): Flow<BaseResultModel> = flow {
        val startMs = Clock.System.now().toEpochMilliseconds()
        val session = OrtSessionManager.getSession(modelPath, config.modelName)
        val tokenizer = OrtSessionManager.getTokenizer(modelPath, config.tokenizerName)
        val env = OrtSessionManager.getEnv()

        val prompt = ChatMLFormatter.formatChat(params)
        val maxNewTokens = (params["max_tokens"] as? Int) ?: config.maxNewTokens
        val inputNames = session.inputNames.toSet()
        val outputNames = session.outputNames.toSet()
        val logitsOutput = if ("logits" in outputNames) "logits" else outputNames.first()

        val resolvedKv = resolveKvConfig(config.kvCache, inputNames)

        val promptIds = tokenizer.encode(prompt).map { it.toLong() }
        val eosId = tokenizer.eosTokenId?.toLong()
        val generated = mutableListOf<Int>()
        val modelLabel = "local:${modelPath.substringAfterLast('/')}"

        if (resolvedKv != null) {
            runKvCacheDecode(
                session, tokenizer, env,
                inputNames, outputNames, logitsOutput,
                promptIds, maxNewTokens, eosId, generated, modelLabel, resolvedKv,
            ) { emit(it) }
        } else {
            runSimpleDecode(
                session, env,
                inputNames, logitsOutput,
                promptIds, maxNewTokens, eosId, generated, modelLabel,
            ) { emit(it) }
        }

        val fullText = tokenizer.decode(generated)
        emit(
            BaseResultModel(
                model = modelLabel,
                createdAt = Clock.System.now().toString(),
                response = fullText,
                done = true,
                message = Message(role = "assistant", content = fullText),
                totalDuration = Clock.System.now().toEpochMilliseconds() - startMs
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Resolves whether to use KV-cache and returns an [ResolvedKvConfig] if so, or null for simple mode.
     */
    private fun resolveKvConfig(kvCacheConfig: KvCacheConfig, inputNames: Set<String>): ResolvedKvConfig? {
        val enabled = when (kvCacheConfig) {
            is KvCacheConfig.Disabled -> return null
            is KvCacheConfig.Auto -> inputNames.any { it.startsWith("past_key_values.") && it.endsWith(".key") }
            is KvCacheConfig.Enabled -> true
        }
        if (!enabled) return null

        val inputPrefix = (kvCacheConfig as? KvCacheConfig.Enabled)?.inputPrefix ?: "past_key_values"
        val outputPrefix = (kvCacheConfig as? KvCacheConfig.Enabled)?.outputPrefix ?: "present"
        val overrideNumKvHeads = (kvCacheConfig as? KvCacheConfig.Enabled)?.numKvHeads
        val overrideHeadDim = (kvCacheConfig as? KvCacheConfig.Enabled)?.headDim

        return ResolvedKvConfig(inputPrefix, outputPrefix, overrideNumKvHeads, overrideHeadDim)
    }

    private data class ResolvedKvConfig(
        val inputPrefix: String,
        val outputPrefix: String,
        val overrideNumKvHeads: Int?,
        val overrideHeadDim: Int?,
    )

    // ── KV-cache path ────────────────────────────────────────────────────────

    private suspend fun runKvCacheDecode(
        session: OrtSession,
        tokenizer: BpeTokenizer,
        env: OrtEnvironment,
        inputNames: Set<String>,
        outputNames: Set<String>,
        logitsOutput: String,
        promptIds: List<Long>,
        maxNewTokens: Int,
        eosId: Long?,
        generated: MutableList<Int>,
        modelLabel: String,
        kv: ResolvedKvConfig,
        emit: suspend (BaseResultModel) -> Unit,
    ) {
        val numLayers = inputNames.count { it.startsWith("${kv.inputPrefix}.") && it.endsWith(".key") }

        val (numKvHeads, headDim, kvType) = resolveKvShape(session, kv)

        var pastKv = createEmptyKvCache(env, numLayers, numKvHeads, headDim, kvType, kv.inputPrefix)
        var (lastId, newKv) = kvStep(
            session, env, inputNames, outputNames, logitsOutput,
            promptIds, pastKv, posOffset = 0, numLayers, kv,
        )
        pastKv.values.forEach { it.close() }
        pastKv = newKv
        var currentPos = promptIds.size

        if (eosId == null || lastId != eosId) {
            generated.add(lastId.toInt())
            tokenizer.decodeToken(lastId.toInt()).takeIf { it.isNotEmpty() }
                ?.let { emit(tokenChunk(modelLabel, it)) }
        }

        for (step in 1 until maxNewTokens) {
            if (eosId != null && lastId == eosId) break
            val (nextId, nextKv) = kvStep(
                session, env, inputNames, outputNames, logitsOutput,
                listOf(lastId), pastKv, posOffset = currentPos, numLayers, kv,
            )
            pastKv.values.forEach { it.close() }
            pastKv = nextKv
            currentPos++
            lastId = nextId
            if (eosId != null && nextId == eosId) break
            generated.add(nextId.toInt())
            tokenizer.decodeToken(nextId.toInt()).takeIf { it.isNotEmpty() }
                ?.let { emit(tokenChunk(modelLabel, it)) }
        }
        pastKv.values.forEach { it.close() }
    }

    /** Resolves numKvHeads, headDim and element type from config overrides or model metadata. */
    private data class KvShapeInfo(val numKvHeads: Int, val headDim: Int, val elemType: OnnxJavaType)

    private fun resolveKvShape(session: OrtSession, kv: ResolvedKvConfig): KvShapeInfo {
        // If both are overridden, skip model metadata lookup
        if (kv.overrideNumKvHeads != null && kv.overrideHeadDim != null) {
            return KvShapeInfo(kv.overrideNumKvHeads, kv.overrideHeadDim, OnnxJavaType.FLOAT)
        }

        // Read shape from inputInfo or outputInfo — shape is [batch, num_kv_heads, seq_len, head_dim]
        val firstInputKey = session.inputNames.firstOrNull { it.startsWith("${kv.inputPrefix}.") && it.endsWith(".key") }
        val firstOutputKey = session.outputNames.firstOrNull { it.startsWith("${kv.outputPrefix}.") && it.endsWith(".key") }
        val tensorInfo = (firstInputKey?.let { session.inputInfo[it]?.info }
            ?: firstOutputKey?.let { session.outputInfo[it]?.info }) as? TensorInfo
            ?: error(
                "Cannot resolve KV-cache shape for prefix '${kv.inputPrefix}'.\n" +
                "Specify numKvHeads and headDim manually via KvCacheConfig.Enabled(...)\n" +
                "inputInfo keys: ${session.inputInfo.keys.filter { it.startsWith(kv.inputPrefix) }}\n" +
                "outputInfo keys: ${session.outputInfo.keys.filter { it.startsWith(kv.outputPrefix) }}"
            )

        val shape = tensorInfo.shape
        require(shape.size == 4 && shape[1] > 0 && shape[3] > 0) {
            "Expected static num_kv_heads (dim 1) and head_dim (dim 3) in KV shape, got: ${shape.toList()}.\n" +
            "Override them via KvCacheConfig.Enabled(numKvHeads = ..., headDim = ...)"
        }

        return KvShapeInfo(
            numKvHeads = kv.overrideNumKvHeads ?: shape[1].toInt(),
            headDim = kv.overrideHeadDim ?: shape[3].toInt(),
            elemType = tensorInfo.type,
        )
    }

    /**
     * Single forward pass with KV-cache.
     * Returns (nextTokenId, updatedPastKv) — caller owns and must close the returned tensors.
     */
    private fun kvStep(
        session: OrtSession,
        env: OrtEnvironment,
        inputNames: Set<String>,
        outputNames: Set<String>,
        logitsOutput: String,
        tokenIds: List<Long>,
        pastKv: Map<String, OnnxTensor>,
        posOffset: Int,
        numLayers: Int,
        kv: ResolvedKvConfig,
    ): Pair<Long, Map<String, OnnxTensor>> {
        val seqLen = tokenIds.size.toLong()
        val pastLen = (pastKv["${kv.inputPrefix}.0.key"]?.info as? TensorInfo)?.shape?.getOrNull(2) ?: 0L
        val totalLen = pastLen + seqLen
        val ids = tokenIds.toLongArray()

        val freshInputs = buildMap<String, OnnxTensor> {
            if ("input_ids" in inputNames)
                put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1L, seqLen)))
            if ("attention_mask" in inputNames)
                put("attention_mask", OnnxTensor.createTensor(
                    env, LongBuffer.wrap(LongArray(totalLen.toInt()) { 1L }), longArrayOf(1L, totalLen)))
            if ("position_ids" in inputNames)
                put("position_ids", OnnxTensor.createTensor(
                    env, LongBuffer.wrap(LongArray(ids.size) { i -> (posOffset + i).toLong() }), longArrayOf(1L, seqLen)))
        }

        val results = session.run(freshInputs + pastKv)
        try {
            val logits = results.get(logitsOutput).orElseThrow {
                IllegalStateException("Output '$logitsOutput' not found")
            }.value as Array<Array<FloatArray>>
            val nextId = greedy(logits[0][ids.size - 1])

            val newKv = buildMap {
                for (i in 0 until numLayers) {
                    val outKey = "${kv.outputPrefix}.$i.key"
                    val outVal = "${kv.outputPrefix}.$i.value"
                    if (outKey in outputNames)
                        put("${kv.inputPrefix}.$i.key", env.cloneFloat4dTensor(results.get(outKey).orElseThrow().value))
                    if (outVal in outputNames)
                        put("${kv.inputPrefix}.$i.value", env.cloneFloat4dTensor(results.get(outVal).orElseThrow().value))
                }
            }
            return Pair(nextId, newKv)
        } finally {
            results.close()
            freshInputs.values.forEach { it.close() }
            // pastKv tensors are NOT closed here — caller manages them
        }
    }

    private fun createEmptyKvCache(
        env: OrtEnvironment,
        numLayers: Int,
        numKvHeads: Int,
        headDim: Int,
        elemType: OnnxJavaType,
        inputPrefix: String,
    ): Map<String, OnnxTensor> = buildMap {
        val shape = longArrayOf(1L, numKvHeads.toLong(), 0L, headDim.toLong())
        repeat(numLayers) { i ->
            put("$inputPrefix.$i.key", env.emptyKvTensor(shape, elemType))
            put("$inputPrefix.$i.value", env.emptyKvTensor(shape, elemType))
        }
    }

    private fun OrtEnvironment.emptyKvTensor(shape: LongArray, elemType: OnnxJavaType): OnnxTensor =
        when (elemType) {
            OnnxJavaType.FLOAT16 -> OnnxTensor.createTensor(this, ShortBuffer.wrap(ShortArray(0)), shape, OnnxJavaType.FLOAT16)
            else -> OnnxTensor.createTensor(this, FloatBuffer.wrap(FloatArray(0)), shape)
        }

    @Suppress("UNCHECKED_CAST")
    private fun OrtEnvironment.cloneFloat4dTensor(value: Any): OnnxTensor {
        val arr = value as Array<Array<Array<FloatArray>>>
        val b = arr.size; val h = arr[0].size; val s = arr[0][0].size; val d = arr[0][0][0].size
        val flat = FloatArray(b * h * s * d)
        var idx = 0
        for (bi in 0 until b) for (hi in 0 until h) for (si in 0 until s) for (di in 0 until d)
            flat[idx++] = arr[bi][hi][si][di]
        return OnnxTensor.createTensor(this, FloatBuffer.wrap(flat), longArrayOf(b.toLong(), h.toLong(), s.toLong(), d.toLong()))
    }

    // ── Simple (no KV-cache) path ────────────────────────────────────────────

    private suspend fun runSimpleDecode(
        session: OrtSession,
        env: OrtEnvironment,
        inputNames: Set<String>,
        logitsOutput: String,
        promptIds: List<Long>,
        maxNewTokens: Int,
        eosId: Long?,
        generated: MutableList<Int>,
        modelLabel: String,
        emit: suspend (BaseResultModel) -> Unit,
    ) {
        var inputIds = promptIds
        repeat(maxNewTokens) {
            val nextId = simpleNextToken(session, env, inputNames, logitsOutput, inputIds)
            if (eosId != null && nextId == eosId) return
            generated.add(nextId.toInt())
            inputIds = inputIds + nextId
        }
    }

    private fun simpleNextToken(
        session: OrtSession,
        env: OrtEnvironment,
        inputNames: Set<String>,
        outputName: String,
        inputIds: List<Long>,
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
                    IllegalStateException("Output '$outputName' not found")
                }.value as Array<Array<FloatArray>>
                greedy(logits[0][ids.size - 1])
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun tokenChunk(modelLabel: String, piece: String) = BaseResultModel(
        model = modelLabel,
        createdAt = Clock.System.now().toString(),
        response = piece,
        done = false,
        message = Message(role = "assistant", content = piece),
    )

    private fun greedy(logits: FloatArray): Long {
        var best = 0
        for (i in 1 until logits.size) if (logits[i] > logits[best]) best = i
        return best.toLong()
    }
}