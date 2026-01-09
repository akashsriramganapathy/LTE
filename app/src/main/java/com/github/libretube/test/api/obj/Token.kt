package com.github.libretube.test.api.obj

import kotlinx.serialization.Serializable

@Serializable
data class Token(
    val token: String? = null,
    val error: String? = null
)

