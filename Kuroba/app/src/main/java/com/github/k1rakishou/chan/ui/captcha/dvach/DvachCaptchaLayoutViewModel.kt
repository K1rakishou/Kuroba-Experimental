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
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.HapticFeedbackManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach
import com.github.k1rakishou.chan.utils.HashingUtil
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.suspendConvertIntoJsonObjectWithAdapter
import com.github.k1rakishou.common.unreachable
import com.github.k1rakishou.core_logger.Logger
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

class DvachCaptchaLayoutViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val proxiedOkHttpClient: RealProxiedOkHttpClient,
  private val siteManager: SiteManager,
  private val moshi: Moshi,
  private val hapticFeedbackManager: HapticFeedbackManager
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
        is ModularResult.Error -> {
          Logger.error(TAG, result.error) { "requestCaptcha(${captchaUrl}) error" }
          AsyncData.Error(result.error)
        }
        is ModularResult.Value -> {
          Logger.debug(TAG) { "requestCaptcha(${captchaUrl}) success: ${result.value::class.java.name}" }
          AsyncData.Data(result.value)
        }
      }
    }
  }

  fun cleanup() {
    currentInputValue.value = ""
    captchaInfoToShow.value = AsyncData.NotInitialized
    currentPuzzlePieceOffsetValue.value = Offset.Unspecified

    activeJob?.cancel()
    activeJob = null
  }

  suspend fun onEmojiKeyboardKeyClicked(keyIndex: Int, captchaInfo: CaptchaInfo.Emoji): String? {
    val dvach = siteManager.bySiteDescriptor(Dvach.SITE_DESCRIPTOR) as? Dvach
    if (dvach == null) {
      return null
    }

    Logger.debug(TAG) { "onEmojiKeyboardKeyClicked(${keyIndex})" }

    hapticFeedbackManager.tap()

    val result = ModularResult.Try { performEmojiClickRequest(dvach, captchaInfo, keyIndex) }
    when (result) {
      is ModularResult.Error -> {
        Logger.error(TAG, result.error) { "onEmojiKeyboardKeyClicked(${keyIndex}) error" }
        captchaInfoToShow.value = AsyncData.Error(result.error)

        return null
      }
      is ModularResult.Value -> {
        Logger.debug(TAG) { "onEmojiKeyboardKeyClicked(${keyIndex}) ${result.value::class.java.name}" }
        captchaInfoToShow.value = AsyncData.Data(result.value)

        return when (val newCaptchaInfo = result.value) {
          is CaptchaInfo.Puzzle,
          is CaptchaInfo.Text -> null
          is CaptchaInfo.Emoji -> {
            Logger.debug(TAG) { "onEmojiKeyboardKeyClicked(${keyIndex}) successId: '${newCaptchaInfo.successId}'" }
            newCaptchaInfo.successId
          }
        }
      }
    }
  }

  private suspend fun performEmojiClickRequest(dvach: Dvach, captchaInfo: CaptchaInfo.Emoji, keyIndex: Int): CaptchaInfo {
    val clickEmojiRequest = ClickEmojiRequest(
      captchaTokenID = captchaInfo.id,
      emojiNumber = keyIndex
    )

    val clickEmojiRequestJson = moshi.adapter(ClickEmojiRequest::class.java)
      .toJson(clickEmojiRequest)

    val requestBuilder = Request.Builder()
      .url("${dvach.domainString}/api/captcha/emoji/click")
      .post(clickEmojiRequestJson.toRequestBody("application/json".toMediaType()))

    dvach.requestModifier().modifyCaptchaGetRequest(dvach, requestBuilder)

    val emojiCaptchaInfo = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
      request = requestBuilder.build(),
      adapter = moshi.adapter(EmojiCaptchaInfo::class.java)
    ).unwrap()

    if (emojiCaptchaInfo == null) {
      throw DvachCaptchaError("emojiCaptchaInfo is null")
    }

    when (emojiCaptchaInfo) {
      is EmojiCaptchaInfo.Success -> {
        return captchaInfo.copy(successId = emojiCaptchaInfo.success)
      }
      is EmojiCaptchaInfo.Image -> {
        val imageData = emojiCaptchaInfo.image
        if (imageData == null) {
          throw DvachCaptchaError("image is null")
        }

        val imageBitmap = run {
          val byteArray = Base64.decode(imageData, Base64.DEFAULT)
          return@run BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        }

        val emojiKeys = emojiCaptchaInfo.keyboard.map { keyboardBitmapData ->
          val byteArray = Base64.decode(keyboardBitmapData, Base64.DEFAULT)
          val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

          return@map CaptchaInfo.Emoji.EmojiKey(
            bitmap = bitmap,
            hash = HashingUtil.stringHashSha512(keyboardBitmapData)
          )
        }

        return CaptchaInfo.Emoji(
          id = captchaInfo.id,
          hash = captchaInfo.hash,
          image = imageBitmap,
          emojiKeys = emojiKeys,
          successId = null
        )
      }
    }
  }

  private suspend fun requestCaptchaIdInternal(captchaUrl: String): CaptchaInfo {
    val captchaType = when {
      captchaUrl.endsWith("captcha/2chcaptcha/id") -> DvachCaptchaType.Text
      captchaUrl.endsWith("captcha/puzzle") -> DvachCaptchaType.Puzzle
      captchaUrl.endsWith("captcha/emoji/id") -> DvachCaptchaType.Emoji
      else -> DvachCaptchaType.Text
    }

    Logger.d(TAG, "requestCaptchaInternal() requesting ${captchaUrl}, captchaType: ${captchaType}")

    val requestBuilder = Request.Builder()
      .url(captchaUrl)
      .get()

    val dvach = siteManager.bySiteDescriptor(Dvach.SITE_DESCRIPTOR) as? Dvach
    if (dvach == null) {
      throw DvachCaptchaError("Site ${Dvach.SITE_DESCRIPTOR} is not supported")
    }

    dvach.requestModifier().modifyCaptchaGetRequest(dvach, requestBuilder)

    val request = requestBuilder.build()

    val captchaInfoData = kotlin.runCatching {
      when (captchaType) {
        DvachCaptchaType.Text -> createTextCaptcha(request)
        DvachCaptchaType.Puzzle -> createPuzzleCaptcha(request)
        DvachCaptchaType.Emoji -> createEmojiCaptcha(dvach, request)
      }
    }

    val exception = captchaInfoData.exceptionOrNull()
    if (exception != null) {
      throw DvachCaptchaError(exception.message ?: "Failed to convert CaptchaInfoData into CaptchaInfo")
    }

    return requireNotNull(captchaInfoData.getOrNull()) { "Result<CaptchaInfo>.getOrNull() returned null!" }
  }

  private suspend fun createEmojiCaptcha(dvach: Dvach, request: Request): CaptchaInfo.Emoji {
    val captchaInfoData = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
      request = request,
      adapter = moshi.adapter(CaptchaInfoData.Emoji::class.java)
    ).unwrap()

    if (captchaInfoData == null) {
      throw DvachCaptchaError("captchaInfoData is null")
    }

    val requestBuilder = Request.Builder()
      .url("${dvach.domainString}/api/captcha/emoji/show?id=${captchaInfoData.id}")
      .get()

    dvach.requestModifier().modifyCaptchaGetRequest(dvach, requestBuilder)

    val emojiCaptchaInfo = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
      request = requestBuilder.build(),
      adapter = moshi.adapter(EmojiCaptchaInfo.Image::class.java)
    ).unwrap()

    if (emojiCaptchaInfo == null) {
      throw DvachCaptchaError("emojiCaptchaInfo is null")
    }

    val imageData = emojiCaptchaInfo.image
    if (imageData == null) {
      throw DvachCaptchaError("image is null")
    }

    val imageBitmap = run {
      val byteArray = Base64.decode(imageData, Base64.DEFAULT)
      return@run BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    val emojiKeys = emojiCaptchaInfo.keyboard.map { keyboardBitmapData ->
      val byteArray = Base64.decode(keyboardBitmapData, Base64.DEFAULT)
      val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

      return@map CaptchaInfo.Emoji.EmojiKey(
        bitmap = bitmap,
        hash = HashingUtil.stringHashSha512(keyboardBitmapData)
      )
    }

    return CaptchaInfo.Emoji(
      id = captchaInfoData.id!!,
      hash = captchaInfoData.challenge!!.hash!!,
      image = imageBitmap,
      emojiKeys = emojiKeys
    )
  }

  private suspend fun createPuzzleCaptcha(request: Request): CaptchaInfo.Puzzle {
    val captchaInfoAdapter = moshi.adapter(CaptchaInfoData.Puzzle::class.java)

    val captchaInfoData = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
      request = request,
      adapter = captchaInfoAdapter
    ).unwrap()

    if (captchaInfoData == null) {
      throw DvachCaptchaError("captchaInfoData is null")
    }

    val id = requireNotNull(captchaInfoData.id) { "CaptchaInfoData.Puzzle.id is null!" }
    val image = requireNotNull(captchaInfoData.image) { "CaptchaInfoData.Puzzle.image is null!" }
    val input = requireNotNull(captchaInfoData.input) { "CaptchaInfoData.Puzzle.input is null!" }
    val puzzle = requireNotNull(captchaInfoData.puzzle) { "CaptchaInfoData.Puzzle.puzzle is null!" }
    val type = requireNotNull(captchaInfoData.type) { "CaptchaInfoData.Puzzle.type is null!" }

    val imageBitmap = run {
      val byteArray = Base64.decode(image, Base64.DEFAULT)
      return@run BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    val puzzleBitmap = run {
      val byteArray = Base64.decode(puzzle, Base64.DEFAULT)
      return@run BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    return CaptchaInfo.Puzzle(
      id = id,
      image = imageBitmap,
      input = input,
      puzzle = puzzleBitmap,
      type = type
    )
  }

  private suspend fun createTextCaptcha(request: Request): CaptchaInfo.Text {
    val captchaInfoAdapter = moshi.adapter(CaptchaInfoData.Text::class.java)

    val captchaInfoData = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
      request = request,
      adapter = captchaInfoAdapter
    ).unwrap()

    if (captchaInfoData == null) {
      throw DvachCaptchaError("Failed to convert json into CaptchaInfo")
    }

    if (!captchaInfoData.isValidDvachCaptcha()) {
      throw DvachCaptchaError("Invalid dvach captcha info: ${captchaInfoData}")
    }

    val id = requireNotNull(captchaInfoData.id) { "CaptchaInfoData.Text.id is null!" }
    val type = requireNotNull(captchaInfoData.type) { "CaptchaInfoData.Text.type is null!" }
    val input = requireNotNull(captchaInfoData.input) { "CaptchaInfoData.Text.input is null!" }

    return CaptchaInfo.Text(
      id = id,
      type = type,
      input = input
    )
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

    data class Emoji(
      val id: String,
      val hash: String,
      val image: Bitmap,
      val emojiKeys: List<EmojiKey>,
      val successId: String? = null
    ) : CaptchaInfo {

      data class EmojiKey(
        val bitmap: Bitmap,
        val hash: String
      )

    }

  }

  sealed class EmojiCaptchaInfo {

    @JsonClass(generateAdapter = true)
    data class Success(
      val success: String
    ) : EmojiCaptchaInfo()

    @JsonClass(generateAdapter = true)
    data class Image(
      val image: String? = null,
      val keyboard: List<String> = listOf()
    ) : EmojiCaptchaInfo()

    class Adapter {

      @FromJson
      fun fromJson(reader: JsonReader): EmojiCaptchaInfo? {
        var success: String? = null
        var image: String? = null
        val keyboard = mutableListOf<String>()

        reader.beginObject()

        while (reader.hasNext()) {
          when (reader.nextName()) {
            "success" -> {
              success = reader.nextString()
            }
            "image" -> {
              image = reader.nextString()
            }
            "keyboard" -> {
              reader.beginArray()

              while (reader.hasNext() && reader.peek() == JsonReader.Token.STRING) {
                keyboard += reader.nextString()
              }

              reader.endArray()
            }
            else -> reader.skipValue()
          }
        }

        reader.endObject()

        return when {
          success != null -> EmojiCaptchaInfo.Success(success)
          image != null -> EmojiCaptchaInfo.Image(image, keyboard)
          else -> null
        }
      }

      @ToJson
      fun toJson(writer: JsonWriter, value: EmojiCaptchaInfo?) {
        unreachable()
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

    @JsonClass(generateAdapter = true)
    data class Emoji(
      val challenge: Challenge? = null,
      val id: String? = null,
      val input: String? = null,
      val result: Int? = null,
      val type: String? = null
    ) : CaptchaInfoData {

      override fun isValidDvachCaptcha(): Boolean {
        return challenge != null
          && id.isNotNullNorEmpty()
          && input.isNotNullNorEmpty()
          && result == 1
          && type == "emoji"
      }

      @JsonClass(generateAdapter = true)
      data class Challenge(
        val hash: String? = null,
        val limit: Int? = null,
        val template: String? = null
      )

    }

  }

  enum class DvachCaptchaType {
    Text,
    Puzzle,
    Emoji
  }

  @JsonClass(generateAdapter = true)
  data class ClickEmojiRequest(
    val captchaTokenID: String,
    val emojiNumber: Int
  )

  class ViewModelFactory @Inject constructor(
    private val proxiedOkHttpClient: RealProxiedOkHttpClient,
    private val siteManager: SiteManager,
    private val moshi: Moshi,
    private val hapticFeedbackManager: HapticFeedbackManager
  ) : ViewModelAssistedFactory<DvachCaptchaLayoutViewModel> {
    override fun create(handle: SavedStateHandle): DvachCaptchaLayoutViewModel {
      return DvachCaptchaLayoutViewModel(
        savedStateHandle = handle,
        proxiedOkHttpClient = proxiedOkHttpClient,
        siteManager = siteManager,
        moshi = moshi,
        hapticFeedbackManager = hapticFeedbackManager
      )
    }
  }

  companion object {
    private const val TAG = "DvachCaptchaLayoutViewModel"
  }

}