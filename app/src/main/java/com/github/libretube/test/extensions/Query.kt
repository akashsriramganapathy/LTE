package com.github.libretube.test.extensions

fun query(block: () -> Unit) {
    Thread {
        try {
            block.invoke()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }.start()
}

