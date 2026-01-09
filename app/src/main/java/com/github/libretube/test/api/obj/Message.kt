package com.github.libretube.test.api.obj

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val error: String? = null,
    val message: String? = null
)

