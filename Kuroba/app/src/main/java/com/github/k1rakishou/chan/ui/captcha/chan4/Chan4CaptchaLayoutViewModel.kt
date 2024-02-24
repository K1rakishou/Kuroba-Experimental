package com.github.k1rakishou.chan.ui.captcha.chan4

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Base64
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.withTranslation
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.CaptchaImageCache
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4CaptchaSettings
import com.github.k1rakishou.chan.core.usecase.LoadChan4CaptchaUseCase
import com.github.k1rakishou.chan.core.usecase.RefreshChan4CaptchaTicketUseCase
import com.github.k1rakishou.chan.features.posting.CaptchaDonation
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.prefs.GsonJsonSetting
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
  @Inject
  lateinit var captchaImageCache: CaptchaImageCache
  @Inject
  lateinit var captchaDonation: CaptchaDonation
  @Inject
  lateinit var loadChan4CaptchaUseCase: LoadChan4CaptchaUseCase
  @Inject
  lateinit var refreshChan4CaptchaTicketUseCase: RefreshChan4CaptchaTicketUseCase

  private var activeJob: Job? = null
  private var captchaTtlUpdateJob: Job? = null

  val chan4CaptchaSettingsJson by lazy {
    siteManager.bySiteDescriptor(Chan4.SITE_DESCRIPTOR)!!
      .getSettingBySettingId<GsonJsonSetting<Chan4CaptchaSettings>>(SiteSetting.SiteSettingId.Chan4CaptchaSettings)!!
  }

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

  private val _captchaSuggestions = mutableListOf<String>()

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
  }

  fun cleanup() {
    activeJob?.cancel()
    activeJob = null

    captchaTtlUpdateJob?.cancel()
    captchaTtlUpdateJob = null

    _captchaTtlMillisFlow.value = -1L
  }

  fun cacheCaptcha(uuid: String?, chanDescriptor: ChanDescriptor) {
    if (uuid == null) {
      return
    }

    if (ChanSettings.donateSolvedCaptchaForGreaterGood.get() != ChanSettings.NullableBoolean.True) {
      return
    }

    val captchaInfo = (_captchaInfoToShow.value as? AsyncData.Data)?.data
      ?: return

    val imgBitmap = captchaInfo.imgBitmap ?: return

    val width = imgBitmap.width
    val height = imgBitmap.height
    val scrollValue = captchaInfo.sliderValue.value

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFFEEEEEE.toInt()
      style = Paint.Style.FILL
    }

    with(canvas) {
      drawRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)

      if (captchaInfo.bgBitmapOriginal != null) {
        canvas.withTranslation(x = (scrollValue * captchaInfo.widthDiff() * -1)) {
          canvas.drawBitmap(captchaInfo.bgBitmapOriginal, 0f, 0f, null)
        }
      }

      canvas.drawBitmap(captchaInfo.imgBitmap, 0f, 0f, null)
    }

    captchaImageCache.put(uuid, chanDescriptor, bitmap)
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
    _captchaSuggestions.clear()
    getCachedCaptchaInfoOrNull(chanDescriptor)?.reset()

    captchaInfoCache.remove(chanDescriptor)

    activeJob = viewModelScope.launch(Dispatchers.Default) {
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

      val result = ModularResult.Try {
        requestCaptchaInternal(
          appContext = appContext,
          chanDescriptor = chanDescriptor,
          ticket = chan4CaptchaSettingsJson.get().captchaTicket
        )
      }
      when (result) {
        is ModularResult.Error -> {
          val error = result.error

          Logger.e(TAG, "requestCaptcha()", error)
          _captchaInfoToShow.value = AsyncData.Error(error)

          if (error is CaptchaCooldownError) {
            waitUntilCaptchaRateLimitPassed(error.cooldownMs)

            withContext(Dispatchers.Main) { requestCaptcha(appContext, chanDescriptor, forced = true) }
            return@launch
          }
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
    var remainingCooldownMs = initialCooldownMs

    while (isActive) {
      if (remainingCooldownMs <= 0) {
        break
      }

      delay(1000L)

      val previousError = (_captchaInfoToShow.value as? AsyncData.Error)?.throwable
        ?: break

      when (previousError) {
        is CaptchaGenericRateLimitError -> {
          _captchaInfoToShow.value = AsyncData.Error(CaptchaGenericRateLimitError(remainingCooldownMs))
        }
        is CaptchaThreadRateLimitError -> {
          _captchaInfoToShow.value = AsyncData.Error(CaptchaThreadRateLimitError(remainingCooldownMs))
        }
        is CaptchaPostRateLimitError -> {
          _captchaInfoToShow.value = AsyncData.Error(CaptchaPostRateLimitError(remainingCooldownMs))
        }
        else -> {
          break
        }
      }

      remainingCooldownMs -= 1000L
    }
  }

  private fun startOrRestartCaptchaTtlUpdateTask(chanDescriptor: ChanDescriptor) {
    captchaTtlUpdateJob?.cancel()
    captchaTtlUpdateJob = null

    captchaTtlUpdateJob = viewModelScope.launch(Dispatchers.Main) {
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

  private suspend fun requestCaptchaInternal(
    appContext: Context,
    chanDescriptor: ChanDescriptor,
    ticket: String?
  ): CaptchaInfo {
    val (captchaInfoRaw, captchaInfoRawString) = getCachedCaptchaOrLoadFresh(
      chanDescriptor = chanDescriptor,
      ticket = ticket
    )

    if (captchaInfoRaw.err?.contains(ERROR_MSG, ignoreCase = true) == true) {
      val cooldownMs = captchaInfoRaw.cooldown?.times(1000L)
        ?: DEFAULT_COOLDOWN_MS

      Logger.d(TAG, "requestCaptchaInternal($chanDescriptor) rate limited! cooldownMs=$cooldownMs")
      throw CaptchaGenericRateLimitError(cooldownMs)
    }

    if (captchaInfoRaw.pcdMsg != null) {
      if (captchaInfoRaw.pcd == null) {
        throw UnknownCaptchaError(captchaInfoRaw.pcdMsg)
      }

      val cooldownMs = captchaInfoRaw.pcd.times(1000L)

      when (chanDescriptor) {
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          error("Cannot use CompositeCatalogDescriptor here")
        }
        is ChanDescriptor.CatalogDescriptor -> {
          Logger.d(TAG, "requestCaptchaInternal($chanDescriptor) new thread creation rate limited! cooldownMs=$cooldownMs")
          throw CaptchaThreadRateLimitError(cooldownMs)
        }
        is ChanDescriptor.ThreadDescriptor -> {
          Logger.d(TAG, "requestCaptchaInternal($chanDescriptor) new post creation rate limited! cooldownMs=$cooldownMs")
          throw CaptchaPostRateLimitError(cooldownMs)
        }
      }
    }

    if (captchaInfoRaw.isNoopChallenge()) {
      Logger.d(TAG, "requestCaptchaInternal($chanDescriptor) NOOP challenge detected")

      return CaptchaInfo(
        chanDescriptor = chanDescriptor,
        bgBitmap = null,
        bgBitmapOriginal = null,
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
    
    val (bgBitmap, bgBitmapOriginal) = captchaInfoRaw.bg.let { bgBase64Img ->
      if (bgBase64Img == null) {
        return@let null to null
      }

      val bgByteArray = Base64.decode(bgBase64Img, Base64.DEFAULT)
      val bitmap = BitmapFactory.decodeByteArray(bgByteArray, 0, bgByteArray.size)
      val bitmapOriginal = BitmapFactory.decodeByteArray(bgByteArray, 0, bgByteArray.size)

      val bgImageBitmap = if (chan4CaptchaSettingsJson.get().sliderCaptchaUseContrastBackground) {
        replaceColor(
          src = bitmap,
          fromColor = CAPTCHA_DEFAULT_BG_COLOR.toArgb(),
          targetColor = CAPTCHA_CONTRAST_BG_COLOR.toArgb()
        )
      } else {
        bitmap
      }

      return@let bgImageBitmap to bitmapOriginal
    }

    val imgBitmap = captchaInfoRaw.img?.let { imgBase64Img ->
      val bgByteArray = Base64.decode(imgBase64Img, Base64.DEFAULT)
      return@let BitmapFactory.decodeByteArray(bgByteArray, 0, bgByteArray.size)
    }

    if (captchaInfoRaw.challenge == null) {
      Logger.error(TAG) { "captchaInfoRawString: '${captchaInfoRawString}'" }
      throw UnknownCaptchaError("Captcha 'challenge' json field does not exist in the server response!")
    }

    if (captchaInfoRaw.ttl == null) {
      Logger.error(TAG) { "captchaInfoRawString: '${captchaInfoRawString}'" }
      throw UnknownCaptchaError("Captcha 'ttl' json field does not exist in the server response!")
    }

    return CaptchaInfo(
      chanDescriptor = chanDescriptor,
      bgBitmap = bgBitmap,
      bgBitmapOriginal = bgBitmapOriginal,
      imgBitmap = imgBitmap,
      challenge = captchaInfoRaw.challenge,
      startedAt = System.currentTimeMillis(),
      ttlSeconds = captchaInfoRaw.ttl,
      imgWidth = captchaInfoRaw.imgWidth,
      bgWidth = captchaInfoRaw.bgWidth,
      captchaInfoRawString = captchaInfoRawString,
      captchaSolution = captchaSolution
    )
  }

  private suspend fun getCachedCaptchaOrLoadFresh(
    chanDescriptor: ChanDescriptor,
    ticket: String?
  ): Pair<LoadChan4CaptchaUseCase.CaptchaInfoRaw, String> {
    val lastRefreshedCaptcha = refreshChan4CaptchaTicketUseCase.lastRefreshedCaptcha

    if (lastRefreshedCaptcha != null && lastRefreshedCaptcha.chanDescriptor == chanDescriptor) {
      Logger.debug(TAG) { "getCachedCaptchaOrLoadFresh(${chanDescriptor}) using cached captcha" }

      val captchaInfoRaw = lastRefreshedCaptcha.captchaResult.captchaInfoRaw
      val captchaInfoRawString = lastRefreshedCaptcha.captchaResult.captchaInfoRawString

      return captchaInfoRaw to captchaInfoRawString
    }

    Logger.debug(TAG) { "getCachedCaptchaOrLoadFresh(${chanDescriptor}) requesting fresh captcha" }

    val captchaResult = loadChan4CaptchaUseCase.await(
      chanDescriptor = chanDescriptor,
      ticket = ticket,
      isRefreshing = false
    ).unwrap()

    val captchaInfoRaw = captchaResult.captchaInfoRaw
    val captchaInfoRawString = captchaResult.captchaInfoRawString

    return captchaInfoRaw to captchaInfoRawString
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

  fun onGotAutoSolverSuggestions(captchaSuggestions: List<String>) {
    _captchaSuggestions.clear()
    _captchaSuggestions.addAll(captchaSuggestions)
  }

  fun resetAutoSolverSuggestions() {
    _captchaSuggestions.clear()
  }

  fun onVerificationCompleted(solution: String) {
    if (_captchaSuggestions.isEmpty()) {
      return
    }

    if (_captchaSuggestions.contains(solution)) {
      captchaDonation.addAutoSolvedCaptcha(solution)
    }
  }

  class CaptchaInfo(
    val chanDescriptor: ChanDescriptor,
    val bgBitmap: Bitmap?,
    val bgBitmapOriginal: Bitmap?,
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

  interface CaptchaCooldownError {
    val cooldownMs: Long
  }

  class CaptchaGenericRateLimitError(override val cooldownMs: Long) :
    Exception("4chan captcha rate-limit detected!\nCaptcha will be reloaded automatically in ${cooldownMs / 1000L}s"),
    CaptchaCooldownError

  class CaptchaThreadRateLimitError(override val cooldownMs: Long) :
    Exception("4chan captcha rate-limit detected!\nPlease wait ${cooldownMs / 1000L} seconds before making a thread."),
    CaptchaCooldownError

  class CaptchaPostRateLimitError(override val cooldownMs: Long) :
    Exception("4chan captcha rate-limit detected!\nPlease wait ${cooldownMs / 1000L} seconds before making a post."),
    CaptchaCooldownError

  class UnknownCaptchaError(message: String) : java.lang.Exception(message)

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