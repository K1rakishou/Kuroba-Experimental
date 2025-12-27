package com.github.k1rakishou.chan.ui.captcha.chan4

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.Chan4CaptchaNotifierManager
import com.github.k1rakishou.chan.core.manager.FirewallBypassManager
import com.github.k1rakishou.chan.core.manager.HapticFeedbackManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4CaptchaSettings
import com.github.k1rakishou.chan.core.usecase.LoadChan4CaptchaUseCase
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.prefs.GsonJsonSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.Locale
import javax.inject.Inject

class Chan4CaptchaLayoutViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val siteManager: SiteManager,
  private val loadChan4CaptchaUseCase: LoadChan4CaptchaUseCase,
  private val hapticFeedbackManager: HapticFeedbackManager,
  private val firewallBypassManager: FirewallBypassManager,
  private val chan4CaptchaNotifierManager: Chan4CaptchaNotifierManager,
) : BaseViewModel() {

  private var activeJob: Job? = null
  private var captchaTtlUpdateJob: Job? = null

  val chan4CaptchaSettingsJson by lazy {
    siteManager.bySiteDescriptor(Chan4.SITE_DESCRIPTOR)!!
      .getSettingBySettingId<GsonJsonSetting<Chan4CaptchaSettings>>(SiteSetting.SiteSettingId.Chan4CaptchaSettings)!!
  }

  private val captchaInfoCache = mutableMapOf<ChanDescriptor, CaptchaInfo>()

  private val _captchaTtlMillisFlow = MutableStateFlow(-1L)
  val captchaTtlMillisFlow: StateFlow<Long>
    get() = _captchaTtlMillisFlow.asStateFlow()

  private val _captchaInfoToShow = mutableStateOf<AsyncData<CaptchaInfo>>(AsyncData.NotInitialized)
  val captchaInfoToShow: State<AsyncData<CaptchaInfo>>
    get() = _captchaInfoToShow

  private val _captchaDataJson = mutableStateOf<String?>(null)
  val captchaDataJson: State<String?>
    get() = _captchaDataJson

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
  }

  fun onCaptchaViewInitialized() {
    chan4CaptchaNotifierManager.onCaptchaViewInitialized()
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

  fun requestCaptcha(chanDescriptor: ChanDescriptor, forced: Boolean) {
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

      _captchaInfoToShow.value = AsyncData.Data(prevCaptchaInfo)
      startOrRestartCaptchaTtlUpdateTask(chanDescriptor)

      return
    }

    Logger.d(TAG, "requestCaptcha() requesting new captcha " +
      "(forced: $forced, ttl: ${prevCaptchaInfo?.ttlMillis()}, chanDescriptor=$chanDescriptor)")

    _captchaTtlMillisFlow.value = -1L
    getCachedCaptchaInfoOrNull(chanDescriptor)?.reset()

    captchaInfoCache.remove(chanDescriptor)

    activeJob = viewModelScope.launch(Dispatchers.Default) {
      _captchaInfoToShow.value = AsyncData.Loading

      val result = ModularResult.Try {
        if (forced) {
          firewallBypassManager.removeHostTimeCheckByChanDescriptor(chanDescriptor)
        }

        requestCaptchaInternal(
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
            val lambda: (Long) -> Boolean = start@{ remainingCooldownMs ->
              if (_captchaInfoToShow.value is AsyncData.NotInitialized) {
                return@start false
              }

              val previousError = (_captchaInfoToShow.value as? AsyncData.Error)?.throwable
                ?: return@start true

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
                  return@start true
                }
              }

              return@start false
            }

            val lambdaWeak = WeakReference(lambda)
            chan4CaptchaNotifierManager.start(chanDescriptor, error.cooldownMs, lambdaWeak)

            if (!chan4CaptchaNotifierManager.wait()) {
              return@launch
            }

            withContext(Dispatchers.Main) { requestCaptcha(chanDescriptor, forced = true) }
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

  fun onCaptchaImageClicked(taskIndex: Int, imageIndex: Int) {
    val captchaInfoAsyncData = _captchaInfoToShow.value

    val captchaInfo = if (captchaInfoAsyncData !is AsyncData.Data) {
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
    chanDescriptor: ChanDescriptor,
    ticket: String?
  ): CaptchaInfo {
    _captchaDataJson.value = null

    val (captchaInfoRaw, captchaInfoRawString) = getCachedCaptchaOrLoadFresh(
      chanDescriptor = chanDescriptor,
      ticket = ticket
    )

    _captchaDataJson.value = captchaInfoRawString.takeIf { it.isNotBlank() }

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
          Logger.debug(TAG) { "requestCaptchaInternal($chanDescriptor) new thread creation rate limited! cooldownMs=$cooldownMs" }
          throw CaptchaThreadRateLimitError(cooldownMs)
        }
        is ChanDescriptor.ThreadDescriptor -> {
          Logger.d(TAG, "requestCaptchaInternal($chanDescriptor) new post creation rate limited! cooldownMs=$cooldownMs")
          throw CaptchaPostRateLimitError(cooldownMs)
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
          throw CaptchaGenericRateLimitError(cooldownMs)
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

      val title = formatTitle(captchaTaskRaw)

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

  private fun formatTitle(captchaTaskRaw: LoadChan4CaptchaUseCase.CaptchaTaskRaw): AnnotatedString {
    var title = captchaTaskRaw.title
      ?: "Use the scroll bar below to find the image that is not like the others, then click Next."

    title = title.removePrefix("Use the scroll bar below to ")
    title = title.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(Locale.ENGLISH) else ch.toString() }
    title = title.removeSuffix(", then click Next.")
    title += "."

    val annotatedTitle = addAnnotations(title)
    return annotatedTitle
  }

  private fun addAnnotations(input: String): AnnotatedString {
    val openTag = "<b>"
    val closeTag = "</b>"

    val span = SpanStyle(
      fontWeight = FontWeight.Bold,
      textDecoration = TextDecoration.Underline
    )

    return buildAnnotatedString {
      val boldStack = ArrayDeque<Int>()
      var offset = 0
      var removedChars = 0
      var firstTagSkipped = false

      while (offset < input.length) {
        when {
          input.startsWith(openTag, offset) -> {
            boldStack.add(offset)
            offset += openTag.length
          }

          input.startsWith(closeTag, offset) -> {
            var start = boldStack.removeLastOrNull()
            if (start != null) {
              if (!firstTagSkipped) {
                firstTagSkipped = true
              } else {
                start -= removedChars
              }

              val end = offset - removedChars - openTag.length
              if (end < offset && end <= length && start < end) {
                addStyle(span, start, end)
              }
            }

            offset += closeTag.length
            removedChars += (openTag.length + closeTag.length)
          }

          else -> {
            append(input[offset])
            offset += 1
          }
        }
      }

      while (boldStack.isNotEmpty()) {
        val start = boldStack.removeLast()
        if (start <= length) {
          addStyle(span, start, length)
        }
      }
    }
  }

  private suspend fun getCachedCaptchaOrLoadFresh(
    chanDescriptor: ChanDescriptor,
    ticket: String?
  ): Pair<LoadChan4CaptchaUseCase.CaptchaInfoRaw, String> {
    Logger.debug(TAG) {
      "getCachedCaptchaOrLoadFresh(${chanDescriptor}, ${StringUtils.formatToken(ticket)}) requesting fresh captcha"
    }

    val captchaResult = loadChan4CaptchaUseCase.await(
      chanDescriptor = chanDescriptor,
      ticket = ticket
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
      val title: AnnotatedString,
      val hasWideImages: Boolean,
      val images: List<TaskImage>
    )

    data class TaskImage(
      val imageBitmap: ImageBitmap,
      val isSelected: Boolean
    )

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

  class ViewModelFactory @Inject constructor(
    private val siteManager: SiteManager,
    private val loadChan4CaptchaUseCase: LoadChan4CaptchaUseCase,
    private val hapticFeedbackManager: HapticFeedbackManager,
    private val firewallBypassManager: FirewallBypassManager,
    private val chan4CaptchaNotifierManager: Chan4CaptchaNotifierManager,
  ) : ViewModelAssistedFactory<Chan4CaptchaLayoutViewModel> {
    override fun create(handle: SavedStateHandle): Chan4CaptchaLayoutViewModel {
      return Chan4CaptchaLayoutViewModel(
        savedStateHandle = handle,
        siteManager = siteManager,
        loadChan4CaptchaUseCase = loadChan4CaptchaUseCase,
        hapticFeedbackManager = hapticFeedbackManager,
        firewallBypassManager = firewallBypassManager,
        chan4CaptchaNotifierManager = chan4CaptchaNotifierManager,
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