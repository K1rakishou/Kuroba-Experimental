package com.github.k1rakishou.common

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.ExperimentalTime

/**
 * A super useful class for cases when you want to initialize something in a class (that may take
 * some time, like insert some default values into the database or load and cache something from the
 * database) and want every other operation that uses that something to wait (suspend, not block!)
 * until it is done.
 *
 * Be careful, though, because you can easily deadlock.
 * */
class SuspendableInitializer<T> @JvmOverloads constructor(
  private val tag: String,
  private val value: CompletableDeferred<T> = CompletableDeferred()
) {
  private val error = AtomicReference<Throwable>(null)

  fun initWithValue(newValue: T) {
    Log.d(tag, "SuspendableInitializer initWithValue() called")

    if (!value.complete(newValue)) {
      Log.d(tag, "SuspendableInitializer initWithValue() already completed, exiting")
      return
    }

    Log.d(tag, "SuspendableInitializer initWithValue() done")
  }

  fun initWithError(exception: Throwable) {
    Log.e(tag, "SuspendableInitializer initWithError() called")
    error.set(exception)

    if (!value.completeExceptionally(exception)) {
      Log.e(tag, "SuspendableInitializer initWithError() already completed, exiting")
      return
    }

    Log.e(tag, "SuspendableInitializer initWithError() done")
  }

  fun initWithModularResult(modularResult: ModularResult<T>) {
    Log.d(tag, "SuspendableInitializer initWithModularResult() called")

    when (modularResult) {
      is ModularResult.Value -> initWithValue(modularResult.value)
      is ModularResult.Error -> initWithError(modularResult.error)
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun awaitUntilInitialized() {
    if (value.isCompleted) {
      // This will throw if it was initialized with an error
      value.getCompleted()
      return
    }

    Log.d(tag, "SuspendableInitializer awaitUntilInitialized() " +
      "called when not initialized, awaiting... (stacktrace=${Throwable().stackTraceToString()})")

    val startTime = System.currentTimeMillis()
    value.await()
    val diffTime = System.currentTimeMillis() - startTime

    Log.d(tag, "SuspendableInitializer awaitUntilInitialized() called when not initialized, " +
      "done (diffTime=${diffTime}ms)")

    return
  }

  fun isInitialized(): Boolean {
    if (!value.isCompleted) {
      return false
    }

    error.get()?.let { initializationError -> throw RuntimeException(initializationError) }
    return true
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun get(): T {
    if (value.isCompleted) {
      return value.getCompleted()
    }

    Log.d(tag, "SuspendableInitializer get() called when not initialized, awaiting...")
    return value.await()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun getOrNull(): T? {
    if (value.isCompleted) {
      return value.getCompleted()
    }

    Log.d(tag, "SuspendableInitializer getOrNull() called when not initialized, returning null")
    return null
  }
}