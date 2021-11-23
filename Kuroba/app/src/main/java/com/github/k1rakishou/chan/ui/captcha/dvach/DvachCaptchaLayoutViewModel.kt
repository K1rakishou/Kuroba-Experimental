package com.github.k1rakishou.chan.ui.captcha.dvach

import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
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

class DvachCaptchaLayoutViewModel : BaseViewModel() {

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

  fun requestCaptcha(captchaIdUrl: String) {
    activeJob?.cancel()
    activeJob = null
    currentInputValue.value = ""

    activeJob = mainScope.launch {
      captchaInfoToShow.value = AsyncData.Loading

      val result = ModularResult.Try { requestCaptchaIdInternal(captchaIdUrl) }
      captchaInfoToShow.value = when (result) {
        is ModularResult.Error -> AsyncData.Error(result.error)
        is ModularResult.Value -> AsyncData.Data(result.value)
      }
    }
  }

  private suspend fun requestCaptchaIdInternal(captchaIdUrl: String): CaptchaInfo {
    Logger.d(TAG, "requestCaptchaInternal() requesting $captchaIdUrl")

    val requestBuilder = Request.Builder()
      .url(captchaIdUrl)
      .get()

    siteManager.bySiteDescriptor(Dvach.SITE_DESCRIPTOR)?.let { site ->
      site.requestModifier().modifyCaptchaGetRequest(site, requestBuilder)
    }

    val request = requestBuilder.build()
    val captchaInfoAdapter = moshi.adapter(CaptchaInfo::class.java)

    val captchaInfo = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
      request = request,
      adapter = captchaInfoAdapter
    ).unwrap()

    if (captchaInfo == null) {
      throw DvachCaptchaError("Failed to convert json into CaptchaInfo")
    }

    if (!captchaInfo.isValidDvachCaptcha()) {
      throw DvachCaptchaError("Invalid dvach captcha info: ${captchaInfo}")
    }

    return captchaInfo
  }

  fun cleanup() {
    currentInputValue.value = ""
    captchaInfoToShow.value = AsyncData.NotInitialized

    activeJob?.cancel()
    activeJob = null
  }

  class DvachCaptchaError(message: String) : Exception(message)

  @JsonClass(generateAdapter = true)
  data class CaptchaInfo(
    val id: String?,
    val type: String?,
    val input: String?
  ) {
    fun isValidDvachCaptcha(): Boolean {
      return id.isNotNullNorEmpty() && type == "2chcaptcha"
    }

    fun fullRequestUrl(siteManager: SiteManager): HttpUrl? {
      if (id == null) {
        return null
      }

      val dvach = siteManager.bySiteDescriptor(Dvach.SITE_DESCRIPTOR) as? Dvach
        ?: return null

      return "${dvach.domainString}/api/captcha/2chcaptcha/show?id=$id".toHttpUrl()
    }
  }

  companion object {
    private const val TAG = "DvachCaptchaLayoutViewModel"
  }

}