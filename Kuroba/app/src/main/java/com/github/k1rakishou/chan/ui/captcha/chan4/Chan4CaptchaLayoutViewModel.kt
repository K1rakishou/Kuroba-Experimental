package com.github.k1rakishou.chan.ui.captcha.chan4

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.suspendConvertIntoJsonObjectWithAdapter
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request
import javax.inject.Inject

class Chan4CaptchaLayoutViewModel : BaseViewModel() {

  @Inject
  lateinit var proxiedOkHttpClient: RealProxiedOkHttpClient
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var moshi: Moshi

  private var activeJob: Job? = null
  var captchaInfoToShow = mutableStateOf<AsyncData<CaptchaInfo>>(AsyncData.NotInitialized)
  var currentInputValue = mutableStateOf<String>("")

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {

  }

  fun cleanup() {
    captchaInfoToShow.value = AsyncData.NotInitialized
    currentInputValue.value = ""

    activeJob?.cancel()
    activeJob = null
  }

  fun requestCaptcha(chanDescriptor: ChanDescriptor) {
    activeJob?.cancel()
    activeJob = null
    currentInputValue.value = ""

    activeJob = mainScope.launch(Dispatchers.Default) {
      captchaInfoToShow.value = AsyncData.Loading

      val result = ModularResult.Try { requestCaptchaInternal(chanDescriptor) }

      captchaInfoToShow.value = when (result) {
        is ModularResult.Error -> {
          AsyncData.Error(result.error)
        }
        is ModularResult.Value -> {
          val captchaInfo = result.value
          if (captchaInfo == null) {
            captchaInfoToShow.value = AsyncData.Error(CaptchaRateLimitError())
            delay(DELAY_TIME_MS)

            requestCaptcha(chanDescriptor)
            return@launch
          }

          AsyncData.Data(captchaInfo)
        }
      }
    }
  }

  private suspend fun requestCaptchaInternal(chanDescriptor: ChanDescriptor): CaptchaInfo? {
    val boardCode = chanDescriptor.boardDescriptor().boardCode

    val urlRaw = when (chanDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> {
        "https://sys.4channel.org/captcha?board=${boardCode}"
      }
      is ChanDescriptor.ThreadDescriptor -> {
        "https://sys.4channel.org/captcha?board=${boardCode}&thread_id=${chanDescriptor.threadNo}"
      }
    }

    Logger.d(TAG, "requestCaptchaInternal($chanDescriptor) requesting $urlRaw")

    val requestBuilder = Request.Builder()
      .url(urlRaw)
      .get()

    siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())?.let { chan4 ->
      chan4.requestModifier().modifyCaptchaGetRequest(chan4, requestBuilder)
    }

    val request = requestBuilder.build()
    val captchaInfoRawAdapter = moshi.adapter(CaptchaInfoRaw::class.java)

    val captchaInfoRaw = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
      request = request,
      adapter = captchaInfoRawAdapter
    ).unwrap()

    if (captchaInfoRaw.error?.contains(ERROR_MSG, ignoreCase = true) == true) {
      Logger.d(TAG, "requestCaptchaInternal($chanDescriptor) rate limited!")
      return null
    }
    
    val bgBitmapPainter = captchaInfoRaw.bg?.let { bgBase64Img ->
      val bgByteArray = Base64.decode(bgBase64Img, Base64.DEFAULT)
      val bitmap = BitmapFactory.decodeByteArray(bgByteArray, 0, bgByteArray.size)
      val bgImageBitmap = replaceColor(bitmap, 0xFFEEEEEEL.toInt(), Color.MAGENTA).asImageBitmap()

      return@let BitmapPainter(bgImageBitmap)
    }

    val imgBitmapPainter = captchaInfoRaw.img?.let { imgBase64Img ->
      val bgByteArray = Base64.decode(imgBase64Img, Base64.DEFAULT)
      val imgImageBitmap = BitmapFactory.decodeByteArray(bgByteArray, 0, bgByteArray.size).asImageBitmap()

      return@let BitmapPainter(imgImageBitmap)
    }

    return CaptchaInfo(
      bgBitmapPainter = bgBitmapPainter,
      imgBitmapPainter = imgBitmapPainter!!,
      challenge = captchaInfoRaw.challenge!!,
      startedAt = System.currentTimeMillis(),
      ttlSeconds = captchaInfoRaw.ttl!!
    )
  }

  private fun replaceColor(src: Bitmap, fromColor: Int, targetColor: Int): Bitmap {
    val width = src.width
    val height = src.height
    val pixels = IntArray(width * height)
    src.getPixels(pixels, 0, width, 0, 0, width, height)

    for (x in pixels.indices) {
      pixels[x] = if (pixels[x] == fromColor) {
        targetColor
      } else {
        pixels[x]
      }
    }

    val result = Bitmap.createBitmap(width, height, src.config)
    result.setPixels(pixels, 0, width, 0, 0, width, height)
    return result
  }

  @JsonClass(generateAdapter = true)
  data class CaptchaInfoRaw(
    @Json(name = "error")
    val error: String?,
    
    // For Slider captcha
    @Json(name = "bg")
    val bg: String?,
    @Json(name = "bg_width")
    val bgWidth: Int?,

    @Json(name = "cd_until")
    val cooldownUntil: Long?,
    @Json(name = "challenge")
    val challenge: String?,
    @Json(name = "img")
    val img: String?,
    @Json(name = "img_width")
    val imgWidth: Int?,
    @Json(name = "img_height")
    val imgHeight: Int?,
    @Json(name = "valid_until")
    val validUntil: Long?,
    @Json(name = "ttl")
    val ttl: Int?
  )

  class CaptchaInfo(
    val bgBitmapPainter: BitmapPainter?,
    val imgBitmapPainter: BitmapPainter,
    val challenge: String,
    val startedAt: Long,
    val ttlSeconds: Int
  ) {
    fun needSlider(): Boolean = bgBitmapPainter != null

    fun ttlMillis(): Long {
      val ttlMillis = ttlSeconds * 1000L

      return ttlMillis - (System.currentTimeMillis() - startedAt)
    }
  }

  class CaptchaRateLimitError : Exception("Captcha rate-limit detected! Please wait a couple of seconds and then try again.")

  companion object {
    private const val TAG = "Chan4CaptchaLayoutViewModel"
    private const val ERROR_MSG = "You have to wait a while before doing this again"
    private const val DELAY_TIME_MS = 3000L
  }

}