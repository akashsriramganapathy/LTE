package com.github.libretube.test.obj

import android.graphics.Bitmap
import com.github.libretube.test.api.obj.Streams

data class DownloadedFile(
    val name: String,
    val size: Long,
    var metadata: Streams? = null,
    var thumbnail: Bitmap? = null
)

