package com.github.k1rakishou.chan.ui.captcha.dvach

import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.suspendConvertIntoJsonObjectWithAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Request
import javax.inject.Inject

class DvachCaptchaLayoutViewModel : BaseViewModel() {

  @Inject
  lateinit var proxiedOkHttpClient: RealProxiedOkHttpClient
  @Inject
  lateinit var moshi: Moshi

  private var activeJob: Job? = null
  var captchaIdToShow = mutableStateOf<AsyncData<String>>(AsyncData.NotInitialized)
  var currentInputValue = mutableStateOf<String>("")

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {

  }

  fun requestCaptcha(captchaIdUrl: String) {
    activeJob?.cancel()
    activeJob = null
    currentInputValue.value = ""

    activeJob = mainScope.launch {
      captchaIdToShow.value = AsyncData.Loading

      val result = ModularResult.Try { requestCaptchaIdInternal(captchaIdUrl) }
      captchaIdToShow.value = when (result) {
        is ModularResult.Error -> AsyncData.Error(result.error)
        is ModularResult.Value -> AsyncData.Data(result.value)
      }
    }
  }

  private suspend fun requestCaptchaIdInternal(captchaIdUrl: String): String {
    val request = Request.Builder()
      .url(captchaIdUrl)
      .get()
      .build()

    val captchaInfoAdapter = moshi.adapter(CaptchaInfo::class.java)

    val captchaInfo = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
      request = request,
      adapter = captchaInfoAdapter
    ).unwrap()

    if (!captchaInfo.isValidDvachCaptcha()) {
      throw DvachCaptchaError("Invalid dvach captcha info: ${captchaInfo}")
    }

    return captchaInfo.id!!
  }

  fun cleanup() {
    currentInputValue.value = ""
    captchaIdToShow.value = AsyncData.NotInitialized

    activeJob?.cancel()
    activeJob = null
  }

  class DvachCaptchaError(message: String) : Exception(message)

  @JsonClass(generateAdapter = true)
  data class CaptchaInfo(
    val id: String?,
    val type: String?,
  ) {
    fun isValidDvachCaptcha(): Boolean {
      return id.isNotNullNorEmpty() && type == "2chcaptcha"
    }
  }

}