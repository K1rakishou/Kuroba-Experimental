package com.github.adamantcheese.base

sealed class ModularResult<V> {
    data class Value<V>(val value: V) : ModularResult<V>()
    data class Error<V>(val error: Throwable) : ModularResult<V>()

    fun isError() = this is Error
    fun isValue() = this is Value

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

    inline fun <T> map(func: (value: V) -> T): ModularResult<T?> {
        return when (this) {
            is Error -> error(error)
            is Value -> safeRun { func(value) }
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
                    "ModularResult.Error{%s, message: %s}",
                    error.javaClass.simpleName,
                    error.message ?: "No error message"
            )
        }
    }

    companion object {
        @JvmStatic
        fun <V> value(value: V): ModularResult<V> {
            return Value(value)
        }

        @JvmStatic
        fun <V> error(error: Throwable): ModularResult<V> {
            return Error(error)
        }

        inline fun <T> safeRun(func: () -> T): ModularResult<T> {
            return try {
                value(func())
            } catch (error: Throwable) {
                error(error)
            }
        }

        // These two are for calling from the Java code since it's not really convenient to use
        // kotlin's lambdas in Java code.
        @JvmStatic
        fun safeRun(func: VoidFunction): ModularResult<Unit> {
            return try {
                value(func.invoke())
            } catch (error: Throwable) {
                error(error)
            }
        }

        @JvmStatic
        fun <T> safeRunR(func: GenericFunction<T>): ModularResult<T> {
            return try {
                value(func.invoke())
            } catch (error: Throwable) {
                error(error)
            }
        }
    }
}