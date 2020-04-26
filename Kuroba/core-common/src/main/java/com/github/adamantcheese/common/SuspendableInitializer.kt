package com.github.adamantcheese.common

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * A super useful class for cases when you want to initialize something in a class (that may take
 * some time, like insert some default values into the database or load and cache something from the
 * database) and want every other operation that uses that something to wait (suspend, not block!)
 * until it is done.
 *
 * Be careful, though, because you can easily deadlock (Be especially cautious not to call
 * [invokeWhenInitialized] during initialization. If a deadlock ever occurs, then use [logStates] flag
 * to debug it.
 * */
class SuspendableInitializer<T>(
        private val tag: String,
        private val logStates: Boolean = false,
        private val value: CompletableDeferred<T> = CompletableDeferred()
) {

    fun initWithValue(newValue: T) {
        if (logStates) {
            Log.d(tag, "initWithValue() called")
        }

        if (value.isCompleted) {
            throw RuntimeException("Double initialization detected!")
        }

        value.complete(newValue)
    }

    fun initWithError(exception: Throwable) {
        if (logStates) {
            Log.e(tag, "initWithError() called")
        }

        if (value.isCompleted) {
            throw RuntimeException("Double initialization detected!")
        }

        value.completeExceptionally(exception)
    }

    fun initWithModularResult(modularResult: ModularResult<T>) {
        when (modularResult) {
            is ModularResult.Value -> initWithValue(modularResult.value)
            is ModularResult.Error -> initWithError(modularResult.error)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun awaitUntilInitialized() {
        if (value.isCompleted) {
            if (logStates) {
                Log.d(tag, "awaitUntilInitialized() called when already initialized")
            }

            // This will throw is it was initialized with an error
            value.getCompleted()
            return
        }

        if (logStates) {
            Log.d(tag, "awaitUntilInitialized() called when not initialized, awaiting...")
        }

        value.await()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun get(): T {
        if (value.isCompleted) {
            if (logStates) {
                Log.d(tag, "get() called when already initialized")
            }

            return value.getCompleted()
        }

        if (logStates) {
            Log.d(tag, "get() called when not initialized, awaiting...")
        }

        return value.await()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getOrNull(): T? {
        if (value.isCompleted) {
            if (logStates) {
                Log.d(tag, "getOrNull() called when already initialized")
            }

            return value.getCompleted()
        }

        if (logStates) {
            Log.d(tag, "getOrNull() called when not initialized, returning null")
        }

        return null
    }

    suspend fun <T : Any?> invokeWhenInitialized(func: suspend () -> T): T {
        if (logStates) {
            Log.d(tag, "afterInitialized() called, before awaitUntilInitialized")
        }

        awaitUntilInitialized()

        if (logStates) {
            Log.d(tag, "afterInitialized() called, after awaitUntilInitialized")
        }

        return func()
    }
}