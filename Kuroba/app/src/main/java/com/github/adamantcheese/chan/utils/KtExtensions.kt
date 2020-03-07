package com.github.adamantcheese.chan.utils

import java.util.regex.Matcher

/**
 * Forces the kotlin compiler to require handling of all branches in the "when" operator
 * */
val <T : Any?> T.exhaustive: T
    get() = this

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