package com.github.adamantcheese.database.util

import android.os.Looper

internal fun isMainThread(): Boolean {
    return Thread.currentThread() === Looper.getMainLooper().getThread()
}

internal fun ensureMainThread() {
    if (!isMainThread()) {
        throw RuntimeException("Cannot be executed on a background thread!")
    }
}

internal fun ensureBackgroundThread() {
    if (isMainThread()) {
        throw RuntimeException("Cannot be executed on the main thread!")
    }
}