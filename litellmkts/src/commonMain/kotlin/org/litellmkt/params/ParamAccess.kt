package org.litellmkt.params

import kotlinx.serialization.json.JsonPrimitive

/*
 * Typed access to the free-form BaseParamsModel map. Missing keys return null; a value of the wrong
 * type fails with IllegalArgumentException instead of being silently dropped.
 */

internal fun BaseParamsModel.requireModel(): String =
    string("model") ?: throw IllegalArgumentException("Missing required parameter 'model'")

internal fun BaseParamsModel.string(key: String): String? = when (val value = this[key]) {
    null -> null
    is String -> value
    else -> invalid(key, "a string", value)
}

internal fun BaseParamsModel.boolean(key: String): Boolean? = when (val value = this[key]) {
    null -> null
    is Boolean -> value
    else -> invalid(key, "a boolean", value)
}

internal fun BaseParamsModel.double(key: String): Double? = number(key)?.toDouble()

internal fun BaseParamsModel.int(key: String): Int? = number(key)?.toInt()

internal fun BaseParamsModel.long(key: String): Long? = number(key)?.toLong()

internal fun BaseParamsModel.stringList(key: String): List<String>? = when (val value = this[key]) {
    null -> null
    is List<*> -> value.map { it as? String ?: invalid(key, "a list of strings", value) }
    else -> invalid(key, "a list of strings", value)
}

/** Accepts a single string or a list of strings. */
internal fun BaseParamsModel.stringOrList(key: String): List<String>? = when (val value = this[key]) {
    is String -> listOf(value)
    else -> stringList(key)
}

internal fun BaseParamsModel.messages(key: String = "messages"): List<Message>? = when (val value = this[key]) {
    null -> null
    is List<*> -> value.map { it as? Message ?: invalid(key, "a list of Message", value) }
    else -> invalid(key, "a list of Message", value)
}

/** A number or a string, e.g. Ollama's `keep_alive` (300 or "5m"). */
internal fun BaseParamsModel.numberOrString(key: String): JsonPrimitive? = when (val value = this[key]) {
    null -> null
    is Number -> JsonPrimitive(value)
    is String -> JsonPrimitive(value)
    else -> invalid(key, "a number or a string", value)
}

private fun BaseParamsModel.number(key: String): Number? = when (val value = this[key]) {
    null -> null
    is Number -> value
    else -> invalid(key, "a number", value)
}

private fun invalid(key: String, expected: String, value: Any): Nothing =
    throw IllegalArgumentException("Invalid '$key': expected $expected but was ${value::class.simpleName}")
