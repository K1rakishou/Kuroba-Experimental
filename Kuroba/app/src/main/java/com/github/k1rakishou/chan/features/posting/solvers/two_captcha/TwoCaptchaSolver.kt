package com.github.k1rakishou.chan.features.posting.solvers.two_captcha

import androidx.annotation.GuardedBy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.common.JsonConversionResult
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.suspendConvertIntoJsonObject
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.google.gson.Gson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.*
import java.util.concurrent.TimeUnit

class TwoCaptchaSolver(
  private val isDevBuild: Boolean,
  private val gson: Gson,
  private val siteManager: SiteManager,
  private val proxiedOkHttpClient: ProxiedOkHttpClient
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val solverAccountInfo = AccountInfo()

  private val activeRequests = mutableMapOf<ChanDescriptor, ActiveCaptchaRequest>()

  val name: String
    get() = "2captcha"
  val enabled: Boolean
    get() = ChanSettings.twoCaptchaSolverEnabled.get()
  val url: String
    get() = ChanSettings.twoCaptchaSolverUrl.get()
  val apiKey: String
    get() = ChanSettings.twoCaptchaSolverApiKey.get()
  val isLoggedIn: Boolean
    get() = enabled && url.isNotBlank() && apiKey.isNotBlank()

  private val actualUrlOrNull: HttpUrl?
    get() = url.toHttpUrlOrNull()

  suspend fun cancelAll() {
    mutex.withLock { activeRequests.clear() }
  }

  suspend fun cancel(chanDescriptor: ChanDescriptor) {
    mutex.withLock { activeRequests.remove(chanDescriptor) }
  }

  suspend fun solve(
    chanDescriptor: ChanDescriptor,
    updateChildNotificationFunc: suspend () -> Unit
  ): ModularResult<TwoCaptchaResult> {
    return ModularResult.Try {
      if (!enabled) {
        Logger.d(TAG, "solve() solver disabled")
        return@Try TwoCaptchaResult.SolverDisabled(name)
      }

      updateChildNotificationFunc()

      if (url.isBlank() || actualUrlOrNull == null) {
        Logger.d(TAG, "solve() bad url: \'$url\'")
        return@Try TwoCaptchaResult.SolverBadApiUrl(solverName = name, url = url)
      }

      if (apiKey.isBlank()) {
        Logger.d(TAG, "solve() bad apiKey")
        return@Try TwoCaptchaResult.SolverBadApiKey(solverName = name)
      }

      val siteDescriptor = chanDescriptor.siteDescriptor()

      val site = siteManager.bySiteDescriptor(siteDescriptor)
      if (site == null) {
        Logger.d(TAG, "solve() failed to find site by descriptor ${siteDescriptor}")
        return@Try TwoCaptchaResult.NotSupported(solverName = name, siteDescriptor = siteDescriptor)
      }

      val postAuthenticate = site.actions().postAuthenticate()
      val captchaType = postAuthenticate.type!!
      Logger.d(TAG, "solve() captchaType=$captchaType")

      val siteAuthentication = when (captchaType) {
        SiteAuthentication.Type.NONE -> {
          Logger.d(TAG, "solve() authentication not needed")
          return@Try TwoCaptchaResult.CaptchaNotNeeded(solverName = name, siteDescriptor = siteDescriptor)
        }
        SiteAuthentication.Type.GENERIC_WEBVIEW -> {
          Logger.d(TAG, "solve() selected authentication type is not supported: ${postAuthenticate.type}")
          return@Try TwoCaptchaResult.NotSupported(solverName = name, siteDescriptor = siteDescriptor)
        }
        SiteAuthentication.Type.CAPTCHA2,
        SiteAuthentication.Type.CAPTCHA2_NOJS,
        SiteAuthentication.Type.CAPTCHA2_INVISIBLE -> {
          postAuthenticate
        }
      }

      val activeRequest = mutex.withLock { activeRequests[chanDescriptor] }
      if (activeRequest != null) {
        return@Try checkSolution(activeRequest, chanDescriptor)
      } else {
        return@Try enqueueSolution(captchaType, siteAuthentication, chanDescriptor)
      }
    }
  }

  suspend fun getAccountBalance(forced: Boolean): ModularResult<TwoCaptchaBalanceResponse?> {
    return ModularResult.Try {
      Logger.d(TAG, "getAccountBalance(forced=$forced) success, got from cache")

      val twoCaptchaBalanceResponseCached = mutex.withLock {
        if (forced) {
          return@withLock null
        }

        val accountInfo = solverAccountInfo
        val twoCaptchaBalanceResponse = accountInfo.twoCaptchaBalanceResponse

        if (twoCaptchaBalanceResponse == null) {
          return@withLock null
        }

        if (accountInfo.shouldCheckBalance()) {
          return@withLock null
        }

        return@withLock twoCaptchaBalanceResponse
      }

      if (twoCaptchaBalanceResponseCached != null) {
        Logger.d(TAG, "getAccountBalance() success, got from cache")
        return@Try twoCaptchaBalanceResponseCached
      }

      val twoCaptchaBalanceResponseFresh = getAccountBalanceFromServer()
      if (twoCaptchaBalanceResponseFresh == null) {
        Logger.d(TAG, "getAccountBalance() getAccountBalanceFromServer() -> null")
        return@Try null
      }

      if (twoCaptchaBalanceResponseFresh.isOk()) {
        mutex.withLock {
          solverAccountInfo.twoCaptchaBalanceResponse = twoCaptchaBalanceResponseFresh
        }
      }

      Logger.d(TAG, "getAccountBalance() success, got from server")
      return@Try twoCaptchaBalanceResponseFresh
    }
  }

  private suspend fun checkSolution(
    activeRequest: ActiveCaptchaRequest,
    chanDescriptor: ChanDescriptor
  ): TwoCaptchaResult {
    // Captcha solution request was already sent, we need to check for solution now
    Logger.d(TAG, "checkSolution() request has already been enqueued, checking for the answer (req=${activeRequest.requestId})")

    val deltaTime = System.currentTimeMillis() - activeRequest.lastSolutionCheckTime
    if (deltaTime < SHORT_WAIT_TIME_MS) {
      val waitingMs = deltaTime - SHORT_WAIT_TIME_MS
      Logger.d(TAG, "checkSolution() can't check solution now, too early, waiting ${waitingMs}ms")

      return TwoCaptchaResult.WaitingForSolution(waitingMs)
    }

    val checkSolutionResponse = checkSolution(activeRequest.requestId)
    if (checkSolutionResponse == null) {
      Logger.d(TAG, "checkSolution() checkSolution(${activeRequest.requestId}) -> null")

      // Remove the request from active requests so we can send a new one on the next iteration
      cancel(chanDescriptor)

      return TwoCaptchaResult.UnknownError("Failed to check captcha solution, see logs for more info")
    }

    if (!checkSolutionResponse.isOk()) {
      if (checkSolutionResponse.isCaptchaNotReady()) {
        Logger.d(TAG, "checkSolution() got checkSolutionResponse, captcha is not ready yet, waiting ${SHORT_WAIT_TIME_MS}ms")
        return TwoCaptchaResult.WaitingForSolution(SHORT_WAIT_TIME_MS)
      }

      Logger.d(TAG, "checkSolution() checkSolutionResponse is not ok, checkSolutionResponse=$checkSolutionResponse")

      // Remove the request from active requests so we can send a new one on the next iteration
      cancel(chanDescriptor)

      return TwoCaptchaResult.BadCheckCaptchaSolutionResponse(checkSolutionResponse)
    }

    val prevActiveRequest = mutex.withLock { activeRequests.remove(chanDescriptor) }
    if (prevActiveRequest == null) {
      Logger.d(TAG, "checkSolution() prevActiveRequest==null, need to retry, waiting ${RETRY_WAIT_TIME_MS}ms")
      return TwoCaptchaResult.WaitingForSolution(RETRY_WAIT_TIME_MS)
    }

    Logger.d(TAG, "checkSolution() got check solution response")
    cancel(chanDescriptor)

    return TwoCaptchaResult.Solution(checkSolutionResponse)
  }

  private suspend fun enqueueSolution(
    captchaType: SiteAuthentication.Type,
    siteAuthentication: SiteAuthentication,
    chanDescriptor: ChanDescriptor
  ): TwoCaptchaResult {
    // Captcha solution request haven't been sent yet, we need to send it
    Logger.d(TAG, "enqueueSolution() request hasn't been enqueued yet, enqueueing it now")

    val siteKey = siteAuthentication.siteKey
    if (siteKey.isNullOrBlank()) {
      Logger.d(TAG, "enqueueSolution() bad site key: \'$siteKey\'")
      return TwoCaptchaResult.BadSiteKey(solverName = name)
    }

    val baseSiteUrl = siteAuthentication.baseUrl
    if (baseSiteUrl.isNullOrBlank()) {
      Logger.d(TAG, "enqueueSolution() bad base site url: \'$baseSiteUrl\'")
      return TwoCaptchaResult.BadSiteBaseUrl(solverName = name, url = baseSiteUrl)
    }

    val balanceResponse = getAccountBalance(forced = false)
      .peekError { error -> Logger.e(TAG, "getAccountBalance() error", error) }
      .valueOrNull()

    if (balanceResponse == null) {
      Logger.d(TAG, "enqueueSolution() getAccountBalance() -> null")
      return TwoCaptchaResult.UnknownError("Failed to check account balance, see logs for more info")
    }

    if (!balanceResponse.isOk()) {
      Logger.d(TAG, "enqueueSolution() balanceResponse is not ok, balanceResponse=$balanceResponse")
      return TwoCaptchaResult.BadBalanceResponse(balanceResponse)
    }

    val balance = balanceResponse.balance
    if (balance == null || balance < MIN_ACCOUNT_BALANCE) {
      Logger.d(TAG, "enqueueSolution() bad balance: $balance")
      return TwoCaptchaResult.BadBalance(balance)
    }

    Logger.d(TAG, "enqueueSolution() current balance=${balance}")

    val enqueueSolveCaptchaResponse = sendEnqueueSolveCaptchaRequest(siteKey, baseSiteUrl, captchaType)
    if (enqueueSolveCaptchaResponse == null) {
      Logger.d(TAG, "enqueueSolution() enqueueSolveCaptchaResponse() -> null")
      return TwoCaptchaResult.UnknownError("Failed to solve captcha, see logs for more info")
    }

    if (!enqueueSolveCaptchaResponse.isOk()) {
      if (enqueueSolveCaptchaResponse.isZeroBalanceError()) {
        Logger.d(TAG, "enqueueSolution() bad balance, balance=${enqueueSolveCaptchaResponse.response.requestRaw}")
        return TwoCaptchaResult.BadBalance(0f)
      }

      if (enqueueSolveCaptchaResponse.badApiKey()) {
        Logger.d(TAG, "enqueueSolution() bad apiKey url")
        return TwoCaptchaResult.SolverBadApiKey(name)
      }

      if (enqueueSolveCaptchaResponse.badUrl()) {
        Logger.d(TAG, "enqueueSolution() bad base site url: \'$baseSiteUrl\'")
        return TwoCaptchaResult.BadSiteBaseUrl(name, url)
      }

      if (enqueueSolveCaptchaResponse.badSiteKey()) {
        Logger.d(TAG, "enqueueSolution() bad site key: \'$siteKey\'")
        return TwoCaptchaResult.BadSiteKey(name)
      }

      if (enqueueSolveCaptchaResponse.noAvailableSlots()) {
        Logger.d(TAG, "enqueueSolution() no available slots, waiting ${SHORT_WAIT_TIME_MS}ms")
        return TwoCaptchaResult.WaitingForSolution(SHORT_WAIT_TIME_MS)
      }

      Logger.d(TAG, "enqueueSolution() enqueueSolveCaptchaResponse is not ok, enqueueSolveCaptchaResponse=$enqueueSolveCaptchaResponse")
      return TwoCaptchaResult.BadSolveCaptchaResponse(enqueueSolveCaptchaResponse)
    }

    val requestId = enqueueSolveCaptchaResponse.requestId
    if (requestId == null) {
      Logger.d(TAG, "enqueueSolution() bad enqueueSolveCaptchaResponse: $enqueueSolveCaptchaResponse")
      return TwoCaptchaResult.BadSolveCaptchaResponse(enqueueSolveCaptchaResponse)
    }

    mutex.withLock {
      check(!activeRequests.contains(chanDescriptor)) { "Already got request?!" }
      activeRequests[chanDescriptor] = ActiveCaptchaRequest(requestId = requestId)
    }

    Logger.d(TAG, "enqueueSolution() enqueued new solve captcha request, waiting ${LONG_WAIT_TIME_MS}ms")
    return TwoCaptchaResult.WaitingForSolution(LONG_WAIT_TIME_MS)
  }

  private suspend fun checkSolution(requestId: Long): TwoCaptchaCheckSolutionResponse? {
    val baseUrl = actualUrlOrNull
    if (baseUrl == null) {
      Logger.d(TAG, "checkSolution() baseUrl is bad, url=\'$url\'")
      return null
    }

    val fullUrl = baseUrl.newBuilder()
      .addEncodedPathSegment("res.php")
      .addEncodedQueryParameter("key", apiKey)
      .addEncodedQueryParameter("action", "get")
      .addEncodedQueryParameter("id", requestId.toString())
      .addEncodedQueryParameter("json", "1")
      .build()

    val request = Request.Builder()
      .url(fullUrl)
      .build()

    val result = proxiedOkHttpClient.okHttpClient()
      .suspendConvertIntoJsonObject<BaseSolverApiResponse>(request, gson)

    val solveCaptchaResponse = when (result) {
      is JsonConversionResult.HttpError -> {
        Logger.e(TAG, "checkSolution() Bad server response status: ${result.status}")
        return null
      }
      is JsonConversionResult.UnknownError -> {
        Logger.e(TAG, "checkSolution() Error", result.error)
        return null
      }
      is JsonConversionResult.Success -> TwoCaptchaCheckSolutionResponse.wrap(result.obj)
    }

    if (!solveCaptchaResponse.isOk()) {
      Logger.e(TAG, "checkSolution() server error: ${solveCaptchaResponse}")
    }

    return solveCaptchaResponse
  }

  private suspend fun sendEnqueueSolveCaptchaRequest(
    siteCaptchaKey: String,
    siteUrl: String,
    captchaType: SiteAuthentication.Type
  ): TwoCaptchaEnqueueSolveCaptchaResponse? {
    val baseUrl = actualUrlOrNull
    if (baseUrl == null) {
      Logger.d(TAG, "sendSolveCaptchaRequest() baseUrl is bad, url=\'$url\'")
      return null
    }

    val invisibleCaptchaParam = if (captchaType == SiteAuthentication.Type.CAPTCHA2_INVISIBLE) {
      "1"
    } else {
      "0"
    }

    val fullUrl = baseUrl.newBuilder()
      .addEncodedPathSegment("in.php")
      .addEncodedQueryParameter("key", apiKey)
      .addEncodedQueryParameter("method", "userrecaptcha")
      .addEncodedQueryParameter("googlekey", siteCaptchaKey)
      .addEncodedQueryParameter("pageurl", siteUrl)
      .addEncodedQueryParameter("invisible", invisibleCaptchaParam)
      .addEncodedQueryParameter("json", "1")
      .build()

    val request = Request.Builder()
      .url(fullUrl)
      .build()

    val result = proxiedOkHttpClient.okHttpClient()
      .suspendConvertIntoJsonObject<BaseSolverApiResponse>(request, gson)

    val solveCaptchaResponse = when (result) {
      is JsonConversionResult.HttpError -> {
        Logger.e(TAG, "sendSolveCaptchaRequest() Bad server response status: ${result.status}")
        return null
      }
      is JsonConversionResult.UnknownError -> {
        Logger.e(TAG, "sendSolveCaptchaRequest() Error", result.error)
        return null
      }
      is JsonConversionResult.Success -> TwoCaptchaEnqueueSolveCaptchaResponse.wrap(result.obj)
    }

    if (!solveCaptchaResponse.isOk()) {
      Logger.e(TAG, "sendSolveCaptchaRequest() server error: ${solveCaptchaResponse}")
    }

    return solveCaptchaResponse
  }

  private suspend fun getAccountBalanceFromServer(): TwoCaptchaBalanceResponse? {
    val baseUrl = actualUrlOrNull
    if (baseUrl == null) {
      Logger.d(TAG, "getAccountBalanceFromServer() baseUrl is bad, url=\'$url\'")
      return null
    }

    val fullUrl = baseUrl.newBuilder()
      .addEncodedPathSegment("res.php")
      .addEncodedQueryParameter("key", apiKey)
      .addEncodedQueryParameter("action", "getbalance")
      .addEncodedQueryParameter("json", "1")
      .build()

    val request = Request.Builder()
      .url(fullUrl)
      .build()

    val result = proxiedOkHttpClient.okHttpClient()
      .suspendConvertIntoJsonObject<BaseSolverApiResponse>(request, gson)

    val balanceResponse = when (result) {
      is JsonConversionResult.HttpError -> {
        Logger.e(TAG, "getAccountBalanceFromServer() Bad server response status: ${result.status}")
        return null
      }
      is JsonConversionResult.UnknownError -> {
        Logger.e(TAG, "getAccountBalanceFromServer() Error", result.error)
        return null
      }
      is JsonConversionResult.Success -> TwoCaptchaBalanceResponse.wrap(result.obj)
    }

    if (!balanceResponse.isOk()) {
      Logger.e(TAG, "getAccountBalanceFromServer() server error: ${balanceResponse}")
    }

    return balanceResponse
  }

  private data class AccountInfo(
    val lastBalanceCheckTimeMs: Long = 0L,
    var twoCaptchaBalanceResponse: TwoCaptchaBalanceResponse? = null
  ) {
    fun shouldCheckBalance(): Boolean {
      return (System.currentTimeMillis() - lastBalanceCheckTimeMs) > BALANCE_CHECK_INTERVAL
    }
  }

  companion object {
    private const val TAG = "TwoCaptchaSolver"
    private val BALANCE_CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(1)

    const val LONG_WAIT_TIME_MS = 15_000L
    private const val SHORT_WAIT_TIME_MS = 5_000L
    private const val RETRY_WAIT_TIME_MS = 1_000L
    private const val MIN_ACCOUNT_BALANCE = 0.001f
  }

}