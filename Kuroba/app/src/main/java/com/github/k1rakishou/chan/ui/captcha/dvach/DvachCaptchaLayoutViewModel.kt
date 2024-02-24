package com.github.k1rakishou.chan.ui.captcha.dvach

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.viewmodel.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.suspendConvertIntoJsonObjectWithAdapter
import com.github.k1rakishou.core_logger.Logger
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import javax.inject.Inject

class DvachCaptchaLayoutViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val proxiedOkHttpClient: RealProxiedOkHttpClient,
  private val siteManager: SiteManager,
  private val moshi: Moshi,
) : BaseViewModel() {

  private var activeJob: Job? = null
  var captchaInfoToShow = mutableStateOf<AsyncData<CaptchaInfo>>(AsyncData.NotInitialized)
  var currentInputValue = mutableStateOf<String>("")
  var currentPuzzlePieceOffsetValue = mutableStateOf<Offset>(Offset.Unspecified)

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {

  }

  fun requestCaptcha(captchaUrl: String) {
    activeJob?.cancel()
    activeJob = null
    currentInputValue.value = ""
    currentPuzzlePieceOffsetValue.value = Offset.Unspecified

    activeJob = viewModelScope.launch {
      captchaInfoToShow.value = AsyncData.Loading

      val result = ModularResult.Try { requestCaptchaIdInternal(captchaUrl) }
      captchaInfoToShow.value = when (result) {
        is ModularResult.Error -> AsyncData.Error(result.error)
        is ModularResult.Value -> AsyncData.Data(result.value)
      }
    }
  }

  private suspend fun requestCaptchaIdInternal(captchaUrl: String): CaptchaInfo {
    val captchaType = when {
      captchaUrl.endsWith("captcha/2chcaptcha/id") -> DvachCaptchaType.Text
      captchaUrl.endsWith("captcha/puzzle") -> DvachCaptchaType.Puzzle
      else -> DvachCaptchaType.Text
    }

    Logger.d(TAG, "requestCaptchaInternal() requesting ${captchaUrl}, captchaType: ${captchaType}")

    val requestBuilder = Request.Builder()
      .url(captchaUrl)
      .get()

    siteManager.bySiteDescriptor(Dvach.SITE_DESCRIPTOR)?.let { site ->
      site.requestModifier().modifyCaptchaGetRequest(site, requestBuilder)
    }

    val request = requestBuilder.build()

    val captchaInfoData = when (captchaType) {
      DvachCaptchaType.Text -> {
        val captchaInfoAdapter = moshi.adapter(CaptchaInfoData.Text::class.java)

        proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
          request = request,
          adapter = captchaInfoAdapter
        ).unwrap()
      }
      DvachCaptchaType.Puzzle -> {
        val captchaInfoAdapter = moshi.adapter(CaptchaInfoData.Puzzle::class.java)

        proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
          request = request,
          adapter = captchaInfoAdapter
        ).unwrap()
      }
    }

    if (captchaInfoData == null) {
      throw DvachCaptchaError("Failed to convert json into CaptchaInfo")
    }

    if (!captchaInfoData.isValidDvachCaptcha()) {
      throw DvachCaptchaError("Invalid dvach captcha info: ${captchaInfoData}")
    }

    val captchaInfo = CaptchaInfo.fromCaptchaInfoData(captchaInfoData)
    val exception = captchaInfo.exceptionOrNull()

    if (exception != null) {
      throw DvachCaptchaError(exception.message ?: "Failed to convert CaptchaInfoData into CaptchaInfo")
    }

    return requireNotNull(captchaInfo.getOrNull()) { "Result<CaptchaInfo>.getOrNull() returned null!" }
  }

  fun cleanup() {
    currentInputValue.value = ""
    captchaInfoToShow.value = AsyncData.NotInitialized
    currentPuzzlePieceOffsetValue.value = Offset.Unspecified

    activeJob?.cancel()
    activeJob = null
  }

  class DvachCaptchaError(message: String) : Exception(message)

  sealed interface CaptchaInfo {

    data class Text(
      val id: String,
      val type: String,
      val input: String
    ) : CaptchaInfo {

      fun fullRequestUrl(siteManager: SiteManager): HttpUrl? {
        val dvach = siteManager.bySiteDescriptor(Dvach.SITE_DESCRIPTOR) as? Dvach
          ?: return null

        return "${dvach.domainString}/api/captcha/2chcaptcha/show?id=$id".toHttpUrl()
      }

    }

    data class Puzzle(
      val id: String,
      val image: Bitmap,
      val input: String,
      val puzzle: Bitmap,
      val type: String,
    ) : CaptchaInfo

    companion object {
      internal fun fromCaptchaInfoData(data: CaptchaInfoData): Result<CaptchaInfo> {
        return Result.runCatching {
          when (data) {
            is CaptchaInfoData.Puzzle -> {
              val id = requireNotNull(data.id) { "CaptchaInfoData.Puzzle.id is null!" }
              val image = requireNotNull(data.image) { "CaptchaInfoData.Puzzle.image is null!" }
              val input = requireNotNull(data.input) { "CaptchaInfoData.Puzzle.input is null!" }
              val puzzle = requireNotNull(data.puzzle) { "CaptchaInfoData.Puzzle.puzzle is null!" }
              val type = requireNotNull(data.type) { "CaptchaInfoData.Puzzle.type is null!" }

              val imageBitmap = run {
                val byteArray = Base64.decode(image, Base64.DEFAULT)
                return@run BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
              }

              val puzzleBitmap = run {
                val byteArray = Base64.decode(puzzle, Base64.DEFAULT)
                return@run BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
              }

              return@runCatching CaptchaInfo.Puzzle(
                id = id,
                image = imageBitmap,
                input = input,
                puzzle = puzzleBitmap,
                type = type
              )
            }
            is CaptchaInfoData.Text -> {
              val id = requireNotNull(data.id) { "CaptchaInfoData.Text.id is null!" }
              val type = requireNotNull(data.type) { "CaptchaInfoData.Text.type is null!" }
              val input = requireNotNull(data.input) { "CaptchaInfoData.Text.input is null!" }

              return@runCatching CaptchaInfo.Text(
                id = id,
                type = type,
                input = input
              )
            }
          }
        }
      }
    }

  }

  internal sealed interface CaptchaInfoData {
    fun isValidDvachCaptcha(): Boolean

    @JsonClass(generateAdapter = true)
    data class Text(
      val id: String?,
      val type: String?,
      val input: String?
    ) : CaptchaInfoData {

      override fun isValidDvachCaptcha(): Boolean {
        return id.isNotNullNorEmpty() && type == "2chcaptcha"
      }

    }

    @JsonClass(generateAdapter = true)
    data class Puzzle(
      val id: String?,
      val image: String?,
      val input: String?,
      val puzzle: String?,
      val type: String?,
    ) : CaptchaInfoData {

      override fun isValidDvachCaptcha(): Boolean {
        return id.isNotNullNorEmpty() && image.isNotNullNorEmpty() && puzzle.isNotNullNorEmpty() && type == "puzzle"
      }

    }

  }

  enum class DvachCaptchaType {
    Text,
    Puzzle
  }

  class ViewModelFactory @Inject constructor(
    private val proxiedOkHttpClient: RealProxiedOkHttpClient,
    private val siteManager: SiteManager,
    private val moshi: Moshi,
  ) : ViewModelAssistedFactory<DvachCaptchaLayoutViewModel> {
    override fun create(handle: SavedStateHandle): DvachCaptchaLayoutViewModel {
      return DvachCaptchaLayoutViewModel(
        savedStateHandle = handle,
        proxiedOkHttpClient = proxiedOkHttpClient,
        siteManager = siteManager,
        moshi = moshi
      )
    }
  }

  companion object {
    private const val TAG = "DvachCaptchaLayoutViewModel"
  }

}