package com.github.k1rakishou.chan.ui.captcha.chan4

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4CaptchaSettings
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.suspendConvertIntoJsonObjectWithAdapter
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.prefs.JsonSetting
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import javax.inject.Inject

class Chan4CaptchaLayoutViewModel : BaseViewModel() {

  @Inject
  lateinit var proxiedOkHttpClient: RealProxiedOkHttpClient
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var moshi: Moshi
  @Inject
  lateinit var themeEngine: ThemeEngine

  private var activeJob: Job? = null
  lateinit var chan4CaptchaSettings: JsonSetting<Chan4CaptchaSettings>

  private val _showCaptchaHelpFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  val showCaptchaHelpFlow: SharedFlow<Unit>
    get() = _showCaptchaHelpFlow.asSharedFlow()

  var captchaInfoToShow = mutableStateOf<AsyncData<CaptchaInfo>>(AsyncData.NotInitialized)
  var currentInputValue = mutableStateOf<String>("")
  var onlyShowBackgroundImage = mutableStateOf(false)

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    chan4CaptchaSettings = siteManager.bySiteDescriptor(Chan4.SITE_DESCRIPTOR)!!
      .getSettingBySettingId(SiteSetting.SiteSettingId.Chan4CaptchaSettings)!!

    val settings = chan4CaptchaSettings.get()
    onlyShowBackgroundImage.value = settings.onlyShowBackgroundImage

    if (!settings.captchaHelpShown) {
      _showCaptchaHelpFlow.tryEmit(Unit)
      chan4CaptchaSettings.set(settings.copy(captchaHelpShown = true))
    }
  }

  fun toggleOnlyShowBackgroundImage() {
    onlyShowBackgroundImage.value = onlyShowBackgroundImage.value.not()

    val updatedSettings = chan4CaptchaSettings.get()
      .copy(onlyShowBackgroundImage = onlyShowBackgroundImage.value)
    chan4CaptchaSettings.set(updatedSettings)
  }

  fun toggleContrastBackground() {
    val settings = chan4CaptchaSettings.get()
    val updatedSettings = settings
      .copy(sliderCaptchaUseContrastBackground = settings.sliderCaptchaUseContrastBackground.not())

    chan4CaptchaSettings.set(updatedSettings)
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
          val error = result.error
          if (error is CaptchaRateLimitError) {
            captchaInfoToShow.value = AsyncData.Error(error)
            delay(error.cooldownMs)

            requestCaptcha(chanDescriptor)
            return@launch
          }

          AsyncData.Error(error)
        }
        is ModularResult.Value -> {
          AsyncData.Data(result.value)
        }
      }
    }
  }

  private suspend fun requestCaptchaInternal(chanDescriptor: ChanDescriptor): CaptchaInfo {
    val boardCode = chanDescriptor.boardDescriptor().boardCode
    val urlRaw = formatCaptchaUrl(chanDescriptor, boardCode)

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
      val cooldownMs = captchaInfoRaw.cooldown?.times(1000L)
        ?: DEFAULT_COOLDOWN_MS

      Logger.d(TAG, "requestCaptchaInternal($chanDescriptor) rate limited! cooldownMs=$cooldownMs")
      throw CaptchaRateLimitError(cooldownMs)
    }

    if (captchaInfoRaw.isNoopChallenge()) {
      Logger.d(TAG, "requestCaptchaInternal($chanDescriptor) NOOP challenge detected")

      return CaptchaInfo(
        bgBitmapPainter = null,
        imgBitmapPainter = null,
        challenge = NOOP_CHALLENGE,
        startedAt = System.currentTimeMillis(),
        ttlSeconds = captchaInfoRaw.ttlSeconds(),
        bgInitialOffset = 0f
      )
    }
    
    val bgBitmapPainter = captchaInfoRaw.bg?.let { bgBase64Img ->
      val bgByteArray = Base64.decode(bgBase64Img, Base64.DEFAULT)
      val bitmap = BitmapFactory.decodeByteArray(bgByteArray, 0, bgByteArray.size)

      val bgImageBitmap = if (chan4CaptchaSettings.get().sliderCaptchaUseContrastBackground) {
        replaceColor(bitmap, 0xFFEEEEEEL.toInt(), themeEngine.chanTheme.accentColor).asImageBitmap()
      } else {
        bitmap.asImageBitmap()
      }

      return@let BitmapPainter(bgImageBitmap)
    }

    val imgBitmapPainter = captchaInfoRaw.img?.let { imgBase64Img ->
      val bgByteArray = Base64.decode(imgBase64Img, Base64.DEFAULT)
      val imgImageBitmap = BitmapFactory.decodeByteArray(bgByteArray, 0, bgByteArray.size).asImageBitmap()

      return@let BitmapPainter(imgImageBitmap)
    }

    val bgInitialOffset = if (captchaInfoRaw.bgWidth != null && captchaInfoRaw.imgWidth != null) {
      if (captchaInfoRaw.bgWidth > captchaInfoRaw.imgWidth) {
        captchaInfoRaw.bgWidth - captchaInfoRaw.imgWidth
      } else {
        captchaInfoRaw.imgWidth - captchaInfoRaw.bgWidth
      }
    } else {
      0
    }

    return CaptchaInfo(
      bgBitmapPainter = bgBitmapPainter,
      imgBitmapPainter = imgBitmapPainter!!,
      challenge = captchaInfoRaw.challenge!!,
      startedAt = System.currentTimeMillis(),
      ttlSeconds = captchaInfoRaw.ttl!!,
      bgInitialOffset = bgInitialOffset.toFloat()
    )
  }

  private fun formatCaptchaUrl(chanDescriptor: ChanDescriptor, boardCode: String): String {
    val chanBoard = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())

    val host = if (chanBoard == null || chanBoard.workSafe) {
      "4channel"
    } else {
      "4chan"
    }

    return when (chanDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> {
        "https://sys.$host.org/captcha?board=${boardCode}"
      }
      is ChanDescriptor.ThreadDescriptor -> {
        "https://sys.$host.org/captcha?board=${boardCode}&thread_id=${chanDescriptor.threadNo}"
      }
    }
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
    @Json(name = "cd")
    val cooldown: Int?,
    
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
  ) {
    fun ttlSeconds(): Int {
      return ttl ?: 120
    }

    fun isNoopChallenge(): Boolean {
      return challenge?.equals(NOOP_CHALLENGE, ignoreCase = true) == true
    }
  }

  class CaptchaInfo(
    val bgBitmapPainter: BitmapPainter?,
    val imgBitmapPainter: BitmapPainter?,
    val challenge: String,
    val startedAt: Long,
    val ttlSeconds: Int,
    val bgInitialOffset: Float
  ) {
    fun needSlider(): Boolean = bgBitmapPainter != null

    fun ttlMillis(): Long {
      val ttlMillis = ttlSeconds * 1000L

      return ttlMillis - (System.currentTimeMillis() - startedAt)
    }

    fun isNoopChallenge(): Boolean {
      return challenge.equals(NOOP_CHALLENGE, ignoreCase = true)
    }

  }

  class CaptchaRateLimitError(val cooldownMs: Long) :
    Exception("4chan captcha rate-limit detected! Captcha will be reloaded automatically in ${cooldownMs / 1000L}s")

  companion object {
    private const val TAG = "Chan4CaptchaLayoutViewModel"
    private const val ERROR_MSG = "You have to wait a while before doing this again"
    private const val DEFAULT_COOLDOWN_MS = 5000L
    const val NOOP_CHALLENGE = "noop"
  }

}