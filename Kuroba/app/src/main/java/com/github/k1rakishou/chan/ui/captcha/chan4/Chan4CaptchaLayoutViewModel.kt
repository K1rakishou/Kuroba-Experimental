package com.github.k1rakishou.chan.ui.captcha.chan4

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.viewmodel.KurobaViewModel
import com.github.k1rakishou.chan.core.compose.AsyncUiData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.Chan4CaptchaNotifierManager
import com.github.k1rakishou.chan.core.manager.FirewallBypassManager
import com.github.k1rakishou.chan.core.manager.HapticFeedbackManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.WebViewTaskManager
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4CaptchaSettings
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4SiteSettings
import com.github.k1rakishou.chan.core.usecase.LoadChan4CaptchaUseCase
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.task.AbstractWebViewTask
import com.github.k1rakishou.chan.features.webview.task.SpurUsAntibotTask
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils.asFormattedToken
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isCancellationException
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.prefs.GsonJsonSetting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import javax.inject.Inject

class Chan4CaptchaLayoutViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val siteManager: SiteManager,
  private val loadChan4CaptchaUseCase: LoadChan4CaptchaUseCase,
  private val hapticFeedbackManager: HapticFeedbackManager,
  private val firewallBypassManager: FirewallBypassManager,
  private val chan4CaptchaNotifierManager: Chan4CaptchaNotifierManager,
  private val webViewTaskManager: WebViewTaskManager,
) : KurobaViewModel(), Chan4CaptchaNotifierManager.CaptchaViewModelCallbacks {

  private var activeJob: Job? = null
  private var captchaTtlUpdateJob: Job? = null

  val chan4CaptchaSettingsJson: GsonJsonSetting<Chan4CaptchaSettings> by lazy {
    siteManager.bySiteDescriptorAndActive(Chan4.SITE_DESCRIPTOR)!!
      .requireSiteSettings(Chan4SiteSettings::class.java)
      .captchaSettings
  }

  private val captchaInfoCache = mutableMapOf<ChanDescriptor, CaptchaInfo>()

  private val _captchaTtlMillisFlow = MutableStateFlow(-1L)
  val captchaTtlMillisFlow: StateFlow<Long>
    get() = _captchaTtlMillisFlow.asStateFlow()

  private val _captchaInfoToShow = mutableStateOf<AsyncUiData<CaptchaInfo>>(AsyncUiData.NotInitialized)
  val captchaInfoToShow: State<AsyncUiData<CaptchaInfo>>
    get() = _captchaInfoToShow

  private val _captchaDataJson = mutableStateOf<String?>(null)
  val captchaDataJson: State<String?>
    get() = _captchaDataJson

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
  }

  override fun readCurrentCaptchaInfo(): AsyncUiData<CaptchaInfo> {
    return _captchaInfoToShow.value
  }

  override fun updateCurrentCaptchaInfo(captchaInfo: AsyncUiData<CaptchaInfo>) {
    _captchaInfoToShow.value = captchaInfo
  }

  fun onCaptchaViewInitialized() {
    chan4CaptchaNotifierManager.onCaptchaViewInitialized(this)
  }

  fun onCaptchaViewDestroyed() {
    activeJob?.cancel()
    activeJob = null

    captchaTtlUpdateJob?.cancel()
    captchaTtlUpdateJob = null

    _captchaTtlMillisFlow.value = -1L
    chan4CaptchaNotifierManager.onCaptchaViewDestroyed()
  }

  fun resetCaptchaForced(chanDescriptor: ChanDescriptor) {
    _captchaInfoToShow.value = AsyncUiData.NotInitialized
    getCachedCaptchaInfoOrNull(chanDescriptor)?.reset()

    captchaInfoCache.remove(chanDescriptor)
  }

  fun resetCaptchaIfCaptchaIsAlmostDead(chanDescriptor: ChanDescriptor) {
    val captchaTtlMillis = getCachedCaptchaInfoOrNull(chanDescriptor)?.ttlMillis() ?: 0L
    if (captchaTtlMillis <= MIN_TTL_TO_RESET_CAPTCHA) {
      resetCaptchaForced(chanDescriptor)
    }
  }

  fun requestCaptcha(
    chanDescriptor: ChanDescriptor,
    mcl: String,
    forced: Boolean
  ) {
    activeJob?.cancel()
    activeJob = null

    captchaTtlUpdateJob?.cancel()
    captchaTtlUpdateJob = null

    val prevCaptchaInfo = getCachedCaptchaInfoOrNull(chanDescriptor)

    if (!forced
      && prevCaptchaInfo != null
      && prevCaptchaInfo.ttlMillis() > MIN_TTL_TO_NOT_REQUEST_NEW_CAPTCHA
    ) {
      Logger.d(TAG, "requestCaptcha() old captcha is still fine, " +
        "ttl: ${prevCaptchaInfo.ttlMillis()}, chanDescriptor=$chanDescriptor")

      _captchaInfoToShow.value = AsyncUiData.UiData(prevCaptchaInfo)
      startOrRestartCaptchaTtlUpdateTask(chanDescriptor)

      return
    }

    Logger.debug(TAG) {
      "requestCaptcha() requesting new captcha (" +
        "forced: $forced, ttl: ${prevCaptchaInfo?.ttlMillis()}, " +
        "chanDescriptor: $chanDescriptor, mcl: ${mcl.asFormattedToken()})"
    }

    _captchaTtlMillisFlow.value = -1L
    getCachedCaptchaInfoOrNull(chanDescriptor)?.reset()

    captchaInfoCache.remove(chanDescriptor)

    activeJob = viewModelScope.launch(Dispatchers.Default) {
      _captchaInfoToShow.value = AsyncUiData.Loading

      val result = ModularResult.Try {
        if (forced) {
          firewallBypassManager.removeHostTimeCheckByChanDescriptor(chanDescriptor)
        }

        requestCaptchaInternal(
          chanDescriptor = chanDescriptor,
          ticket = chan4CaptchaSettingsJson.get().captchaTicket,
          mcl = mcl
        )
      }
      when (result) {
        is ModularResult.Error -> {
          try {
            handleCaptchaRequestError(
              chanDescriptor = chanDescriptor,
              error = result.error
            )
          } catch (error: Throwable) {
            Logger.d(TAG, "requestCaptcha() handleCaptchaRequestError: ${error.errorMessageOrClassName()}")

            if (!error.isCancellationException()) {
              _captchaInfoToShow.value = AsyncUiData.Error(error)
            }
          }
        }
        is ModularResult.Value -> {
          Logger.d(TAG, "requestCaptcha() success")

          captchaInfoCache[chanDescriptor] = result.value
          _captchaInfoToShow.value = AsyncUiData.UiData(result.value)

          startOrRestartCaptchaTtlUpdateTask(chanDescriptor)
        }
      }

      activeJob = null
    }
  }

  fun onCaptchaImageClicked(taskIndex: Int, imageIndex: Int) {
    val captchaInfoAsyncData = _captchaInfoToShow.value

    val captchaInfo = if (captchaInfoAsyncData !is AsyncUiData.UiData) {
      return
    } else {
      captchaInfoAsyncData.data
    }

    val task = captchaInfo.tasks.getOrNull(taskIndex) ?: return

    val updatedImages = task.images
      .mapIndexed { index, image ->
        if (index == imageIndex) {
          image.copy(isSelected = !image.isSelected)
        } else {
          image.copy(isSelected = false)
        }
      }

    captchaInfo.tasks[taskIndex] = task.copy(images = updatedImages)
    hapticFeedbackManager.tap()
  }

  private fun startOrRestartCaptchaTtlUpdateTask(chanDescriptor: ChanDescriptor) {
    captchaTtlUpdateJob?.cancel()
    captchaTtlUpdateJob = viewModelScope.launch(Dispatchers.Main) {
      while (isActive) {
        val captchaInfoAsyncData = _captchaInfoToShow.value

        val captchaInfo = if (captchaInfoAsyncData !is AsyncUiData.UiData) {
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
    chanDescriptor: ChanDescriptor,
    ticket: String?,
    mcl: String
  ): CaptchaInfo {
    _captchaDataJson.value = null

    val (captchaInfoRaw, captchaInfoRawString) = getCachedCaptchaOrLoadFresh(
      chanDescriptor = chanDescriptor,
      ticket = ticket,
      mcl = mcl
    )

    _captchaDataJson.value = captchaInfoRawString.takeIf { it.isNotBlank() }
    val now = System.currentTimeMillis()

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
          Logger.debug(TAG) {
            "requestCaptchaInternal($chanDescriptor) new thread creation rate limited! cooldownMs=$cooldownMs"
          }

          throw CaptchaThreadRateLimitError(
            cooldownEndTimeMs = now + cooldownMs,
            cooldownMs = cooldownMs
          )
        }
        is ChanDescriptor.ThreadDescriptor -> {
          Logger.debug(TAG) {
            "requestCaptchaInternal($chanDescriptor) new post creation rate limited! cooldownMs=$cooldownMs"
          }

          throw CaptchaPostRateLimitError(
            cooldownEndTimeMs = now + cooldownMs,
            cooldownMs = cooldownMs
          )
        }
      }
    }

    val captchaError = captchaInfoRaw.err
    if (captchaError != null) {
      when {
        // TODO: add errors when trying to create a post/thread without the ticket
        captchaError.contains(RATE_LIMIT_ERROR_MSG, ignoreCase = true) -> {
          val cooldownMs = captchaInfoRaw.cooldown?.times(1000L)
            ?: DEFAULT_COOLDOWN_MS

          Logger.d(TAG, "requestCaptchaInternal($chanDescriptor) rate limited! cooldownMs=$cooldownMs")
          throw CaptchaGenericRateLimitError(
            cooldownEndTimeMs = now + cooldownMs,
            cooldownMs = cooldownMs
          )
        }
        else -> {
          // Some unknown captcha error
          Logger.error(TAG) { "captchaError: '${captchaError}', captchaInfoRawString: '${captchaInfoRawString}'" }
          throw UnknownCaptchaError("Captcha error: '${captchaError}'")
        }
      }
    }

    val challenge = captchaInfoRaw.challenge
    if (challenge.isNullOrBlank()) {
      Logger.error(TAG) { "captchaInfoRawString: '${captchaInfoRawString}'" }
      throw UnknownCaptchaError("Captcha 'challenge' json field does not exist in the server response!")
    }

    if (captchaInfoRaw.isNoopChallenge()) {
      Logger.d(TAG, "requestCaptchaInternal($chanDescriptor) NOOP challenge detected")

      return CaptchaInfo(
        chanDescriptor = chanDescriptor,
        challenge = NOOP_CHALLENGE,
        startedAt = System.currentTimeMillis(),
        ttlSeconds = captchaInfoRaw.ttlSeconds(),
        newTasks = emptyList(),
      )
    }

    if (captchaInfoRaw.ttl == null) {
      Logger.error(TAG) { "captchaInfoRawString: '${captchaInfoRawString}'" }
      throw UnknownCaptchaError("Captcha 'ttl' json field does not exist in the server response!")
    }

    val tasks = captchaInfoRaw.tasks?.map { captchaTaskRaw ->
      val images = captchaTaskRaw.items.map { imageBase64 ->
        val bgByteArray = Base64.decode(imageBase64, Base64.DEFAULT)
        val imageBitmap = BitmapFactory.decodeByteArray(bgByteArray, 0, bgByteArray.size).asImageBitmap()

        return@map CaptchaInfo.TaskImage(
          imageBitmap = imageBitmap,
          isSelected = false
        )
      }

      val hasWideImages = images.any { image ->
        val bitmap = image.imageBitmap
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        return@any aspectRatio > 1.5f
      }

      val title = when {
        captchaTaskRaw.textTitle.isNotNullNorEmpty() -> Chan4CaptchaTitleFormatter().format(captchaTaskRaw.textTitle)
        captchaTaskRaw.imageTitle.isNotNullNorEmpty() -> {
          val decodedImageBytes = Base64.decode(captchaTaskRaw.imageTitle, Base64.DEFAULT)
          val bitmapImage = BitmapFactory.decodeByteArray(decodedImageBytes, 0, decodedImageBytes.size).asImageBitmap()
          Chan4CaptchaTitleFormatter.Title.Image(bitmapImage)
        }
        else -> null
      }

      CaptchaInfo.Task(
        title = title,
        hasWideImages = hasWideImages,
        images = images
      )
    }

    return CaptchaInfo(
      chanDescriptor = chanDescriptor,
      challenge = challenge,
      startedAt = System.currentTimeMillis(),
      ttlSeconds = captchaInfoRaw.ttl,
      newTasks = tasks ?: emptyList(),
    )
  }

  suspend fun handleCaptchaRequestError(
    chanDescriptor: ChanDescriptor,
    error: Throwable
  ) {
    Logger.e(TAG, "requestCaptcha()", error)

    if (!error.isCancellationException()) {
      _captchaInfoToShow.value = AsyncUiData.Error(error)
    }

    if (error is CaptchaCooldownError) {
      Logger.debug(TAG) {
        "requestCaptcha() error is CaptchaCooldownError, starting the waiter for ${chanDescriptor}"
      }
      chan4CaptchaNotifierManager.start(chanDescriptor, error.cooldownEndTimeMs)

      if (!chan4CaptchaNotifierManager.wait()) {
        Logger.debug(TAG) {
          "requestCaptcha() chan4CaptchaNotifierManager.wait() was canceled for ${chanDescriptor}"
        }

        return
      }

      withContext(Dispatchers.Main) {
        requestCaptcha(chanDescriptor = chanDescriptor, mcl = "", forced = true)
      }

      return
    }

    if (error is LoadChan4CaptchaUseCase.AntibotCheckDetected) {
      Logger.debug(TAG) { "requestCaptcha() error is AntibotCheckDetected, loading WebView" }

      val document = Jsoup.parseBodyFragment(error.htmlToLoad)
      val challengeUrl = document.select("script#_mcl").attr("src").toHttpUrl()

      val taskResult = webViewTaskManager.performWebViewTask(
        SpurUsAntibotTask(
          headerTitleText = "SpurUsAntibot",
          loadableUrl = AbstractWebViewTask.Loadable.Url(challengeUrl),
          invokerWaiter = CompletableDeferred<WebViewTaskResult>()
        )
      )

      if (taskResult is WebViewTaskResult.Result) {
        _captchaInfoToShow.value = AsyncUiData.Loading

        val mcl = taskResult.rawCookies as String
        Logger.debug(TAG) {
          "Got SpurUsAntibot mcl (wtf is even this shit?): '${mcl.asFormattedToken()}'. Retrying captcha."
        }

        withContext(Dispatchers.Main) {
          requestCaptcha(chanDescriptor = chanDescriptor, mcl = mcl, forced = true)
        }

        return
      }

      Logger.error(TAG, error) { "Failed to pass SpurUsAntibot, taskResult: ${taskResult}" }
      _captchaInfoToShow.value = AsyncUiData.Error(UnknownCaptchaError("Failed to pass SpurUsAntibot"))
    }
  }

  private suspend fun getCachedCaptchaOrLoadFresh(
    chanDescriptor: ChanDescriptor,
    ticket: String?,
    mcl: String
  ): Pair<LoadChan4CaptchaUseCase.CaptchaInfoRaw, String> {
    Logger.debug(TAG) {
      "getCachedCaptchaOrLoadFresh(${chanDescriptor}, ${ticket.asFormattedToken()}) requesting fresh captcha"
    }

    val captchaResult = loadChan4CaptchaUseCase.await(
      chanDescriptor = chanDescriptor,
      ticket = ticket,
      mcl = mcl,
    ).unwrap()

    val captchaInfoRaw = captchaResult.captchaInfoRaw
    val captchaInfoRawString = captchaResult.captchaInfoRawString

    return captchaInfoRaw to captchaInfoRawString
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

  class CaptchaInfo(
    val chanDescriptor: ChanDescriptor,
    val challenge: String,
    val startedAt: Long,
    val ttlSeconds: Int,
    newTasks: List<Task>
  ) {
    val tasks = mutableStateListOf<Task>()

    init {
      tasks.clear()
      tasks.addAll(newTasks)
    }

    fun reset() {
      tasks.forEachIndexed { index, task ->
        tasks[index] = task.copy(images = task.images.map { image -> image.copy(isSelected = false) })
      }
    }

    fun ttlMillis(): Long {
      val ttlMillis = ttlSeconds * 1000L

      return ttlMillis - (System.currentTimeMillis() - startedAt)
    }

    fun isNoopChallenge(): Boolean {
      return challenge.equals(NOOP_CHALLENGE, ignoreCase = true)
    }

    fun isFilledIn(): Boolean {
      if (tasks.isEmpty()) {
        return true
      }

      return tasks.all { task ->
        task.images.any { image -> image.isSelected }
      }
    }

    fun solution(): String {
      return buildString {
        tasks.forEach { task ->
          for ((imageIndex, image) in task.images.withIndex()) {
            if (image.isSelected) {
              append(imageIndex)
              break
            }
          }
        }
      }
    }

    data class Task(
      val title: Chan4CaptchaTitleFormatter.Title?,
      val hasWideImages: Boolean,
      val images: List<TaskImage>
    ) {
      fun drawFakeSliderAndNextButton(): Boolean {
        if (title is Chan4CaptchaTitleFormatter.Title.Image) {
          return true
        }

        if (title is Chan4CaptchaTitleFormatter.Title.TextWithImage) {
          return title.annotated.text.contains("not like the others", ignoreCase = true)
        }

        return false
      }
    }

    data class TaskImage(
      val imageBitmap: ImageBitmap,
      val isSelected: Boolean
    )

  }

  interface CaptchaCooldownError {
    val cooldownEndTimeMs: Long
    val cooldownMs: Long
  }

  class CaptchaGenericRateLimitError(
    override val cooldownEndTimeMs: Long,
    override val cooldownMs: Long
  ) :
    Exception("4chan captcha rate-limit detected!\nCaptcha will be reloaded automatically in ${cooldownMs / 1000L}s"),
    CaptchaCooldownError

  class CaptchaThreadRateLimitError(
    override val cooldownEndTimeMs: Long,
    override val cooldownMs: Long
  ) :
    Exception("4chan captcha rate-limit detected!\nPlease wait ${cooldownMs / 1000L} seconds before making a thread."),
    CaptchaCooldownError

  class CaptchaPostRateLimitError(
    override val cooldownEndTimeMs: Long,
    override val cooldownMs: Long
  ) :
    Exception("4chan captcha rate-limit detected!\nPlease wait ${cooldownMs / 1000L} seconds before making a post."),
    CaptchaCooldownError

  class UnknownCaptchaError(message: String) : java.lang.Exception(message)

  class ViewModelFactory @Inject constructor(
    private val siteManager: SiteManager,
    private val loadChan4CaptchaUseCase: LoadChan4CaptchaUseCase,
    private val hapticFeedbackManager: HapticFeedbackManager,
    private val firewallBypassManager: FirewallBypassManager,
    private val chan4CaptchaNotifierManager: Chan4CaptchaNotifierManager,
    private val webViewTaskManager: WebViewTaskManager,
  ) : ViewModelAssistedFactory<Chan4CaptchaLayoutViewModel> {
    override fun create(handle: SavedStateHandle): Chan4CaptchaLayoutViewModel {
      return Chan4CaptchaLayoutViewModel(
        savedStateHandle = handle,
        siteManager = siteManager,
        loadChan4CaptchaUseCase = loadChan4CaptchaUseCase,
        hapticFeedbackManager = hapticFeedbackManager,
        firewallBypassManager = firewallBypassManager,
        chan4CaptchaNotifierManager = chan4CaptchaNotifierManager,
        webViewTaskManager = webViewTaskManager
      )
    }
  }

  companion object {
    private const val TAG = "Chan4CaptchaLayoutViewModel"
    private const val RATE_LIMIT_ERROR_MSG = "You have to wait a while before doing this again"
    private const val DEFAULT_COOLDOWN_MS = 5000L

    private const val MIN_TTL_TO_NOT_REQUEST_NEW_CAPTCHA = 25_000L // 25 seconds
    private const val MIN_TTL_TO_RESET_CAPTCHA = 5_000L // 5 seconds

    const val NOOP_CHALLENGE = "noop"
  }

}