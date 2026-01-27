package com.github.k1rakishou.chan.ui.captcha.lynxchan.pow

import android.util.Base64
import com.github.k1rakishou.common.StringUtils.asFormattedToken
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.time.measureTime

class LynxchanProofOfWork(
  private val bypass: String
) {
  fun find(): Flow<Event> {
    return channelFlow {
      Logger.debug(TAG) { "bypass: ${bypass.asFormattedToken()}" }

      val session = bypass.substring(24, 24 + 344)
      val hash = bypass.substring(24 + 344)
      val targetHash = Base64.decode(hash.trim(), Base64.DEFAULT)
      val coresCount = Runtime.getRuntime().availableProcessors()
      val solution = AtomicInteger(-1)
      val iterations = AtomicInteger(-1)

      val time = measureTime {
        try {
          coroutineScope {
            (0..<coresCount)
              .map { index ->
                async(Dispatchers.Default) {
                  val secretFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")

                  val length = 256 * 8
                  val iter = 16384
                  val sessionArray = session.toCharArray()
                  var iteration = index

                  while (isActive && solution.get() == -1) {
                    val spec = PBEKeySpec(sessionArray, iteration.toString().toByteArray(), iter, length)
                    val attempt = secretFactory.generateSecret(spec).encoded

                    if (attempt.contentEquals(targetHash)) {
                      solution.set(iteration)
                      break
                    } else {
                      iteration += coresCount
                    }

                    if (iterations.incrementAndGet() % 16 == 0) {
                      send(Event.Update(iterations.get()))
                    }
                  }
                }
              }.awaitAll()
          }
        } catch (error: Throwable) {
          send(Event.Solution(-1))
          Logger.error(TAG, error) { "Failed to calculate POW" }
          return@channelFlow
        }
      }

      Logger.debug(TAG) { "Got POW: ${solution.get()}, took ${time}" }

      val value = solution.get()
        .takeIf { it >= 0 }
        ?: -1

      send(Event.Solution(value))
    }
  }

  sealed interface Event {
    data class Update(val iteration: Int) : Event
    data class Solution(val value: Int) : Event
  }

  companion object {
    private const val TAG = "LynxchanProofOfWork"
  }
}