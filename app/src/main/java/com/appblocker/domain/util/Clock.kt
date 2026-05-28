package com.appblocker.domain.util

fun interface Clock {
    fun nowMs(): Long

    companion object {
        val System: Clock = Clock { java.lang.System.currentTimeMillis() }
    }
}
