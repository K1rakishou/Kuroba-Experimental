package com.github.adamantcheese.chan.utils

import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

import java.util.regex.Matcher

/**
 * Forces the kotlin compiler to require handling of all branches in the "when" operator
 * */
val <T : Any?> T.exhaustive: T
    get() = this

operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
    this.add(disposable)
}

fun Matcher.groupOrNull(group: Int): String? {
    return try {
        if (group < 0 || group > groupCount()) {
            return null
        }

        this.group(group)
    } catch (error: Throwable) {
        null
    }
}

/**
 * Not thread-safe!
 * */
fun <K, V> MutableMap<K, V>.putIfNotContains(key: K, value: V) {
    if (!this.containsKey(key)) {
        this[key] = value
    }
}

fun <K, V> HashMap<K, V>.putIfNotContains(key: K, value: V) {
    if (!this.containsKey(key)) {
        this[key] = value
    }
}