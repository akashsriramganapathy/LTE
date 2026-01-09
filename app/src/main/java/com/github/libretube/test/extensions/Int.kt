package com.github.libretube.test.extensions

import kotlin.math.ceil

fun Int.ceilHalf() = ceil((toDouble() / 2)).toInt()

