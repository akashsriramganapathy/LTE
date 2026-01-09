package com.github.libretube.test.ui.models

import androidx.compose.runtime.Composable

sealed class PreferenceItem {
    abstract val title: String
    abstract val summary: String?
    abstract val key: String?

    data class Switch(
        override val title: String,
        override val summary: String? = null,
        override val key: String,
        val defaultValue: Boolean = false
    ) : PreferenceItem()

    data class List(
        override val title: String,
        override val summary: String? = null,
        override val key: String,
        val entries: Map<String, String>, // label to value
        val defaultValue: String = ""
    ) : PreferenceItem()

    data class Clickable(
        override val title: String,
        override val summary: String? = null,
        override val key: String? = null,
        val onClick: () -> Unit
    ) : PreferenceItem()

    data class Category(
        override val title: String,
        override val summary: String? = null,
        override val key: String? = null
    ) : PreferenceItem()
    
    data class Custom(
        override val title: String,
        override val summary: String? = null,
        override val key: String? = null,
        val content: @Composable () -> Unit
    ) : PreferenceItem()
}
