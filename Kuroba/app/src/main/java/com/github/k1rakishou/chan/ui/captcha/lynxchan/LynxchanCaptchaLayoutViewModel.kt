package com.github.k1rakishou.chan.ui.captcha.lynxchan

import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanSite
import com.github.k1rakishou.chan.ui.captcha.lynxchan.pow.LynxchanProofOfWork
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils.asFormattedToken
import com.github.k1rakishou.common.isContentTypeApplicationJson
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.removeAllAfterFirstInclusive
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import javax.inject.Inject

class LynxchanCaptchaLayoutViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val proxiedOkHttpClient: RealProxiedOkHttpClient,
  private val siteManager: SiteManager,
  private val moshi: Moshi,
) : BaseViewModel() {

  var captchaInfoToShow = mutableStateOf<AsyncData<LynxchanCaptchaFull>>(AsyncData.NotInitialized)
  private var activeRequestCaptchaJob: Job? = null

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {

  }

  fun resetCaptchaForced() {
    captchaInfoToShow.value = AsyncData.NotInitialized
  }

  fun cleanup() {
    activeRequestCaptchaJob?.cancel()
    activeRequestCaptchaJob = null
  }

  fun requestCaptcha(
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha?,
    chanDescriptor: ChanDescriptor
  ) {
    if (lynxchanCaptcha == null) {
      captchaInfoToShow.value = AsyncData.Error(LynxchanCaptchaError("lynxchanCaptcha is null"))
      return
    }

    activeRequestCaptchaJob?.cancel()
    activeRequestCaptchaJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        captchaInfoToShow.value = AsyncData.Loading

        val needBlockBypass = needBlockBypass(
          lynxchanCaptcha = lynxchanCaptcha,
          chanDescriptor = chanDescriptor
        )

        val lynxchanCaptchaFull = requestCaptchaInternal(
          chanDescriptor = chanDescriptor,
          lynxchanCaptcha = lynxchanCaptcha,
          needBlockBypass = needBlockBypass
        )

        storeCaptchaIdCookie(
          chanDescriptor = chanDescriptor,
          cookie = lynxchanCaptchaFull.captchaInfo.kurobaCookie
        )

        captchaInfoToShow.value = AsyncData.Data(lynxchanCaptchaFull)
      } catch (error: Throwable) {
        Logger.error(TAG, error) { "Failed to load captcha for ${chanDescriptor}" }
        captchaInfoToShow.value = AsyncData.Error(error)
      }
    }
  }

  suspend fun verifyCaptcha(
    needBlockBypass: Boolean,
    chanDescriptor: ChanDescriptor,
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha,
    captchaInfo: LynxchanCaptchaFull,
    answer: String
  ): ModularResult<Boolean> {
    return withContext(Dispatchers.IO) {
      return@withContext ModularResult.Try {
        val verifyCaptchaEndpoint = if (needBlockBypass) {
          lynxchanCaptcha.renewBypassEndpoint
        } else {
          lynxchanCaptcha.solveCaptchaEndpoint
        }

        Logger.d(TAG, "verifyCaptcha(needBlockBypass: ${needBlockBypass}) verifyCaptchaEndpoint=$verifyCaptchaEndpoint")

        val captchaId = captchaInfo.captchaInfo.captchaId
        if (captchaId.isNullOrEmpty()) {
          throw LynxchanCaptchaError("No captchaId provided")
        }

        if (answer.isNullOrEmpty()) {
          throw LynxchanCaptchaError("No answer provided")
        }

        val requestBody = if (needBlockBypass) {
          MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("captcha", answer)
            .build()
        } else {
          MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("captchaId", captchaId)
            .addFormDataPart("answer", answer)
            .build()
        }

        val requestBuilder = Request.Builder()
          .url(verifyCaptchaEndpoint)
          .post(requestBody)

        val site = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())
        if (site != null) {
          site.requestModifier().modifyCaptchaGetRequest(
            site = site,
            requestBuilder = requestBuilder
          )
        }

        val response = proxiedOkHttpClient.okHttpClient().suspendCall(request = requestBuilder.build())
        if (!response.isSuccessful) {
          throw BadStatusResponseException(status = response.code)
        }

        val responseString = response.body.string()

        if (response.isContentTypeApplicationJson()) {
          // {"status":"hashcash","data":null}
          val blockBypassStatus = moshi.adapter(BlockBypassStatus::class.java).fromJson(responseString)
          if (blockBypassStatus == null) {
            throw LynxchanCaptchaError("Failed to extract BlockBypassStatus from '$responseString'")
          }

          if (needBlockBypass && blockBypassStatus.data == null && site is LynxchanSite) {
            val bypass = response.headers("Set-Cookie")
              .firstOrNull { setCookie -> setCookie.startsWith("bypass=") }
              ?.let { bypassCookie -> KurobaCookie.fromRawCookie(bypassCookie, "bypass")?.value }

            if (bypass != null && bypass.isBypassCookieValidForPOW()) {
              // Need to solve Proof Of Work
              Logger.debug(TAG) { "verifyCaptcha(needBlockBypass: ${needBlockBypass}) need to solve POW" }
              findProofOfWorkAndSubmit(bypass, chanDescriptor, lynxchanCaptcha).unwrap()
              return@Try false
            }

            throw LynxchanCaptchaError("bypassCookie is too short '${bypass.asFormattedToken()}'")
          }

          if (blockBypassStatus.isHashcash) {
            val lynxchanCaptchaFull = (captchaInfoToShow.value as? AsyncData.Data)?.data
            if (lynxchanCaptchaFull == null) {
              return@Try false
            }

            // Not sure about this one
            lynxchanCaptchaFull.needProofOfWork.value = true
            throw LynxchanCaptchaPOWError()
          }

          if (blockBypassStatus.isError) {
            val errorMessage = blockBypassStatus.data
            if (errorMessage.isNotNullNorEmpty()) {
              throw LynxchanCaptchaError("Error. Message=\'$errorMessage\'")
            }
          }

          if (blockBypassStatus.isOk) {
            extractAndStoreBypassCookie(chanDescriptor, response.headers)
          }

          return@Try blockBypassStatus.isOk
        }

        if (responseString.contains(CAPTCHA_SOLVED_MSG, ignoreCase = true)) {
          extractAndStoreBypassCookie(chanDescriptor, response.headers)
          return@Try true
        }

        Logger.error(TAG) { "Failed to verify captcha. response: '${responseString}'" }
        throw LynxchanCaptchaError("Failed to verify captcha. See logs for more info.")
      }
    }
  }

  private suspend fun needBlockBypass(
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha,
    chanDescriptor: ChanDescriptor
  ): Boolean {
    try {
      val requestBuilder = Request.Builder()
        .url(lynxchanCaptcha.bypassEndpoint)
        .get()

      val site = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())
        ?: throw LynxchanCaptchaError("Site ${chanDescriptor.siteDescriptor()} does not exist or not active")

      site.requestModifier().modifyCaptchaGetRequest(
        site = site,
        requestBuilder = requestBuilder
      )

      val response = proxiedOkHttpClient.okHttpClient().suspendCall(request = requestBuilder.build())
      if (!response.isSuccessful) {
        return false
      }

      // Endchan doesn't support this thing with json mode
      if (chanDescriptor.siteDescriptor().isEndchan() && !response.isContentTypeApplicationJson()) {
        return false
      }

      val body = response.body.string()

      val blockBypassWithStatusJson = moshi
        .adapter(BlockBypassWithStatusJson::class.java)
        .fromJson(body)
        ?: return false

      if (blockBypassWithStatusJson.status != "ok") {
        return false
      }

      if (blockBypassWithStatusJson.data.valid) {
        if (blockBypassWithStatusJson.data.validated != null
          && !blockBypassWithStatusJson.data.validated
          && site is LynxchanSite
        ) {
          val bypassCookie = site.bypassCookie.get()?.value
          if (bypassCookie != null && bypassCookie.isBypassCookieValidForPOW()) {
            // Need to solve Proof Of Work
            Logger.debug(TAG) { "needBlockBypass() need to solve POW" }
            findProofOfWorkAndSubmit(bypassCookie, chanDescriptor, lynxchanCaptcha).unwrap()
            return true
          }
        }
      }

      return !blockBypassWithStatusJson.data.valid
    } catch (error: Throwable) {
      Logger.e(TAG, "needBlockBypass() error", error)
      return false
    }
  }

  private suspend fun requestCaptchaInternal(
    chanDescriptor: ChanDescriptor,
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha,
    needBlockBypass: Boolean
  ): LynxchanCaptchaFull {
    var captchaEndpoint = lynxchanCaptcha.captchaEndpoint
    if (chanDescriptor.siteDescriptor().is8chanMoe()) {
      captchaEndpoint = captchaEndpoint.newBuilder()
        .addEncodedQueryParameter("d", Chan8MoeCaptchaTimeFormatter.print(DateTime.now()))
        .build()
    }

    val requestBuilder = Request.Builder()
      .url(captchaEndpoint)
      .get()

    val site = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())
      ?: throw LynxchanCaptchaError("Site ${chanDescriptor.siteDescriptor()} does not exist or not active")

    site.requestModifier().modifyCaptchaGetRequest(
      site = site,
      requestBuilder = requestBuilder
    )

    val refererUrl = site.resolvable().desktopUrl(chanDescriptor, null)
    if (refererUrl.isNotNullNorBlank()) {
      requestBuilder.header("Referer", refererUrl)
    }

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request = requestBuilder.build())
    if (!response.isSuccessful) {
      throw BadStatusResponseException(status = response.code)
    }

    val lynxchanCaptchaInfo = extractLynxchanCaptchaInfo(response)

    val imgByteArray = response.body.bytes()
    val imgImageBitmap = BitmapPainter(
      BitmapFactory.decodeByteArray(imgByteArray, 0, imgByteArray.size)
        .asImageBitmap()
    )

    return LynxchanCaptchaFull(
      needBlockBypass = needBlockBypass,
      captchaInfo = lynxchanCaptchaInfo,
      captchaImage = imgImageBitmap,
    )
  }

  private fun extractLynxchanCaptchaInfo(response: Response): LynxchanCaptchaFull.CaptchaInfo {
    val captchaData = run {
      val captchaData = mutableListOf<String>()
      var currentResponse: Response? = response

      while (currentResponse != null) {
        val setCookieHeader = currentResponse.headers
          .filter { (name, _) -> name.equals("set-cookie", ignoreCase = true) }
          .map { (_, value) -> value }

        if (setCookieHeader.isNotEmpty()) {
          captchaData.addAll(setCookieHeader)
          break
        }

        currentResponse = currentResponse.priorResponse
      }

      return@run captchaData
    }

    Logger.d(TAG, "extractLynxchanCaptcha() captchaData=$captchaData")

    val captchaIdCookieRaw = captchaData
      .firstOrNull { captchaCookie -> captchaCookie.startsWith("captchaid=", ignoreCase = true) }

    if (captchaIdCookieRaw == null || captchaIdCookieRaw.isEmpty()) {
        throw LynxchanCaptchaError("captchaIdCookieRaw is null or empty (captchaData: '${captchaData}')")
    }

    val captchaId = captchaIdCookieRaw.split("; ")
      .firstOrNull { part -> part.startsWith("captchaid=") }
      ?.removePrefix("captchaid=")

    if (captchaId.isNullOrBlank()) {
      throw LynxchanCaptchaError("captchaId is null or empty (captchaIdCookieRaw: '${captchaIdCookieRaw}')")
    }

    val kurobaCookie = KurobaCookie.fromRawCookie(captchaIdCookieRaw, "captchaid")
      ?: throw LynxchanCaptchaError("Failed to create KurobaCookie from '${captchaIdCookieRaw}'")

    val captchaExpirationTimeMillis = captchaData
      .firstOrNull { captchaCookie -> captchaCookie.startsWith("captchaexpiration=", ignoreCase = true) }
      ?.removePrefix("captchaexpiration=")
      ?.removeAllAfterFirstInclusive(delimiter = ';')
      ?.let { captchaExpirationString -> LYNXCHAN_CAPTCHA_DATE_PARSER.parseDateTime(captchaExpirationString).millis }

    return LynxchanCaptchaFull.CaptchaInfo(
      captchaId = captchaId,
      kurobaCookie = kurobaCookie,
      captchaExpirationTimeMillis = captchaExpirationTimeMillis
    )
  }

  private suspend fun findProofOfWorkAndSubmit(
    bypass: String,
    chanDescriptor: ChanDescriptor,
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha
  ): ModularResult<Unit> {
    return ModularResult.Try {
      val pow = LynxchanProofOfWork(bypass).find()
      if (pow == null) {
        throw FailedToDoPOW("Failed to find the POW solution")
      }

      val validateBypassEndpoint = lynxchanCaptcha.validateBypassEndpoint

      val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("code", pow.toString())
        .build()

      val requestBuilder = Request.Builder()
        .url(validateBypassEndpoint)
        .post(requestBody)

      val site = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())
      if (site != null) {
        site.requestModifier().modifyCaptchaGetRequest(
          site = site,
          requestBuilder = requestBuilder
        )
      }

      val response = proxiedOkHttpClient.okHttpClient().suspendCall(request = requestBuilder.build())
      if (!response.isSuccessful) {
        throw BadStatusResponseException(status = response.code)
      }

      val responseString = response.body.string()

      val blockBypassStatus = moshi
        .adapter(BlockBypassStatus::class.java)
        .fromJson(responseString)

      if (blockBypassStatus == null || !blockBypassStatus.isOk || blockBypassStatus.data != null) {
        Logger.error(TAG) { "Failed to submit POW solution. data: '${blockBypassStatus?.data}'" }
        return@Try
      }

      Logger.debug(TAG) { "Successfully submitted POW! You should be able to post now." }
    }
  }

  private fun extractAndStoreBypassCookie(
    chanDescriptor: ChanDescriptor,
    headers: Headers
  ) {
    val setCookieHeader = headers
      .filter { (name, _) -> name.equals("set-cookie", ignoreCase = true) }
      .map { (_, value) -> value }

    val bypassCookie = setCookieHeader
      .firstOrNull { captchaCookie -> captchaCookie.startsWith("bypass=", ignoreCase = true) }

    if (bypassCookie.isNullOrEmpty()) {
      Logger.debug(TAG) { "extractAndStoreBypassCookie() bypass not found" }
      return
    }

    val bypassKurobaCookie = KurobaCookie.fromRawCookie(bypassCookie, "bypass")
    if (bypassKurobaCookie == null) {
      Logger.debug(TAG) { "extractAndStoreBypassCookie() failed to create KurobaCookie from '${bypassCookie}'" }
      return
    }

    storeBypassCookie(chanDescriptor, bypassKurobaCookie)
  }

  private fun storeBypassCookie(chanDescriptor: ChanDescriptor, bypass: KurobaCookie) {
    val site = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())
      ?: return

    if (site !is LynxchanSite) {
      return
    }

    site.bypassCookie.set(bypass)
  }

  private fun storeCaptchaIdCookie(chanDescriptor: ChanDescriptor, cookie: KurobaCookie) {
    val site = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())
      ?: return

    if (site !is LynxchanSite) {
      return
    }

    site.captchaIdCookie.set(cookie)
  }

  class LynxchanCaptchaError(message: String) : ClientException(message)
  class LynxchanCaptchaPOWError : ClientException("Proof-of-work required to post")
  class FailedToDoPOW(message: String) : ClientException("Proof-of-work failed: $message")

  private fun String.isBypassCookieValidForPOW(): Boolean {
    val str = this
    // https://gitgud.io/LynxChan/PoWSolver/-/blob/master/src/PowSolver.java?ref_type=heads#L16
    return str.length >= 712
  }

  class LynxchanCaptchaFull(
    val needBlockBypass: Boolean,
    val captchaInfo: CaptchaInfo,
    val captchaImage: BitmapPainter
  ) {
    var needProofOfWork = mutableStateOf(false)
    var currentInputValue = mutableStateOf<String>("")

    data class CaptchaInfo(
      val captchaId: String,
      val captchaExpirationTimeMillis: Long?,
      val kurobaCookie: KurobaCookie,
    )
  }

  // {"status":"hashcash","data":null}
  @JsonClass(generateAdapter = true)
  data class BlockBypassStatus(
    @Json(name = "status") val status: String,
    @Json(name = "data") val data: String?
  ) {
    val isOk: Boolean = status.equals("ok", ignoreCase = true)
    val isHashcash: Boolean = status.equals("hashcash", ignoreCase = true)
    val isError: Boolean = status.equals("error", ignoreCase = true)
  }

  // {"status":"ok","data":{"valid":false,"mode":1}}
  @JsonClass(generateAdapter = true)
  data class BlockBypassWithStatusJson(
    @Json(name = "status") val status: String,
    @Json(name = "data") val data: BlockBypassJson
  )

  @JsonClass(generateAdapter = true)
  data class BlockBypassJson(
    @Json(name = "valid") val valid: Boolean,
    @Json(name = "validated") val validated: Boolean?,
    @Json(name = "mode") val mode: Int
  )

  class ViewModelFactory @Inject constructor(
    private val proxiedOkHttpClient: RealProxiedOkHttpClient,
    private val siteManager: SiteManager,
    private val moshi: Moshi,
  ) : ViewModelAssistedFactory<LynxchanCaptchaLayoutViewModel> {
    override fun create(handle: SavedStateHandle): LynxchanCaptchaLayoutViewModel {
      return LynxchanCaptchaLayoutViewModel(
        savedStateHandle = handle,
        proxiedOkHttpClient = proxiedOkHttpClient,
        siteManager = siteManager,
        moshi = moshi
      )
    }
  }

  companion object {
    private const val TAG = "LynxchanCaptchaLayoutViewModel"
    private const val CAPTCHA_SOLVED_MSG = "<title>Captcha solved.</title>"

    private var Chan8MoeCaptchaTimeFormatter =
      DateTimeFormat.forPattern("EEE MMM dd yyyy HH:mm:ss 'GMT' ZZ '(Indochina Time)'")

    //                                                            Thu, 18 Nov 2021 12:02:36 GMT
    private val LYNXCHAN_CAPTCHA_DATE_PARSER = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss zzz")
  }

}