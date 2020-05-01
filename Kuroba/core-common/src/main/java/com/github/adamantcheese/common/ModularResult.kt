package com.github.adamantcheese.common

import java.util.*

sealed class ModularResult<V : Any?> {
    data class Value<V : Any?>(val value: V) : ModularResult<V>()
    data class Error<V : Any?>(val error: Throwable) : ModularResult<V>()

    fun peekError(func: (Throwable) -> Unit): ModularResult<V> {
        when (this) {
            is Value -> return this
            is Error -> {
                func(this.error)
                return this
            }
        }
    }

    fun peekValue(func: (V) -> Unit): ModularResult<V> {
        when (this) {
            is Value -> {
                func(this.value)
                return this
            }
            is Error -> return this
        }
    }

    fun valueOrNull(): V? {
        if (this is Value) {
            return value
        }

        return null
    }

    fun errorOrNull(): Throwable? {
        if (this is Error) {
            return error
        }

        return null
    }

    inline fun <T : Any?> mapValue(func: (value: V) -> T): ModularResult<T> {
        return when (this) {
            is Error -> error(error)
            is Value -> Try { func(value) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun mapError(mapper: (error: Throwable) -> Throwable): ModularResult<V> {
        return when (this) {
            is Error -> error(mapper(error))
            is Value -> this
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <T : Any?> mapErrorToValue(mapper: (error: Throwable) -> T): T {
        return when (this) {
            is Error -> mapper(error)
            is Value -> value as T
        }
    }

    fun ignore() {
        // No-op. Just an indicator that we don't care about handling this result. This is just so
        // it's obvious that the original intention was to ignore handling the result not that it
        // was forgotten completely.
    }

    /**
     * This is a handy function for cases when you want to only log the error and the return from
     * the function. In case of a value we won't log anything and will just return the value.
     * */
    inline fun safeUnwrap(handler: (Throwable) -> Nothing): V {
        return when (this) {
            is Error -> handler(error)
            is Value -> value
        }
    }

    fun unwrap(): V {
        return when (this) {
            is Error -> throw error
            is Value -> value
        }
    }

    override fun toString(): String {
        return when (this) {
            is Value -> value?.toString() ?: "ModularResult.Value{null}"
            is Error -> String.format(
                    Locale.ENGLISH,
                    "ModularResult.Error{%s, message: %s}",
                    error.javaClass.simpleName,
                    error.message ?: "No error message"
            )
        }
    }

    companion object {
        @JvmStatic
        fun <V : Any?> value(value: V): ModularResult<V> {
            return Value(value)
        }

        @JvmStatic
        fun <V : Any?> error(error: Throwable): ModularResult<V> {
            return Error(error)
        }

        @Suppress("FunctionName")
        inline fun <T> Try(func: () -> T): ModularResult<T> {
            return try {
                value(func())
            } catch (error: Throwable) {
                error(error)
            }
        }

    }
}