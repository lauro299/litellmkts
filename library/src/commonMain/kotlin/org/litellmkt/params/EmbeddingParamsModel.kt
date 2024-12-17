package org.litellmkt.params

import kotlinx.serialization.Serializable

sealed interface HandlerEmbeddingsParamsModel : Model {
    val input: List<String>
}

