package com.github.k1rakishou.common

import androidx.annotation.GuardedBy
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

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
  private val enabledLogs: Boolean = true,
  private val value: CompletableDeferred<T> = CompletableDeferred()
) {
  private val error = AtomicReference<Throwable?>(null)

  @GuardedBy("itself")
  private val waiters = ArrayList<(Throwable?) -> Unit>()

  fun initWithValue(newValue: T) {
    logInternal("SuspendableInitializer initWithValue() called")

    if (!value.complete(newValue)) {
      logInternal("SuspendableInitializer initWithValue() already completed, exiting")
      return
    }

    notifyAllWaiters()
    logInternal("SuspendableInitializer initWithValue() done")
  }

  fun initWithError(exception: Throwable) {
    logErrorInternal("SuspendableInitializer initWithError() called")
    error.set(exception)

    if (!value.completeExceptionally(exception)) {
      logErrorInternal("SuspendableInitializer initWithError() already completed, exiting")
      return
    }

    notifyAllWaiters(exception)
    logErrorInternal("SuspendableInitializer initWithError() done")
  }

  fun initWithModularResult(modularResult: ModularResult<T>) {
    logInternal("SuspendableInitializer initWithModularResult() called")

    when (modularResult) {
      is ModularResult.Value -> initWithValue(modularResult.value)
      is ModularResult.Error -> initWithError(modularResult.error)
    }
  }

  suspend fun awaitUntilInitialized() {
    if (value.isCompleted) {
      val throwable = error.get()
      notifyAllWaiters(throwable = throwable)

      if (throwable != null) {
        throw throwable
      }

      // This will throw if it was initialized with an error
      value.getCompleted()
      return
    }

    logInternal("SuspendableInitializer awaitUntilInitialized() called when not initialized, awaiting...")

    val startTime = System.currentTimeMillis()
    withContext(NonCancellable) { value.await() }
    val diffTime = System.currentTimeMillis() - startTime

    logInternal("SuspendableInitializer awaitUntilInitialized() called when not initialized, " +
      "done (diffTime=${diffTime}ms)")

    return
  }

  fun isInitialized(): Boolean {
    if (!value.isCompleted) {
      return false
    }

    error.get()?.let { initializationError -> throw initializationError }
    return true
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun get(): T {
    if (value.isCompleted) {
      val throwable = error.get()
      if (throwable != null) {
        throw throwable
      }

      return value.getCompleted()
    }

    logInternal("SuspendableInitializer get() called when not initialized, awaiting...")
    return value.await()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun getOrNull(): T? {
    if (value.isCompleted) {
      val throwable = error.get()
      if (throwable != null) {
        return null
      }

      return value.getCompleted()
    }

    logInternal("SuspendableInitializer getOrNull() called when not initialized, returning null")
    return null
  }

  fun runWhenInitialized(func: (Throwable?) -> Unit) {
    if (isInitialized()) {
      func(null)
      notifyAllWaiters(null)
      return
    }

    synchronized(this) {
      waiters += func
    }
  }

  private fun notifyAllWaiters(throwable: Throwable? = null) {
    val waitersCopy = synchronized(waiters) {
      logInternal("notifyAllWaiters throwable==null: ${throwable == null}, waiters=${waiters.size}")
      val copy = ArrayList(waiters)
      waiters.clear()

      return@synchronized copy
    }

    waitersCopy.forEach { waiter -> waiter.invoke(throwable) }
  }

  private fun logInternal(message: String) {
    if (enabledLogs) {
      Logger.d(tag, message)
    }
  }

  private fun logErrorInternal(message: String) {
    if (enabledLogs) {
      Logger.e(tag, message)
    }
  }
}