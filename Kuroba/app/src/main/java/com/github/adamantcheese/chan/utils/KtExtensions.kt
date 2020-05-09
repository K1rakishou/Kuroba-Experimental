package com.github.adamantcheese.chan.utils

import com.airbnb.epoxy.*
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

/**
 * Not thread-safe!
 * */
fun <K, V> HashMap<K, V>.putIfNotContains(key: K, value: V) {
    if (!this.containsKey(key)) {
        this[key] = value
    }
}

fun Throwable.errorMessageOrClassName(): String {
    if (message != null) {
        return message!!
    }

    return this::class.java.name
}

fun EpoxyRecyclerView.withModelsAsync(buildModels: EpoxyController.() -> Unit) {
    val controller = object : AsyncEpoxyController(true) {
        override fun buildModels() {
            buildModels(this)
        }
    }

    setController(controller)
    controller.requestModelBuild()
}

fun EpoxyController.addOneshotModelBuildListener(callback: () -> Unit) {
    addModelBuildListener(object : OnModelBuildFinishedListener {
        override fun onModelBuildFinished(result: DiffResult) {
            callback()

            removeModelBuildListener(this)
        }
    })
}