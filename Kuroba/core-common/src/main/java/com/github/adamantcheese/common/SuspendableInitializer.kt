package com.github.adamantcheese.common

import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference

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
class SuspendableInitializer<T> @JvmOverloads constructor(
  private val tag: String,
  private val logStates: Boolean = false,
  private val value: CompletableDeferred<T> = CompletableDeferred()
) {
  private val toRunAfterInitialized = mutableListOf<(Throwable?) -> Unit>()
  private val error = AtomicReference<Throwable>(null)

  fun initWithValue(newValue: T) {
    if (logStates) {
      Log.d(tag, "SuspendableInitializer initWithValue() called")
    }

    if (value.isCompleted) {
      throw RuntimeException("Double initialization detected!")
    }

    value.complete(newValue)
    invokeAllCallbacks(null)

    if (logStates) {
      Log.d(tag, "SuspendableInitializer initWithValue() done")
    }
  }

  fun initWithError(exception: Throwable) {
    if (logStates) {
      Log.e(tag, "SuspendableInitializer initWithError() called")
    }

    if (value.isCompleted) {
      throw RuntimeException("Double initialization detected!")
    }

    error.set(exception)
    value.completeExceptionally(exception)
    invokeAllCallbacks(exception)

    if (logStates) {
      Log.e(tag, "SuspendableInitializer initWithError() done")
    }
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
        Log.d(tag, "SuspendableInitializer awaitUntilInitialized() called when already initialized")
      }

      // This will throw if it was initialized with an error
      value.getCompleted()
      return
    }

    if (logStates) {
      Log.d(tag, "SuspendableInitializer awaitUntilInitialized() called when not initialized, awaiting...")
    }

    value.awaitSilently()
    return
  }

  fun awaitUntilInitializedBlocking() {
    if (logStates) {
      Log.d(tag, "SuspendableInitializer awaitUntilInitializedBlocking() called, " +
        "currentThread = ${Thread.currentThread().name}")
    }

    if (isInitialized()) {
      return
    }

    if (Thread.currentThread() == Looper.getMainLooper().thread) {
      throw IllegalStateException("Cannot be executed on the main thread. This will deadlock the app!")
    }

    if (logStates) {
      Log.d(tag, "SuspendableInitializer awaitUntilInitializedBlocking() before blocking await(), " +
        "currentThread = ${Thread.currentThread().name}")
    }

    runBlocking { awaitUntilInitialized() }

    if (logStates) {
      Log.d(tag, "SuspendableInitializer awaitUntilInitializedBlocking() after blocking await(), " +
        "currentThread = ${Thread.currentThread().name}")
    }
  }

  fun isInitialized() = value.isCompleted

  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun get(): T {
    if (value.isCompleted) {
      if (logStates) {
        Log.d(tag, "SuspendableInitializer get() called when already initialized")
      }

      return value.getCompleted()
    }

    if (logStates) {
      Log.d(tag, "SuspendableInitializer get() called when not initialized, awaiting...")
    }

    return value.await()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun getOrNull(): T? {
    if (value.isCompleted) {
      if (logStates) {
        Log.d(tag, "SuspendableInitializer getOrNull() called when already initialized")
      }

      return value.getCompleted()
    }

    if (logStates) {
      Log.d(tag, "SuspendableInitializer getOrNull() called when not initialized, returning null")
    }

    return null
  }

  fun invokeAfterInitialized(func: (Throwable?) -> Unit) {
    if (value.isCompleted) {
      if (logStates) {
        Log.d(tag, "SuspendableInitializer invokeAfterInitialized() called when already initialized")
      }

      func(error.get())
      return
    }

    synchronized(this) {
      toRunAfterInitialized.add(func)
    }

    if (logStates) {
      Log.d(tag, "SuspendableInitializer invokeAfterInitialized() called, new callback added")
    }
  }

  suspend fun <T : Any?> invokeWhenInitialized(func: suspend () -> T): T {
    if (logStates) {
      Log.d(tag, "SuspendableInitializer afterInitialized() called, before awaitUntilInitialized")
    }

    awaitUntilInitialized()

    if (logStates) {
      Log.d(tag, "SuspendableInitializer afterInitialized() called, after awaitUntilInitialized")
    }

    return func()
  }

  private fun invokeAllCallbacks(error: Throwable?) {
    val copyOfCallbacks = synchronized(this) {
      val copy = toRunAfterInitialized.toList()
      toRunAfterInitialized.clear()

      return@synchronized copy
    }

    if (logStates) {
      Log.d(tag, "SuspendableInitializer invokeAllCallbacks() called, before copyOfCallbacks.forEach")
    }

    copyOfCallbacks.forEach { func -> func.invoke(error) }

    if (logStates) {
      Log.d(tag, "SuspendableInitializer invokeAllCallbacks() called, after copyOfCallbacks.forEach")
    }
  }
}