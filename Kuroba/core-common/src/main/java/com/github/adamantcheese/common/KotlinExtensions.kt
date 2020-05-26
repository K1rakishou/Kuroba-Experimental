package com.github.adamantcheese.common

import com.github.adamantcheese.common.ModularResult.Companion.Try
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun OkHttpClient.suspendCall(request: Request): Response {
  return suspendCancellableCoroutine { continuation ->
    val call = newCall(request)

    continuation.invokeOnCancellation {
      ModularResult.Try { call.cancel() }.ignore()
    }

    call.enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        continuation.resumeWithException(e)
      }

      override fun onResponse(call: Call, response: Response) {
        continuation.resume(response)
      }
    })
  }
}

public inline fun <T, R> Iterable<T>.flatMapNotNull(transform: (T) -> Iterable<R>?): List<R> {
  return flatMapNotNullTo(ArrayList<R>(), transform)
}

public inline fun <T, R, C : MutableCollection<in R>> Iterable<T>.flatMapNotNullTo(destination: C, transform: (T) -> Iterable<R>?): C {
  for (element in this) {
    val list = transform(element)

    if (list != null) {
      destination.addAll(list)
    }
  }
  return destination
}

public inline fun <T, R> Collection<T>.flatMapIndexed(transform: (Int, T) -> Collection<R>): List<R> {
  val destination = mutableListOf<R>()

  for ((index, element) in this.withIndex()) {
    val list = transform(index, element)

    destination.addAll(list)
  }

  return destination
}

@Suppress("RedundantAsync")
public suspend fun <T> CoroutineScope.myAsyncSafe(func: suspend () -> T): ModularResult<T> {
  return supervisorScope {
    Try { async { func() }.await() }
  }
}

@Suppress("RedundantAsync")
public suspend fun <T> CoroutineScope.myAsync(func: suspend () -> T): T {
  return supervisorScope {
    async { func() }.await()
  }
}