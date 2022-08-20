package com.github.k1rakishou.chan.ui.captcha.lynxchan

import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanSite
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.removeAllAfterFirstInclusive
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Period
import org.joda.time.format.DateTimeFormat
import javax.inject.Inject

class LynxchanCaptchaLayoutViewModel : BaseViewModel() {

  @Inject
  lateinit var proxiedOkHttpClient: RealProxiedOkHttpClient
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var moshi: Moshi

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
    activeRequestCaptchaJob = mainScope.launch {
      try {
        captchaInfoToShow.value = AsyncData.Loading

        val needBlockBypass = needBlockBypass(
          lynxchanCaptcha = lynxchanCaptcha,
          chanDescriptor = chanDescriptor
        )

        val lynxchanCaptchaFull = requestCaptchaInternal(
          lynxchanCaptcha = lynxchanCaptcha,
          needBlockBypass = needBlockBypass
        )

        storeCaptchaIdCookie(
          chanDescriptor = chanDescriptor,
          captchaId = lynxchanCaptchaFull.lynxchanCaptchaJson.captchaId
        )

        captchaInfoToShow.value = AsyncData.Data(lynxchanCaptchaFull)
      } catch (error: Throwable) {
        captchaInfoToShow.value = AsyncData.Error(error)
      }
    }
  }

  private fun storeCaptchaIdCookie(chanDescriptor: ChanDescriptor, captchaId: String?) {
    if (captchaId.isNullOrEmpty()) {
      return
    }

    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
      ?: return

    if (site !is LynxchanSite) {
      return
    }

    if (site.captchaIdCookie.get().equals(captchaId, ignoreCase = true)) {
      return
    }

    site.captchaIdCookie.set(captchaId)
  }

  suspend fun verifyCaptcha(
    needBlockBypass: Boolean,
    chanDescriptor: ChanDescriptor,
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha,
    captchaInfo: LynxchanCaptchaFull,
    answer: String
  ): ModularResult<Boolean> {
    return ModularResult.Try {
      val verifyCaptchaEndpoint = if (needBlockBypass) {
        lynxchanCaptcha.verifyBypassEndpoint
      } else {
        lynxchanCaptcha.verifyCaptchaEndpoint
      }

      Logger.d(TAG, "verifyCaptcha(needBlockBypass=${needBlockBypass}) verifyCaptchaEndpoint=$verifyCaptchaEndpoint")

      val captchaId = captchaInfo.lynxchanCaptchaJson.captchaId
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

      val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
      if (site != null) {
        site.requestModifier().modifyCaptchaGetRequest(site = site, requestBuilder = requestBuilder)
      }

      val response = proxiedOkHttpClient.okHttpClient().suspendCall(request = requestBuilder.build())
      if (!response.isSuccessful) {
        throw BadStatusResponseException(status = response.code)
      }

      val responseBody = response.body
        ?: throw EmptyBodyResponseException()

      val responseString = responseBody.string()
      val contentType = responseBody.contentType()

      if (contentType != null && (contentType.type == JSON_CONTENT_TYPE.type && contentType.subtype == JSON_CONTENT_TYPE.subtype)) {
        // {"status":"hashcash","data":null}
        val blockBypassStatus = moshi.adapter(BlockBypassStatus::class.java).fromJson(responseString)
        if (blockBypassStatus == null) {
          throw LynxchanCaptchaError("Failed to extract BlockBypassStatus from '$responseString'")
        }

        if (blockBypassStatus.isHashcash) {
          val lynxchanCaptchaFull = (captchaInfoToShow.value as? AsyncData.Data)?.data
          if (lynxchanCaptchaFull == null) {
            return@Try false
          }

          lynxchanCaptchaFull.needProofOfWork.value = true
          throw LynxchanCaptchaPOWError()
        }

        if (blockBypassStatus.isError) {
          val errorMessage = blockBypassStatus.data
          if (errorMessage.isNotNullNorEmpty()) {
            throw LynxchanCaptchaError("Error. Message=\'$errorMessage\'")
          }
        }

        return@Try blockBypassStatus.isOk
      }

      if (!responseString.contains(CAPTCHA_SOLVED_MSG, ignoreCase = true)) {
        return@Try false
      }

      return@Try true
    }
  }

  private suspend fun needBlockBypass(
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha,
    chanDescriptor: ChanDescriptor
  ): Boolean {
    try {
      val needBlockBypass = kotlin.run {
        val requestBuilder = Request.Builder()
          .url(lynxchanCaptcha.bypassEndpoint)
          .get()

        val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
        if (site != null) {
          site.requestModifier().modifyCaptchaGetRequest(site = site, requestBuilder = requestBuilder)
        }

        val response = proxiedOkHttpClient.okHttpClient().suspendCall(request = requestBuilder.build())
        if (!response.isSuccessful) {
          return@run false
        }

        val type = response.body?.contentType()?.type
        val subtype = response.body?.contentType()?.subtype

        // Endchan doesn't support this thing with json mode
        if (type != JSON_CONTENT_TYPE.type || subtype != JSON_CONTENT_TYPE.subtype) {
          return@run false
        }

        val body = response.body?.string()
          ?: throw EmptyBodyResponseException()

        val blockBypassWithStatusJson = moshi.adapter(BlockBypassWithStatusJson::class.java).fromJson(body)
          ?: return@run false

        if (blockBypassWithStatusJson.status == "ok") {
          return@run false
        }

        return@run !blockBypassWithStatusJson.data.valid
      }

      return needBlockBypass
    } catch (error: Throwable) {
      Logger.e(TAG, "needBlockBypass() error", error)
      return false
    }
  }

  private suspend fun requestCaptchaInternal(
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha,
    needBlockBypass: Boolean
  ): LynxchanCaptchaFull {
    val captchaEndpoint = lynxchanCaptcha.captchaEndpoint

    val requestBuilder = Request.Builder()
      .url(captchaEndpoint)
      .get()

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request = requestBuilder.build())
    if (!response.isSuccessful) {
      throw BadStatusResponseException(status = response.code)
    }

    val lynxchanCaptchaJson = extractLynxchanCaptcha(response)
      ?: throw LynxchanCaptchaError("Failed to extract captcha info from headers")

    val imgByteArray = response.body?.bytes()
      ?: throw EmptyBodyResponseException()

    val imgImageBitmap = BitmapPainter(
      BitmapFactory.decodeByteArray(imgByteArray, 0, imgByteArray.size)
        .asImageBitmap()
    )

    return LynxchanCaptchaFull(
      needBlockBypass = needBlockBypass,
      lynxchanCaptchaJson = lynxchanCaptchaJson,
      captchaImage = imgImageBitmap
    )
  }

  private fun extractLynxchanCaptcha(response: Response): LynxchanCaptchaJson? {
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

    Logger.d(TAG, "extractLynxchanCaptcha() captchaData=$captchaData")

    val captchaDataSplit = captchaData
      .firstOrNull { captchaCookie -> captchaCookie.startsWith("captchaid=", ignoreCase = true) }
      ?.split(';')

    if (captchaDataSplit == null || captchaDataSplit.isNullOrEmpty()) {
      Logger.d(TAG, "extractLynxchanCaptcha() captchaDataSplit is null")
      return null
    }

    val containsMaxAge = captchaDataSplit
      .any { splitParam -> splitParam.contains("Max-Age=", ignoreCase = true) }

    if (containsMaxAge) {
      return extractLynxchanCaptchaWithMaxAge(captchaDataSplit)
    }

    return extractLynxchanCaptchaWithCaptchaExpiration(captchaData)
  }

  private fun extractLynxchanCaptchaWithMaxAge(captchaDataSplit: List<String>): LynxchanCaptchaJson? {
    val captchaId = captchaDataSplit
      .firstOrNull { splitData -> splitData.startsWith("captchaid=", ignoreCase = true) }
      ?.removePrefix("captchaid=")

    if (captchaId.isNullOrEmpty()) {
      Logger.d(TAG, "extractLynxchanCaptchaWithMaxAge() captchaId not found")
      return null
    }

    val maxAge = captchaDataSplit
      .firstOrNull { splitData -> splitData.startsWith("Max-Age=", ignoreCase = true) }

    if (maxAge == null || maxAge.isNullOrEmpty()) {
      Logger.d(TAG, "extractLynxchanCaptchaWithMaxAge() maxAge not found")
      return null
    }

    val cookieLifetimeSeconds = maxAge.split('=').getOrNull(1)?.toIntOrNull()
    if (cookieLifetimeSeconds == null) {
      Logger.d(TAG, "extractLynxchanCaptchaWithMaxAge() failed to extract cookieLifetimeSeconds, maxAge=\'maxAge\'")
      return null
    }

    val expiresAt = DateTime().plus(Period.seconds(cookieLifetimeSeconds))
    val captchaExpirationString = LynxchanCaptchaJson.LYNXCHAN_CAPTCHA_DATE_PARSER.print(expiresAt)

    return LynxchanCaptchaJson(
      _captchaId = captchaId,
      _expirationDate = captchaExpirationString
    )
  }

  private fun extractLynxchanCaptchaWithCaptchaExpiration(captchaData: List<String>): LynxchanCaptchaJson? {
    val captchaId = captchaData
      .firstOrNull { captchaCookie -> captchaCookie.startsWith("captchaid=", ignoreCase = true) }
      ?.removePrefix("captchaid=")
      ?.removeAllAfterFirstInclusive(delimiter = ';')

    if (captchaId.isNullOrEmpty()) {
      Logger.d(TAG, "extractLynxchanCaptchaWithCaptchaExpiration() captchaId not found")
      return null
    }

    val captchaExpirationString = captchaData
      .firstOrNull { captchaCookie -> captchaCookie.startsWith("captchaexpiration=", ignoreCase = true) }
      ?.removePrefix("captchaexpiration=")
      ?.removeAllAfterFirstInclusive(delimiter = ';')

    if (captchaExpirationString.isNullOrEmpty()) {
      Logger.d(TAG, "extractLynxchanCaptchaWithCaptchaExpiration() captchaExpirationString not found")
      return null
    }

    try {
      LynxchanCaptchaJson.LYNXCHAN_CAPTCHA_DATE_PARSER.parseDateTime(captchaExpirationString)
    } catch (error: Throwable) {
      Logger.e(TAG, "extractLynxchanCaptchaWithCaptchaExpiration() parseDateTime(\'$captchaExpirationString\') error", error)
      return null
    }

    return LynxchanCaptchaJson(
      _captchaId = captchaId,
      _expirationDate = captchaExpirationString
    )
  }

  class LynxchanCaptchaError(message: String) : Error(message)
  class LynxchanCaptchaPOWError : Error("Proof-of-work required to post")

  class LynxchanCaptchaFull(
    val needBlockBypass: Boolean,
    val lynxchanCaptchaJson: LynxchanCaptchaJson,
    val captchaImage: BitmapPainter
  ) {
    var needProofOfWork = mutableStateOf(false)
    var currentInputValue = mutableStateOf<String>("")
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
    @Json(name = "mode") val mode: Int
  )

  @JsonClass(generateAdapter = true)
  data class LynxchanCaptchaJson(
    @Json(name = "captchaId") val _captchaId: String?,
    @Json(name = "expirationDate") val _expirationDate: String?
  ) {
    val captchaId: String?
      get() = _captchaId

    val expirationTimeMillis: Long?
      get() {
        if (_expirationDate == null) {
          return null
        }

        try {
          val theirTime = LYNXCHAN_CAPTCHA_DATE_PARSER.parseDateTime(_expirationDate)
          val ourTimeInTheirTimezone = DateTime.now(DateTimeZone.forID(theirTime.zone.id))

          val remainingMillis = theirTime.millis - ourTimeInTheirTimezone.millis
          if (remainingMillis <= 0) {
            return null
          }

          return remainingMillis
        } catch (error: Throwable) {
          return null
        }
      }

    companion object {
      //                                                            Thu, 18 Nov 2021 12:02:36 GMT
      val LYNXCHAN_CAPTCHA_DATE_PARSER = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss zzz")
    }
  }

  companion object {
    private const val TAG = "LynxchanCaptchaLayoutViewModel"
    private const val CAPTCHA_SOLVED_MSG = "<title>Captcha solved.</title>"

    private val JSON_CONTENT_TYPE = "application/json".toMediaType()
  }

}