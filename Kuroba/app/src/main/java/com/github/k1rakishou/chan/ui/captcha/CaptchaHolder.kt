package com.github.k1rakishou.chan.ui.captcha

import android.os.Handler
import android.os.Looper
import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayoutViewModel
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.unreachable
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CaptchaHolder {
  private val running = AtomicBoolean(false)
  private val mainThreadHandler = Handler(Looper.getMainLooper())
  private var timer: Timer? = null
  private var catalogCaptchaValidationListener: CaptchaValidationListener? = null
  private var threadCaptchaValidationListener: CaptchaValidationListener? = null

  @GuardedBy("itself")
  private val captchaQueue: MutableList<CaptchaInfo> = ArrayList()

  fun setListener(descriptor: ChanDescriptor, listener: CaptchaValidationListener?) {
    BackgroundUtils.ensureMainThread()

    if (descriptor.isCatalogDescriptor()) {
      catalogCaptchaValidationListener = listener
    } else {
      threadCaptchaValidationListener = listener
    }

    notifyListener()
  }

  fun clearCallbacks() {
    BackgroundUtils.ensureMainThread()
    catalogCaptchaValidationListener = null
    threadCaptchaValidationListener = null
  }

  @JvmOverloads
  fun addNewToken(token: String, tokenLifetime: Long = RECAPTCHA_TOKEN_LIVE_TIME) {
    addNewSolution(CaptchaSolution.SimpleTokenSolution(token), tokenLifetime)
  }

  @JvmOverloads
  fun addNewSolution(solution: CaptchaSolution, tokenLifetime: Long = RECAPTCHA_TOKEN_LIVE_TIME) {
    BackgroundUtils.ensureMainThread()
    removeNotValidTokens()

    if (tokenLifetime <= 0) {
      return
    }

    synchronized(captchaQueue) {
      captchaQueue.add(
        0,
        CaptchaInfo(
          solution = solution,
          validUntil = tokenLifetime + System.currentTimeMillis()
        )
      )

      Logger.d(TAG, "A new token has been added, validCount: ${captchaQueue.size}, " +
              "solution=$solution, tokenLifetime=$tokenLifetime")
    }

    notifyListener()
    startTimer()
  }

  fun hasSolution(): Boolean {
    BackgroundUtils.ensureMainThread()
    removeNotValidTokens()

    synchronized(captchaQueue) {
      if (captchaQueue.isEmpty()) {
        stopTimer()
        return false
      }
    }

    return true
  }

  val solution: CaptchaSolution?
    get() {
      BackgroundUtils.ensureMainThread()
      removeNotValidTokens()

      synchronized(captchaQueue) {
        if (captchaQueue.isEmpty()) {
          stopTimer()
          return null
        }

        val lastIndex = captchaQueue.size - 1
        val solution = captchaQueue[lastIndex].solution

        captchaQueue.removeAt(lastIndex)
        Logger.d(TAG, "getToken() solution=${solution}")

        notifyListener()
        return solution
      }
    }

  fun generateCaptchaUuid(): String {
    while (true) {
      val uuid = UUID.randomUUID().toString()

      if (captchaQueue.none { captchaInfo -> captchaInfo.uuid == uuid }) {
        return uuid
      }
    }

    unreachable()
  }

  private fun startTimer() {
    if (running.compareAndSet(false, true)) {
      timer = Timer()
      timer!!.scheduleAtFixedRate(CheckCaptchaFreshnessTask(), INTERVAL, INTERVAL)

      Logger.d(TAG, "Timer started")
    }
  }

  private fun stopTimer() {
    if (running.compareAndSet(true, false)) {
      timer!!.cancel()
      timer!!.purge()

      Logger.d(TAG, "Timer stopped")
    }
  }

  private fun removeNotValidTokens() {
    val now = System.currentTimeMillis()
    var captchasCountDecreased = false

    synchronized(captchaQueue) {
      val it = captchaQueue.listIterator(captchaQueue.size)

      while (it.hasPrevious()) {
        val captchaInfo = it.previous()
        if (now > captchaInfo.validUntil) {
          captchasCountDecreased = true
          it.remove()

          Logger.d(TAG, "Captcha token got expired, " +
            "now: ${CaptchaInfo.captchaDateFormat.format(now)}, " +
            "token validUntil: ${CaptchaInfo.captchaDateFormat.format(captchaInfo.validUntil)}, " +
            "solution: ${captchaInfo.solution}")
        }
      }

      if (captchaQueue.isEmpty()) {
        stopTimer()
      }
    }

    if (captchasCountDecreased) {
      notifyListener()
    }
  }

  private fun notifyListener() {
    mainThreadHandler.post {
      var count = 0

      synchronized(captchaQueue) { count = captchaQueue.size }
      if (catalogCaptchaValidationListener != null) {
        catalogCaptchaValidationListener!!.onCaptchaCountChanged(count)
      }

      if (threadCaptchaValidationListener != null) {
        threadCaptchaValidationListener!!.onCaptchaCountChanged(count)
      }
    }
  }

  private inner class CheckCaptchaFreshnessTask : TimerTask() {
    override fun run() {
      removeNotValidTokens()
    }
  }

  interface CaptchaValidationListener {
    fun onCaptchaCountChanged(validCaptchaCount: Int)
  }

  companion object {
    private const val TAG = "CaptchaHolder"
    private const val INTERVAL: Long = 5000

    val RECAPTCHA_TOKEN_LIVE_TIME = TimeUnit.MINUTES.toMillis(2)
  }
}

sealed class CaptchaSolution {
  fun isTokenEmpty(): Boolean {
    return when (this) {
      is SimpleTokenSolution -> token.isBlank()
      is ChallengeWithSolution -> solution.isBlank() && !is4chanNoopChallenge()
    }
  }

  data class SimpleTokenSolution(
    val token: String
  ) : CaptchaSolution() {
    override fun toString(): String {
      return "SimpleTokenSolution{token=$token}"
    }
  }

  data class ChallengeWithSolution(
    val uuid: String,
    val challenge: String,
    val solution: String
  ) : CaptchaSolution() {
    fun is4chanNoopChallenge(): Boolean {
      return challenge.equals(Chan4CaptchaLayoutViewModel.NOOP_CHALLENGE, ignoreCase = true)
    }

    override fun toString(): String {
      return "ChallengeWithSolution{uuid=${uuid}, challenge=$challenge, solution=$solution}"
    }
  }
}

class CaptchaInfo(
  val solution: CaptchaSolution,
  val validUntil: Long
) {
  val uuid: String?
    get() {
      return when (val sol = solution) {
        is CaptchaSolution.ChallengeWithSolution -> sol.uuid
        is CaptchaSolution.SimpleTokenSolution -> null
      }
    }

  override fun hashCode(): Int {
    return (solution.hashCode()
      + 31 * (validUntil and 0x00000000FFFFFFFFL).toInt()
      + 31 * (validUntil shr 32 and 0x00000000FFFFFFFFL).toInt())
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) {
      return false
    }
    if (this === other) {
      return true
    }
    if (this.javaClass != other.javaClass) {
      return false
    }
    val otherCaptchaInfo = other as CaptchaInfo
    return solution == otherCaptchaInfo.solution && validUntil == otherCaptchaInfo.validUntil
  }

  override fun toString(): String {
    return "validUntil: ${captchaDateFormat.format(validUntil)}, solution: ${solution}"
  }

  companion object {
    val captchaDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
  }
}