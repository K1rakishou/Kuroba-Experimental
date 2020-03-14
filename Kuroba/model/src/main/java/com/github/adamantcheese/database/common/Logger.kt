package com.github.adamantcheese.database.common

import android.util.Log

class Logger(private val verboseLogs: Boolean) {
    internal fun log(tag: String, message: String) {
        if (verboseLogs) {
            Log.d(tag, String.format("[%s]: %s", Thread.currentThread().name, message))
        }
    }

    internal fun logError(tag: String, message: String, error: Throwable? = null) {
        if (error == null) {
            Log.e(tag, String.format("[%s]: %s", Thread.currentThread().name, message))
        } else {
            Log.e(tag, String.format("[%s]: %s", Thread.currentThread().name, message), error)
        }
    }
}