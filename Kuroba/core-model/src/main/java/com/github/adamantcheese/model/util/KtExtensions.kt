package com.github.adamantcheese.model.util

internal fun Throwable.errorMessageOrClassName(): String {
    if (message != null) {
        return message!!
    }

    return this::class.java.name
}