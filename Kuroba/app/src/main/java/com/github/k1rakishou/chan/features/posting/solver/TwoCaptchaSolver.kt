package com.github.k1rakishou.chan.features.posting.solver

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
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

  private val actualUrlOrNull by lazy { url.toHttpUrlOrNull() }

  suspend fun cancelAll() {
    mutex.withLock { activeRequests.clear() }
  }

  suspend fun cancel(chanDescriptor: ChanDescriptor) {
    mutex.withLock { activeRequests.remove(chanDescriptor) }
  }

  suspend fun solve(chanDescriptor: ChanDescriptor): ModularResult<TwoCaptchaResult> {
    return ModularResult.Try {
      if (!enabled) {
        Logger.d(TAG, "solve() solver disabled")
        return@Try TwoCaptchaResult.SolverDisabled(name)
      }

      if (url.isBlank() || actualUrlOrNull == null) {
        Logger.d(TAG, "solve() bad url: \'$url\'")
        return@Try TwoCaptchaResult.SolverBadApiUrl(solverName = name, url = url)
      }

      if (apiKey.isBlank()) {
        Logger.d(TAG, "solve() apiKey url: \'$apiKey\'")
        return@Try TwoCaptchaResult.SolverBadApiKey(solverName = name)
      }

      val siteDescriptor = chanDescriptor.siteDescriptor()

      val site = siteManager.bySiteDescriptor(siteDescriptor)
      if (site == null) {
        Logger.d(TAG, "solve() failed to find site by descriptor ${siteDescriptor}")
        return@Try TwoCaptchaResult.NotSupported(solverName = name, siteDescriptor = siteDescriptor)
      }

      val postAuthenticate = site.actions().postAuthenticate()

      val siteAuthentication = when (postAuthenticate.type!!) {
        SiteAuthentication.Type.NONE -> {
          Logger.d(TAG, "solve() authentication not needed")
          return@Try TwoCaptchaResult.CaptchaNotNeeded(solverName = name, siteDescriptor = siteDescriptor)
        }
        SiteAuthentication.Type.GENERIC_WEBVIEW -> {
          Logger.d(TAG, "solve() selected authentication type is not supported: ${postAuthenticate.type}")
          return@Try TwoCaptchaResult.NotSupported(solverName = name, siteDescriptor = siteDescriptor)
        }
        SiteAuthentication.Type.CAPTCHA2,
        SiteAuthentication.Type.CAPTCHA2_NOJS -> {
          postAuthenticate
        }
      }

      if (isDevBuild) {
        Logger.d(TAG, "solve() apiKey=${apiKey}")
      }

      val activeRequest = mutex.withLock { activeRequests[chanDescriptor] }
      if (activeRequest != null) {
        return@Try checkSolution(activeRequest, chanDescriptor)
      } else {
        return@Try enqueueSolution(siteAuthentication, chanDescriptor)
      }
    }
  }

  suspend fun getAccountBalance(): ModularResult<TwoCaptchaBalanceResponse?> {
    return ModularResult.Try {
      val twoCaptchaBalanceResponseCached = mutex.withLock {
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

      mutex.withLock {
        solverAccountInfo.twoCaptchaBalanceResponse = twoCaptchaBalanceResponseFresh
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
      return TwoCaptchaResult.UnknownError("Failed to check captcha solution, see logs for more info")
    }

    if (!checkSolutionResponse.isOk()) {
      if (checkSolutionResponse.isCaptchaNotReady()) {
        Logger.d(TAG, "checkSolution() got checkSolutionResponse, captcha is not ready yet, waiting ${SHORT_WAIT_TIME_MS}ms")
        return TwoCaptchaResult.WaitingForSolution(SHORT_WAIT_TIME_MS)
      }

      Logger.d(TAG, "checkSolution() checkSolutionResponse is not ok, checkSolutionResponse=$checkSolutionResponse")
      return TwoCaptchaResult.BadCheckCaptchaSolutionResponse(checkSolutionResponse)
    }

    val prevActiveRequest = mutex.withLock { activeRequests.remove(chanDescriptor) }
    if (prevActiveRequest == null) {
      Logger.d(TAG, "checkSolution() prevActiveRequest==null, need to retry, waiting ${RETRY_WAIT_TIME_MS}ms")
      return TwoCaptchaResult.WaitingForSolution(RETRY_WAIT_TIME_MS)
    }

    Logger.d(TAG, "checkSolution() got check solution response")
    return TwoCaptchaResult.Solution(checkSolutionResponse)
  }

  private suspend fun enqueueSolution(
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

    val balanceResponse = getAccountBalance()
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
    if (balance == null || balance < 0.00001f) {
      Logger.d(TAG, "enqueueSolution() bad balance: $balance")
      return TwoCaptchaResult.BadBalance(balance)
    }

    Logger.d(TAG, "enqueueSolution() current balance=${balance}")

    val enqueueSolveCaptchaResponse = sendEnqueueSolveCaptchaRequest(siteKey, baseSiteUrl)
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
        Logger.d(TAG, "enqueueSolution() apiKey url: \'$apiKey\'")
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
    siteUrl: String
  ): TwoCaptchaEnqueueSolveCaptchaResponse? {
    val baseUrl = actualUrlOrNull
    if (baseUrl == null) {
      Logger.d(TAG, "sendSolveCaptchaRequest() baseUrl is bad, url=\'$url\'")
      return null
    }

    val fullUrl = baseUrl.newBuilder()
      .addEncodedPathSegment("in.php")
      .addEncodedQueryParameter("key", apiKey)
      .addEncodedQueryParameter("method", "userrecaptcha")
      .addEncodedQueryParameter("googlekey", siteCaptchaKey)
      .addEncodedQueryParameter("pageurl", siteUrl)
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

  data class ActiveCaptchaRequest(
    val requestId: Long
  ) {
    var lastSolutionCheckTime: Long = 0L
  }

  private data class AccountInfo(
    val lastBalanceCheckTimeMs: Long = 0L,
    var twoCaptchaBalanceResponse: TwoCaptchaBalanceResponse? = null
  ) {
    fun shouldCheckBalance(): Boolean {
      return (System.currentTimeMillis() - lastBalanceCheckTimeMs) > BALANCE_CHECK_INTERVAL
    }
  }

  sealed class TwoCaptchaResult {
    data class BadSiteKey(val solverName: String) : TwoCaptchaResult()
    data class SolverBadApiKey(val solverName: String) : TwoCaptchaResult()
    data class SolverDisabled(val solverName: String) : TwoCaptchaResult()
    data class SolverBadApiUrl(val solverName: String, val url: String) : TwoCaptchaResult()
    data class BadSiteBaseUrl(val solverName: String, val url: String?) : TwoCaptchaResult()
    data class NotSupported(val solverName: String, val siteDescriptor: SiteDescriptor) : TwoCaptchaResult()
    data class CaptchaNotNeeded(val solverName: String, val siteDescriptor: SiteDescriptor) : TwoCaptchaResult()
    data class BadBalance(val balance: Float?) : TwoCaptchaResult()

    data class UnknownError(val message: String) : TwoCaptchaResult()
    data class BadBalanceResponse(val twoCaptchaBalanceResponse: TwoCaptchaBalanceResponse) : TwoCaptchaResult()
    data class BadSolveCaptchaResponse(val twoCaptchaSolveCaptchaResponse: TwoCaptchaEnqueueSolveCaptchaResponse) : TwoCaptchaResult()
    data class BadCheckCaptchaSolutionResponse(val twoCaptchaCheckSolutionResponse: TwoCaptchaCheckSolutionResponse) : TwoCaptchaResult()
    data class WaitingForSolution(val waitTime: Long) : TwoCaptchaResult()
    data class Solution(val twoCaptchaCheckSolutionResponse: TwoCaptchaCheckSolutionResponse): TwoCaptchaResult()
  }

  data class BaseSolverApiResponse(
    @SerializedName("status")
    val status: Int,
    @SerializedName("request")
    val requestRaw: String,
    @SerializedName("error_text")
    val errorText: String?
  ) {
    fun isOk(): Boolean = status == 1

    fun errorTextOrDefault(): String = errorText ?: "No error text"
  }

  class TwoCaptchaEnqueueSolveCaptchaResponse private constructor(
    val response: BaseSolverApiResponse
  ) {
    val requestId: Long? by lazy {
      if (response.isOk()) {
        return@lazy response.requestRaw.toLongOrNull()
      }

      return@lazy null
    }

    fun badApiKey(): Boolean = response.requestRaw == "ERROR_KEY_DOES_NOT_EXIST"
    fun badUrl(): Boolean = response.requestRaw == "ERROR_PAGEURL"
    fun badSiteKey(): Boolean = response.requestRaw == "ERROR_GOOGLEKEY"
    fun isZeroBalanceError(): Boolean = response.requestRaw == "ERROR_ZERO_BALANCE"
    fun noAvailableSlots(): Boolean = response.requestRaw == "ERROR_NO_SLOT_AVAILABLE"

    fun isOk(): Boolean = response.isOk()

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as TwoCaptchaEnqueueSolveCaptchaResponse

      if (response != other.response) return false

      return true
    }

    override fun hashCode(): Int {
      return response.hashCode()
    }

    override fun toString(): String {
      return "TwoCaptchaEnqueueSolveCaptchaResponse(response=$response)"
    }

    companion object {
      fun wrap(response: BaseSolverApiResponse): TwoCaptchaEnqueueSolveCaptchaResponse {
        return TwoCaptchaEnqueueSolveCaptchaResponse(response)
      }
    }
  }

  class TwoCaptchaBalanceResponse private constructor(
    val response: BaseSolverApiResponse
  ) {
    val balance: Float? by lazy {
      if (response.isOk()) {
        return@lazy response.requestRaw.toFloatOrNull()
      }

      return@lazy null
    }

    fun isOk(): Boolean = response.isOk()

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as TwoCaptchaBalanceResponse

      if (response != other.response) return false

      return true
    }

    override fun hashCode(): Int {
      return response.hashCode()
    }

    override fun toString(): String {
      return "TwoCaptchaBalanceResponse(response=$response)"
    }

    companion object {
      fun wrap(response: BaseSolverApiResponse): TwoCaptchaBalanceResponse {
        return TwoCaptchaBalanceResponse(response)
      }
    }
  }

  class TwoCaptchaCheckSolutionResponse private constructor(
    val response: BaseSolverApiResponse
  ) {
    fun isOk(): Boolean = response.isOk()

    fun isCaptchaNotReady(): Boolean = response.requestRaw == "CAPCHA_NOT_READY"

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as TwoCaptchaCheckSolutionResponse

      if (response != other.response) return false

      return true
    }

    override fun hashCode(): Int {
      return response.hashCode()
    }

    override fun toString(): String {
      return "TwoCaptchaCheckSolutionResponse(response=$response)"
    }

    companion object {
      fun wrap(response: BaseSolverApiResponse): TwoCaptchaCheckSolutionResponse {
        return TwoCaptchaCheckSolutionResponse(response)
      }
    }
  }

  companion object {
    private const val TAG = "TwoCaptchaSolver"
    private val BALANCE_CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(1)

    const val LONG_WAIT_TIME_MS = 15_000L
    private const val SHORT_WAIT_TIME_MS = 5_000L
    private const val RETRY_WAIT_TIME_MS = 1_000L
  }

}