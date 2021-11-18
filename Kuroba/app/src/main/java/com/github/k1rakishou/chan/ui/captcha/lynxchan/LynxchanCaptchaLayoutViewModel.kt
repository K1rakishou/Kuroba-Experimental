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
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.MultipartBody
import okhttp3.Request
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import javax.inject.Inject

class LynxchanCaptchaLayoutViewModel : BaseViewModel() {

  @Inject
  lateinit var proxiedOkHttpClient: RealProxiedOkHttpClient
  @Inject
  lateinit var siteManager: SiteManager

  var captchaInfoToShow = mutableStateOf<AsyncData<LynxchanCaptchaFull>>(AsyncData.NotInitialized)
  private var activeJob: Job? = null

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {

  }

  fun resetCaptchaForced() {
    captchaInfoToShow.value = AsyncData.NotInitialized
  }

  fun cleanup() {
    activeJob?.cancel()
    activeJob = null
  }

  fun requestCaptcha(
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha?,
    chanDescriptor: ChanDescriptor
  ) {
    if (lynxchanCaptcha == null) {
      captchaInfoToShow.value = AsyncData.Error(LynxchanCaptchaError("lynxchanCaptcha is null"))
      return
    }

    activeJob?.cancel()
    activeJob = mainScope.launch {
      val lynxchanCaptchaResult = ModularResult.Try {
        requestCaptchaInternal(lynxchanCaptcha, chanDescriptor)
      }

      captchaInfoToShow.value = when (lynxchanCaptchaResult) {
        is ModularResult.Error -> AsyncData.Error(lynxchanCaptchaResult.error)
        is ModularResult.Value -> AsyncData.Data(lynxchanCaptchaResult.value)
      }
    }
  }

  suspend fun verifyCaptcha(
    chanDescriptor: ChanDescriptor,
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha,
    captchaInfo: LynxchanCaptchaFull,
    answer: String
  ): ModularResult<Boolean> {
    return ModularResult.Try {
      val verifyCaptchaEndpoint = lynxchanCaptcha.verifyCaptchaEndpoint

      val captchaId = captchaInfo.lynxchanCaptchaJson.captchaId
      if (captchaId.isNullOrEmpty()) {
        throw LynxchanCaptchaError("No captchaId provided")
      }

      if (answer.isNullOrEmpty()) {
        throw LynxchanCaptchaError("No answer provided")
      }

      val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("captchaId", captchaId)
        .addFormDataPart("answer", answer)
        .build()

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

      val responseString = response.body?.string()
        ?: throw EmptyBodyResponseException()

      if (!responseString.contains(CAPTCHA_SOLVED_MSG, ignoreCase = true)) {
        return@Try false
      }

      return@Try true
    }
  }

  private suspend fun requestCaptchaInternal(
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha,
    chanDescriptor: ChanDescriptor
  ): LynxchanCaptchaFull {
    val captchaEndpoint = lynxchanCaptcha.getCaptchaEndpoint

    val requestBuilder = Request.Builder()
      .url(captchaEndpoint)
      .get()

    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    if (site != null) {
      site.requestModifier().modifyCaptchaGetRequest(site = site, requestBuilder = requestBuilder)
    }

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request = requestBuilder.build())
    if (!response.isSuccessful) {
      throw BadStatusResponseException(status = response.code)
    }

    val imgByteArray = response.body?.bytes()
      ?: throw EmptyBodyResponseException()

    val lynxchanCaptchaJson = extractLynxchanCaptcha(response.headers)
      ?: throw LynxchanCaptchaError("Failed to extract captcha info from headers")

    val imgImageBitmap = BitmapPainter(
      BitmapFactory.decodeByteArray(imgByteArray, 0, imgByteArray.size)
        .asImageBitmap()
    )

    return LynxchanCaptchaFull(
      lynxchanCaptchaJson = lynxchanCaptchaJson,
      captchaImage = imgImageBitmap
    )
  }

  private fun extractLynxchanCaptcha(headers: Headers): LynxchanCaptchaJson? {
    val captchaData = headers
      .filter { (name, _) -> name.equals("set-cookie", ignoreCase = true) }
      .map { (_, value) -> value }

    Logger.d(TAG, "extractLynxchanCaptcha() captchaData=$captchaData")

    val captchaId = captchaData
      .firstOrNull { captchaCookie -> captchaCookie.startsWith("captchaid=") }
      ?.removePrefix("captchaid=")
      ?.removeSuffix("; path=/")

    if (captchaId.isNullOrEmpty()) {
      Logger.d(TAG, "extractLynxchanCaptcha() captchaId not found")
      return null
    }

    val captchaExpirationString = captchaData
      .firstOrNull { captchaCookie -> captchaCookie.startsWith("captchaexpiration=") }
      ?.removePrefix("captchaexpiration=")
      ?.removeSuffix("; path=/")

    if (captchaExpirationString.isNullOrEmpty()) {
      Logger.d(TAG, "extractLynxchanCaptcha() captchaExpirationString not found")
      return null
    }

    try {
      LynxchanCaptchaJson.LYNXCHAN_CAPTCHA_DATE_PARSER.parseDateTime(captchaExpirationString)
    } catch (error: Throwable) {
      Logger.e(TAG, "extractLynxchanCaptcha() parseDateTime(\'$captchaExpirationString\') error", error)
      return null
    }

    return LynxchanCaptchaJson(
      _captchaId = captchaId,
      _expirationDate = captchaExpirationString
    )
  }

  class LynxchanCaptchaError(message: String) : Error(message)

  class LynxchanCaptchaFull(
    val lynxchanCaptchaJson: LynxchanCaptchaJson,
    val captchaImage: BitmapPainter
  ) {
    var currentInputValue = mutableStateOf<String>("")
  }

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
  }

}