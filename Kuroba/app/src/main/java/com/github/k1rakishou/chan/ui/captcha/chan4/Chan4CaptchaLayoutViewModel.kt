package com.github.k1rakishou.chan.ui.captcha.chan4

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4CaptchaSettings
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.prefs.GsonJsonSetting
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import kotlin.math.abs

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
  @Inject
  lateinit var chan4CaptchaSolverHelper: Chan4CaptchaSolverHelper

  private var activeJob: Job? = null
  private var captchaTtlUpdateJob: Job? = null

  val chan4CaptchaSettingsJson by lazy {
    siteManager.bySiteDescriptor(Chan4.SITE_DESCRIPTOR)!!
      .getSettingBySettingId<GsonJsonSetting<Chan4CaptchaSettings>>(SiteSetting.SiteSettingId.Chan4CaptchaSettings)!!
  }

  private val _showCaptchaHelpFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  val showCaptchaHelpFlow: SharedFlow<Unit>
    get() = _showCaptchaHelpFlow.asSharedFlow()

  private val _captchaTtlMillisFlow = MutableStateFlow(-1L)
  val captchaTtlMillisFlow: StateFlow<Long>
    get() = _captchaTtlMillisFlow.asStateFlow()

  private val captchaInfoCache = mutableMapOf<ChanDescriptor, CaptchaInfo>()

  private var _captchaInfoToShow = mutableStateOf<AsyncData<CaptchaInfo>>(AsyncData.NotInitialized)
  val captchaInfoToShow: State<AsyncData<CaptchaInfo>>
    get() = _captchaInfoToShow

  @Volatile private var notifiedUserAboutCaptchaSolver = false

  private var _captchaSolverInstalled = mutableStateOf<Boolean>(false)
  val captchaSolverInstalled: State<Boolean>
    get() = _captchaSolverInstalled

  private var _solvingInProgress = mutableStateOf<Boolean>(false)
  val solvingInProgress: State<Boolean>
    get() = _solvingInProgress

  private val _notifyUserAboutCaptchaSolverErrorFlow = MutableSharedFlow<CaptchaSolverInfo>(extraBufferCapacity = 1)
  val notifyUserAboutCaptchaSolverErrorFlow: SharedFlow<CaptchaSolverInfo>
    get() = _notifyUserAboutCaptchaSolverErrorFlow.asSharedFlow()

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    val captchaSettings = chan4CaptchaSettingsJson.get()

    if (!captchaSettings.captchaHelpShown) {
      _showCaptchaHelpFlow.tryEmit(Unit)
      chan4CaptchaSettingsJson.set(captchaSettings.copy(captchaHelpShown = true))
    }
  }

  fun cleanup() {
    activeJob?.cancel()
    activeJob = null

    captchaTtlUpdateJob?.cancel()
    captchaTtlUpdateJob = null

    _captchaTtlMillisFlow.value = -1L
  }

  fun resetCaptchaForced(chanDescriptor: ChanDescriptor) {
    _captchaInfoToShow.value = AsyncData.NotInitialized
    getCachedCaptchaInfoOrNull(chanDescriptor)?.reset()

    captchaInfoCache.remove(chanDescriptor)
  }

  fun resetCaptchaIfCaptchaIsAlmostDead(chanDescriptor: ChanDescriptor) {
    val captchaTtlMillis = getCachedCaptchaInfoOrNull(chanDescriptor)?.ttlMillis() ?: 0L
    if (captchaTtlMillis <= MIN_TTL_TO_RESET_CAPTCHA) {
      resetCaptchaForced(chanDescriptor)
    }
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  fun requestCaptcha(context: Context, chanDescriptor: ChanDescriptor, forced: Boolean) {
    activeJob?.cancel()
    activeJob = null

    captchaTtlUpdateJob?.cancel()
    captchaTtlUpdateJob = null

    val prevCaptchaInfo = getCachedCaptchaInfoOrNull(chanDescriptor)
    val appContext = context

    if (!forced
      && prevCaptchaInfo != null
      && prevCaptchaInfo.ttlMillis() > MIN_TTL_TO_NOT_REQUEST_NEW_CAPTCHA
    ) {
      Logger.d(TAG, "requestCaptcha() old captcha is still fine, " +
        "ttl: ${prevCaptchaInfo.ttlMillis()}, chanDescriptor=$chanDescriptor")

      _captchaInfoToShow.value = AsyncData.Data(prevCaptchaInfo)
      startOrRestartCaptchaTtlUpdateTask(chanDescriptor)

      return
    }

    Logger.d(TAG, "requestCaptcha() requesting new captcha " +
      "(forced: $forced, ttl: ${prevCaptchaInfo?.ttlMillis()}, chanDescriptor=$chanDescriptor)")

    _captchaTtlMillisFlow.value = -1L
    getCachedCaptchaInfoOrNull(chanDescriptor)?.reset()

    captchaInfoCache.remove(chanDescriptor)

    activeJob = mainScope.launch(Dispatchers.Default) {
      _captchaInfoToShow.value = AsyncData.Loading
      viewModelInitialized.awaitUntilInitialized()

      if (chan4CaptchaSettingsJson.get().useCaptchaSolver) {
        val chan4CaptchaSolverInfo = chan4CaptchaSolverHelper.checkCaptchaSolverInstalled(appContext)
        if (chan4CaptchaSolverInfo !is CaptchaSolverInfo.Installed && !notifiedUserAboutCaptchaSolver) {
          notifiedUserAboutCaptchaSolver = true
          _notifyUserAboutCaptchaSolverErrorFlow.emit(chan4CaptchaSolverInfo)
        }

        _captchaSolverInstalled.value = chan4CaptchaSolverInfo == CaptchaSolverInfo.Installed
      }

      val result = ModularResult.Try { requestCaptchaInternal(appContext, chanDescriptor) }
      when (result) {
        is ModularResult.Error -> {
          Logger.e(TAG, "requestCaptcha() error=${result.error.errorMessageOrClassName()}")

          val error = result.error
          if (error is CaptchaRateLimitError) {
            waitUntilCaptchaRateLimitPassed(error.cooldownMs)

            withContext(Dispatchers.Main) { requestCaptcha(appContext, chanDescriptor, forced = true) }
            return@launch
          }

          _captchaInfoToShow.value = AsyncData.Error(error)
        }
        is ModularResult.Value -> {
          Logger.d(TAG, "requestCaptcha() success")

          captchaInfoCache[chanDescriptor] = result.value
          _captchaInfoToShow.value = AsyncData.Data(result.value)

          startOrRestartCaptchaTtlUpdateTask(chanDescriptor)
        }
      }

      activeJob = null
    }
  }

  private suspend fun CoroutineScope.waitUntilCaptchaRateLimitPassed(initialCooldownMs: Long) {
    var remainingCooldownMs = initialCooldownMs + 1000L

    while (isActive) {
      if (remainingCooldownMs <= 0) {
        break
      }

      delay(1000L)

      _captchaInfoToShow.value = AsyncData.Error(CaptchaRateLimitError(remainingCooldownMs))
      remainingCooldownMs -= 1000L
    }
  }

  private fun startOrRestartCaptchaTtlUpdateTask(chanDescriptor: ChanDescriptor) {
    captchaTtlUpdateJob?.cancel()
    captchaTtlUpdateJob = null

    captchaTtlUpdateJob = mainScope.launch(Dispatchers.Main) {
      while (isActive) {
        val captchaInfoAsyncData = _captchaInfoToShow.value

        val captchaInfo = if (captchaInfoAsyncData !is AsyncData.Data) {
          resetCaptchaForced(chanDescriptor)
          break
        } else {
          captchaInfoAsyncData.data
        }

        val captchaTtlMillis = captchaInfo.ttlMillis().coerceAtLeast(0L)
        _captchaTtlMillisFlow.value = captchaTtlMillis

        if (captchaTtlMillis <= 0) {
          break
        }

        delay(1000L)
      }

      captchaTtlUpdateJob = null
    }
  }

  private suspend fun requestCaptchaInternal(appContext: Context, chanDescriptor: ChanDescriptor): CaptchaInfo {
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

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
    if (!response.isSuccessful) {
      throw BadStatusResponseException(response.code)
    }

    val captchaInfoRawString = response.body?.string()
    if (captchaInfoRawString == null) {
      throw EmptyBodyResponseException()
    }

    val captchaInfoRaw = captchaInfoRawAdapter.fromJson(captchaInfoRawString)
    if (captchaInfoRaw == null) {
      throw IOException("Failed to convert json to CaptchaInfoRaw")
    }

    if (captchaInfoRaw.error?.contains(ERROR_MSG, ignoreCase = true) == true) {
      val cooldownMs = captchaInfoRaw.cooldown?.times(1000L)
        ?: DEFAULT_COOLDOWN_MS

      Logger.d(TAG, "requestCaptchaInternal($chanDescriptor) rate limited! cooldownMs=$cooldownMs")
      throw CaptchaRateLimitError(cooldownMs)
    }

    if (captchaInfoRaw.isNoopChallenge()) {
      Logger.d(TAG, "requestCaptchaInternal($chanDescriptor) NOOP challenge detected")

      return CaptchaInfo(
        chanDescriptor = chanDescriptor,
        bgBitmap = null,
        imgBitmap = null,
        challenge = NOOP_CHALLENGE,
        startedAt = System.currentTimeMillis(),
        ttlSeconds = captchaInfoRaw.ttlSeconds(),
        imgWidth = null,
        bgWidth = null,
        captchaInfoRawString = null,
        captchaSolution = null
      )
    }

    val captchaSolution = if (_captchaSolverInstalled.value) {
      chan4CaptchaSolverHelper.autoSolveCaptcha(appContext, captchaInfoRawString, null)
    } else {
      null
    }
    
    val bgBitmap = captchaInfoRaw.bg?.let { bgBase64Img ->
      val bgByteArray = Base64.decode(bgBase64Img, Base64.DEFAULT)
      val bitmap = BitmapFactory.decodeByteArray(bgByteArray, 0, bgByteArray.size)

      val bgImageBitmap = if (chan4CaptchaSettingsJson.get().sliderCaptchaUseContrastBackground) {
        replaceColor(
          src = bitmap,
          fromColor = CAPTCHA_DEFAULT_BG_COLOR.toArgb(),
          targetColor = CAPTCHA_CONTRAST_BG_COLOR.toArgb()
        )
      } else {
        bitmap
      }

      return@let bgImageBitmap
    }

    val imgBitmap = captchaInfoRaw.img?.let { imgBase64Img ->
      val bgByteArray = Base64.decode(imgBase64Img, Base64.DEFAULT)
      return@let BitmapFactory.decodeByteArray(bgByteArray, 0, bgByteArray.size)
    }

    return CaptchaInfo(
      chanDescriptor = chanDescriptor,
      bgBitmap = bgBitmap,
      imgBitmap = imgBitmap,
      challenge = captchaInfoRaw.challenge!!,
      startedAt = System.currentTimeMillis(),
      ttlSeconds = captchaInfoRaw.ttl!!,
      imgWidth = captchaInfoRaw.imgWidth,
      bgWidth = captchaInfoRaw.bgWidth,
      captchaInfoRawString = captchaInfoRawString,
      captchaSolution = captchaSolution
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
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        error("Cannot use CompositeCatalogDescriptor here")
      }
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

  private fun getCachedCaptchaInfoOrNull(chanDescriptor: ChanDescriptor): CaptchaInfo? {
    val captchaInfo = captchaInfoCache[chanDescriptor]
    if (captchaInfo == null) {
      return null
    }

    if (captchaInfo.ttlMillis() < 0L) {
      captchaInfoCache.remove(chanDescriptor)
      return null
    }

    return captchaInfo
  }

  fun solveCaptcha(context: Context, captchaInfoRawString: String, sliderOffset: Float) {
    viewModelScope.launch {
      val appContext = context.applicationContext

      if (!_captchaSolverInstalled.value) {
        return@launch
      }

      val currentCaptchaInfo = (_captchaInfoToShow.value as? AsyncData.Data)?.data
        ?: return@launch

      _solvingInProgress.value = true

      val captchaSolution = try {
        chan4CaptchaSolverHelper.autoSolveCaptcha(
          context = appContext,
          captchaInfoRawString = captchaInfoRawString,
          sliderOffset = sliderOffset
        )
      } finally {
        _solvingInProgress.value = false
      }

      if (captchaSolution == null) {
        return@launch
      }

      currentCaptchaInfo.captchaSolution.value = captchaSolution
    }
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
    val chanDescriptor: ChanDescriptor,
    val bgBitmap: Bitmap?,
    val imgBitmap: Bitmap?,
    val challenge: String,
    val startedAt: Long,
    val ttlSeconds: Int,
    val bgWidth: Int?,
    val imgWidth: Int?,
    val captchaInfoRawString: String?,
    captchaSolution: Chan4CaptchaSolverHelper.CaptchaSolution?
  ) {
    val currentInputValue = mutableStateOf<String>("")
    val sliderValue = mutableStateOf(0f)
    val captchaSolution = mutableStateOf<Chan4CaptchaSolverHelper.CaptchaSolution?>(captchaSolution)

    fun widthDiff(): Int {
      if (imgWidth == null || bgWidth == null) {
        return 0
      }

      return abs(imgWidth - bgWidth)
    }

    fun reset() {
      currentInputValue.value = ""
      sliderValue.value = 0f
    }

    fun needSlider(): Boolean = bgBitmap != null

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

    private const val MIN_TTL_TO_NOT_REQUEST_NEW_CAPTCHA = 25_000L // 25 seconds
    private const val MIN_TTL_TO_RESET_CAPTCHA = 5_000L // 5 seconds

    val CAPTCHA_DEFAULT_BG_COLOR = Color(0xFFEEEEEEL.toInt())
    val CAPTCHA_CONTRAST_BG_COLOR = Color(0xFFE0224E.toInt())

    const val NOOP_CHALLENGE = "noop"
  }

}