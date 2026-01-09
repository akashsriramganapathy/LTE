package com.github.libretube.test.extensions

import androidx.lifecycle.MutableLiveData

fun <T> MutableLiveData<T>.updateIfChanged(newValue: T) {
    if (value != newValue) value = newValue
}

