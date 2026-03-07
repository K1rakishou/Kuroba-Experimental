package com.github.k1rakishou.chan.ui.captcha.lynxchan.pow

import com.github.k1rakishou.common.StringUtils.asFormattedToken
import com.github.k1rakishou.common.awaitCatching
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class Chan8MoeProofOfWork(
  private val token: String,
  private val difficulty: Int,
  private val algorithm: Int,
) {
  suspend fun find(): Int? {
    Logger.debug(TAG) { "token: ${token.asFormattedToken()}, difficulty: ${difficulty}, algorithm: ${algorithm}" }

    return coroutineScope {
      val found = AtomicBoolean(false)
      val result = CompletableDeferred<Int?>()
      val iterations = AtomicInteger(-1)
      val numCoroutines = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)

      repeat(numCoroutines) { coroutineId ->
        launch(Dispatchers.Default) {
          var n = coroutineId

          val sha = if (algorithm == 512) "SHA-512" else "SHA-256"
          val bytes = if (algorithm == 512) 64 else 32
          val digest = MessageDigest.getInstance(sha)

          while (isActive && !found.get()) {
            val message = token + n
            digest.reset()
            val hash = digest.digest(message.toByteArray(Charsets.UTF_8))

            var bits = 0
            outer@ for (i in 0 until bytes) {
              if (bits >= difficulty) break

              for (bit in 7 downTo 0) {
                if ((hash[i].toInt() and (1 shl bit)) == 0) {
                  bits++

                  if (bits >= difficulty) {
                    break@outer
                  }
                } else {
                  break@outer
                }
              }
            }

            if (bits >= difficulty && found.compareAndSet(false, true)) {
              result.complete(n)
              return@launch
            }

            if (iterations.incrementAndGet() % 1024 == 0) {
              Logger.debug(TAG) { "iteration: ${iterations.get()}" }
            }

            n += numCoroutines
          }
        }
      }

      return@coroutineScope result.awaitCatching()
        .onSuccess { solution -> Logger.debug(TAG) { "Found solution: ${solution}" } }
        .onError { error -> Logger.error(TAG, error) { "Failed to find solution" } }
        .mapErrorToValue { null }
    }
  }

  companion object {
    private const val TAG = "Find8chanMoeProofOfWork"
  }
}