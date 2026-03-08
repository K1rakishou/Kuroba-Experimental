package com.github.k1rakishou.chan.ui.captcha.lynxchan

import android.graphics.BitmapFactory
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.base.viewmodel.KurobaViewModel
import com.github.k1rakishou.chan.core.compose.AsyncUiData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.core.site.sites.lynxchan.Krautchan
import com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8.Chan8Moe
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.BaseLynxchanSite
import com.github.k1rakishou.chan.ui.captcha.lynxchan.pow.LynxchanProofOfWork
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils.asFormattedToken
import com.github.k1rakishou.common.addOrReplaceCookieHeader
import com.github.k1rakishou.common.awaitSilently
import com.github.k1rakishou.common.isContentTypeApplicationJson
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import javax.inject.Inject

class LynxchanCaptchaLayoutViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val appResources: AppResources,
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val siteManager: SiteManager,
  private val moshi: Moshi,
) : KurobaViewModel() {

  private val _captchaInfoToShow = mutableStateOf<AsyncUiData<LynxchanCaptchaFull>>(AsyncUiData.NotInitialized)
  val captchaInfoToShow: State<AsyncUiData<LynxchanCaptchaFull>>
    get() = _captchaInfoToShow
  private val _hashCashInfoToShow = mutableStateOf<HashCashInfo?>(null)
  val hashCashInfoToShow: State<HashCashInfo?>
    get() = _hashCashInfoToShow
  private val _captchaBlock = mutableStateOf<LynxchanCaptchaBlock?>(null)
  val captchaBlock: State<LynxchanCaptchaBlock?>
    get() = _captchaBlock
  val currentInputValue = mutableStateOf<String>("")

  private var _activeRequestCaptchaJob: Job? = null

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {}

  fun resetCaptchaForced() {
    _captchaInfoToShow.value = AsyncUiData.NotInitialized
  }

  fun cleanup() {
    _activeRequestCaptchaJob?.cancel()
    _activeRequestCaptchaJob = null
  }

  fun requestCaptcha(
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha?,
    chanDescriptor: ChanDescriptor,
    resetCaptchaCookies: Boolean
  ) {
    if (lynxchanCaptcha == null) {
      _captchaInfoToShow.value = AsyncUiData.Error(LynxchanCaptchaError("lynxchanCaptcha is null"))
      return
    }

    _activeRequestCaptchaJob?.cancel()
    _activeRequestCaptchaJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        _captchaInfoToShow.value = AsyncUiData.Loading
        _hashCashInfoToShow.value = null
        _captchaBlock.value = null
        currentInputValue.value = ""

        val site = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())
        if (site == null || site !is BaseLynxchanSite) {
          val message = if (site == null) {
            "Site is not active "
          } else {
            "Site ${site::class.java.simpleName} is not a Lynxchan site"
          }

          throw LynxchanCaptchaError(message)
        }

        if (resetCaptchaCookies) {
          site.settings.captchaIdCookie.set(null)
        }

        val needBlockBypass = needBlockBypass(
          lynxchanCaptcha = lynxchanCaptcha,
          chanDescriptor = chanDescriptor
        )

        val lynxchanCaptchaFull = requestCaptchaInternal(
          chanDescriptor = chanDescriptor,
          lynxchanCaptcha = lynxchanCaptcha,
          needBlockBypass = needBlockBypass
        )

        site.settings.captchaIdCookie.set(lynxchanCaptchaFull.captchaInfo.kurobaCookie)
        _captchaInfoToShow.value = AsyncUiData.UiData(lynxchanCaptchaFull)
      } catch (error: Throwable) {
        Logger.error(TAG, error) { "Failed to load captcha for ${chanDescriptor}" }
        _captchaInfoToShow.value = AsyncUiData.Error(error)
      }
    }
  }

  suspend fun verifyCaptcha(
    needBlockBypass: Boolean,
    chanDescriptor: ChanDescriptor,
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha,
    captchaInfo: LynxchanCaptchaFull,
    answer: String
  ): ModularResult<VerifyCaptchaResult> {
    return withContext(Dispatchers.IO) {
      return@withContext ModularResult.Try {
        val verifyCaptchaEndpoint = if (needBlockBypass) {
          lynxchanCaptcha.renewBypassEndpoint
        } else {
          lynxchanCaptcha.solveCaptchaEndpoint
        }

        Logger.d(TAG, "verifyCaptcha(needBlockBypass: ${needBlockBypass}) verifyCaptchaEndpoint=$verifyCaptchaEndpoint")

        val captchaId = captchaInfo.captchaInfo.captchaId
        if (captchaId.isNullOrEmpty()) {
          throw LynxchanCaptchaError("No captchaId provided")
        }

        if (answer.isNullOrEmpty()) {
          throw LynxchanCaptchaError("No answer provided")
        }

        val requestBody = if (needBlockBypass) {
          MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("captcha", answer)
            .build()
        } else {
          MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("captchaId", captchaId)
            .addFormDataPart("answer", answer)
            .build()
        }

        val requestBuilder = Request.Builder()
          .url(verifyCaptchaEndpoint)
          .post(requestBody)

        val lynxchanSite = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor()) as? BaseLynxchanSite
        if (lynxchanSite == null) {
          throw LynxchanCaptchaError("Site ${chanDescriptor.siteDescriptor()} is not active")
        }

        lynxchanSite.requestModifier.modifyGenericRequest(
          site = lynxchanSite,
          requestBuilder = requestBuilder
        )

        val response = proxiedOkHttpClient.okHttpClient().suspendCall(request = requestBuilder.build())
        if (!response.isSuccessful) {
          throw BadStatusResponseException(status = response.code)
        }

        val responseString = response.body.string()

        if (response.isContentTypeApplicationJson()) {
          val blockBypassStatus = moshi.adapter(BlockBypassStatus::class.java).fromJson(responseString)
          if (blockBypassStatus == null) {
            throw LynxchanCaptchaError("Failed to extract BlockBypassStatus from '$responseString'")
          }

          if (blockBypassStatus.isHashcash) {
            val lynxchanCaptchaFull = (_captchaInfoToShow.value as? AsyncUiData.UiData)?.data
            if (lynxchanCaptchaFull == null) {
              return@Try VerifyCaptchaResult.Failure
            }

            val urlExample = "${lynxchanSite.currentDomainString}/addon.js/hashcash?action=save" +
              "&b=XXXXXXXXXXXXXXXXXXXXXXXX&h=YYYYYYYYYYYYYYYYYYYYYYYY&e=ZZZ"

            _hashCashInfoToShow.value = when (lynxchanSite) {
              is Krautchan -> {
                HashCashInfo.Krautchan(
                  descriptionText = appResources.string(stringId = R.string.krautchan_hashcash_description),
                  urlExample = urlExample,
                  urlToOpen = "${lynxchanSite.currentDomainString}/addon.js/hashcash/?action=get"
                )
              }
              else -> {
                HashCashInfo.GenericLynxchan(
                  descriptionText = appResources.string(R.string.lynxchan_hashcash_description),
                  urlExample = urlExample
                )
              }
            }

            return@Try VerifyCaptchaResult.NotSupported
          }

          if (blockBypassStatus.isError) {
            val errorMessage = blockBypassStatus.data
            throw LynxchanCaptchaError("Error. Message: \'$errorMessage\'")
          }

          if (needBlockBypass && blockBypassStatus.data == null && lynxchanSite is Chan8Moe) {
            val bypass = response.headers("Set-Cookie")
              .firstOrNull { setCookie -> setCookie.startsWith("bypass=") }
              ?.let { bypassCookie -> KurobaCookie.fromRawCookie(bypassCookie, "bypass")?.value }

            if (bypass != null && bypass.isBypassCookieValidForPOW()) {
              // Need to solve Proof Of Work
              Logger.debug(TAG) { "verifyCaptcha(needBlockBypass: ${needBlockBypass}) need to solve POW" }

              findProofOfWorkAndSubmit(
                captchaId = captchaId,
                bypass = bypass,
                chanDescriptor = chanDescriptor,
                lynxchanCaptcha = lynxchanCaptcha
              ).unwrap()

              return@Try VerifyCaptchaResult.SolvedProofOfWork
            }

            throw LynxchanCaptchaError("bypassCookie is too short '${bypass.asFormattedToken()}'")
          }

          // fallthrough
        }

        if (responseString.contains(CAPTCHA_SOLVED_MSG, ignoreCase = true)) {
          extractAndStoreBypassCookie(chanDescriptor, response.headers)
          return@Try VerifyCaptchaResult.SolvedCaptcha
        }

        Logger.error(TAG) { "Failed to verify captcha. response: '${responseString}'" }
        throw LynxchanCaptchaError("Failed to verify captcha. See logs for more info.")
      }
    }.onError { error ->
      Logger.error(TAG, error) { "Failed to verify captcha" }
    }
  }

  fun validateHashCashUrl(text: CharSequence): String? {
    // https://kohlchan.net/addon.js/hashcash?action=save&b=123&h=546&e=100
    // https://krautchan.org/addon.js/hashcash?action=save&b=123&h=456&e=100
    val url = text.toString().toHttpUrlOrNull()
    if (url == null) {
      return "Not a HTTP url"
    }

    if (url.queryParameter("b").isNullOrBlank()) {
      return "Missing 'b' parameter"
    }

    if (url.queryParameter("h").isNullOrBlank()) {
      return "Missing 'h' parameter"
    }

    if (url.queryParameter("e").isNullOrBlank()) {
      return "Missing 'e' parameter"
    }

    return null
  }

  suspend fun applyHashCashCookiesByUrl(
    chanDescriptor: ChanDescriptor,
    url: HttpUrl
  ): ModularResult<KurobaCookie?> {
    return ModularResult.Try {
      val lynxchanSite = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())
        ?: throw LynxchanCaptchaError("Site ${chanDescriptor.siteDescriptor()} does not exist or not active")

      lynxchanSite as BaseLynxchanSite

      val requestBuilder = Request.Builder()
        .url(url)
        .get()

      lynxchanSite.requestModifier
        .modifyGenericRequest(lynxchanSite, requestBuilder)

      val response = proxiedOkHttpClient.okHttpClient()
        .suspendCall(requestBuilder.build())

      if (!response.isSuccessful) {
        throw LynxchanCaptchaError("Response is not successful: ${response.code}")
      }

      val cookies = response.headers("Set-Cookie")

      val bypassCookie = cookies
        .firstOrNull { cookie -> cookie.startsWith("bypass=") }
        ?.let { bypass -> KurobaCookie.fromRawCookie(bypass, "bypass") }
      val extraCookie = cookies
        .firstOrNull { cookie -> cookie.startsWith("extraCookie=") }
        ?.let { extraCookie -> KurobaCookie.fromRawCookie(extraCookie, "extraCookie") }

      if (bypassCookie == null) {
        Logger.debug(TAG) { "All cookies: ${cookies.joinToString(separator = "; ")}" }
        throw LynxchanCaptchaError("Failed to parse bypass cookie")
      }

      if (extraCookie == null) {
        Logger.debug(TAG) { "All cookies: ${cookies.joinToString(separator = "; ")}" }
        throw LynxchanCaptchaError("Failed to parse bypass extraCookie cookie")
      }

      lynxchanSite.settings.bypassCookie.setSync(bypassCookie)
      lynxchanSite.settings.extraCookie.setSync(extraCookie)

      Logger.debug(TAG) { "Successfully received HashCash challenge cookies (site: ${lynxchanSite.descriptor})!" }

      val captchaIdCookie = lynxchanSite.settings.captchaIdCookie.get()
      if (captchaIdCookie == null) {
        return@Try null
      }

      return@Try captchaIdCookie
    }
  }

  private suspend fun needBlockBypass(
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha,
    chanDescriptor: ChanDescriptor
  ): Boolean {
    try {
      val requestBuilder = Request.Builder()
        .url(lynxchanCaptcha.bypassEndpoint)
        .get()

      val site = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())
        ?: throw LynxchanCaptchaError("Site ${chanDescriptor.siteDescriptor()} does not exist or not active")

      site.requestModifier.modifyGenericRequest(
        site = site,
        requestBuilder = requestBuilder
      )

      val response = proxiedOkHttpClient.okHttpClient().suspendCall(request = requestBuilder.build())
      if (!response.isSuccessful) {
        return false
      }

      // Endchan doesn't support this thing with json mode
      if (chanDescriptor.siteDescriptor().isEndchan() && !response.isContentTypeApplicationJson()) {
        return false
      }

      val body = response.body.string()

      val blockBypassWithStatusJson = moshi
        .adapter(BlockBypassWithStatusJson::class.java)
        .fromJson(body)
        ?: return false

      if (blockBypassWithStatusJson.status != "ok") {
        return false
      }

      return !blockBypassWithStatusJson.data.valid
    } catch (error: Throwable) {
      Logger.e(TAG, "needBlockBypass() error", error)
      return false
    }
  }

  private suspend fun requestCaptchaInternal(
    chanDescriptor: ChanDescriptor,
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha,
    needBlockBypass: Boolean
  ): LynxchanCaptchaFull {
    var captchaEndpoint = lynxchanCaptcha.captchaEndpoint
    if (chanDescriptor.siteDescriptor().is8chanMoe()) {
      captchaEndpoint = captchaEndpoint.newBuilder()
        .addEncodedQueryParameter("d", Chan8MoeCaptchaTimeFormatter.print(DateTime.now()))
        .build()
    }

    val requestBuilder = Request.Builder()
      .url(captchaEndpoint)
      .get()

    val site = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())
      ?: throw LynxchanCaptchaError("Site ${chanDescriptor.siteDescriptor()} does not exist or not active")

    site.requestModifier.modifyGenericRequest(
      site = site,
      requestBuilder = requestBuilder
    )

    val refererUrl = site.urlHandler.desktopUrl(chanDescriptor, null, null)
    if (refererUrl.isNotNullNorBlank()) {
      requestBuilder.header("Referer", refererUrl)
    }

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request = requestBuilder.build())
    if (!response.isSuccessful) {
      throw BadStatusResponseException(status = response.code)
    }

    val lynxchanCaptchaInfo = extractLynxchanCaptchaInfo(response)

    val imgByteArray = response.body.bytes()
    val imgImageBitmap = BitmapPainter(
      BitmapFactory.decodeByteArray(imgByteArray, 0, imgByteArray.size)
        .asImageBitmap()
    )

    return LynxchanCaptchaFull(
      needBlockBypass = needBlockBypass,
      captchaInfo = lynxchanCaptchaInfo,
      captchaImage = imgImageBitmap,
    )
  }

  private fun extractLynxchanCaptchaInfo(response: Response): LynxchanCaptchaFull.CaptchaInfo {
    val captchaData = run {
      val captchaData = mutableListOf<String>()
      var currentResponse: Response? = response

      while (currentResponse != null) {
        val setCookieHeader = currentResponse.headers
          .filter { (name, _) -> name.equals("set-cookie", ignoreCase = true) }
          .map { (_, value) -> value }

        if (setCookieHeader.isNotEmpty()) {
          captchaData.addAll(setCookieHeader)
          break
        }

        currentResponse = currentResponse.priorResponse
      }

      return@run captchaData
    }

    Logger.d(TAG, "extractLynxchanCaptchaInfo() captchaData=$captchaData")

    val captchaIdCookieRaw = captchaData
      .firstOrNull { captchaCookie -> captchaCookie.startsWith("captchaid=", ignoreCase = true) }

    if (captchaIdCookieRaw == null || captchaIdCookieRaw.isEmpty()) {
        throw LynxchanCaptchaError("captchaIdCookieRaw is null or empty (captchaData: '${captchaData}')")
    }

    val kurobaCookie = KurobaCookie.fromRawCookie(captchaIdCookieRaw, "captchaid")
      ?: throw LynxchanCaptchaError("Failed to create KurobaCookie from '${captchaIdCookieRaw}'")

    return LynxchanCaptchaFull.CaptchaInfo(
      captchaId = kurobaCookie.value,
      kurobaCookie = kurobaCookie
    )
  }

  private suspend fun findProofOfWorkAndSubmit(
    captchaId: String,
    bypass: String,
    chanDescriptor: ChanDescriptor,
    lynxchanCaptcha: SiteAuthentication.CustomCaptcha.LynxchanCaptcha
  ): ModularResult<Unit> {
    return ModularResult.Try {
      val pow = coroutineScope {
        coroutineContext[Job.Key]
          ?.invokeOnCompletion { _captchaBlock.value = null }

        val solution = CompletableDeferred<Int>()

        launch {
          try {
            LynxchanProofOfWork(bypass)
              .find()
              .onEach { event ->
                when (event) {
                  is LynxchanProofOfWork.Event.Update -> {
                    _captchaBlock.value = LynxchanCaptchaBlock(event.iteration)
                  }
                  is LynxchanProofOfWork.Event.Solution -> {
                    solution.complete(event.value)
                    _captchaBlock.value = null
                  }
                }
              }
              .collect()
          } catch (error: Throwable) {
            Logger.error(TAG, error) { "Unknown error while trying to find the POW solution" }
            solution.complete(-1)

            throw error
          }
        }

        return@coroutineScope solution.awaitSilently(-1)
      }

      if (pow == -1) {
        throw FailedToDoPOW("Failed to find the POW solution")
      }

      val validateBypassEndpoint = lynxchanCaptcha.validateBypassEndpoint

      val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("code", pow.toString())
        .build()

      val requestBuilder = Request.Builder()
        .url(validateBypassEndpoint)
        .post(requestBody)

      val site = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())
      if (site != null) {
        site.requestModifier.modifyGenericRequest(
          site = site,
          requestBuilder = requestBuilder
        )
      }

      requestBuilder
        .addOrReplaceCookieHeader("captchaid=${captchaId}")
        .addOrReplaceCookieHeader("bypass=${bypass}")

      val response = proxiedOkHttpClient.okHttpClient().suspendCall(request = requestBuilder.build())
      if (!response.isSuccessful) {
        throw BadStatusResponseException(status = response.code)
      }

      val responseString = response.body.string()

      val blockBypassStatus = moshi
        .adapter(BlockBypassStatus::class.java)
        .fromJson(responseString)

      if (blockBypassStatus == null || !blockBypassStatus.isOk || blockBypassStatus.data != null) {
        throw FailedToDoPOW("Failed to submit POW solution. Response data: '${blockBypassStatus?.data}'")
      }

      extractAndStoreBypassCookie(chanDescriptor, response.headers)
      Logger.debug(TAG) { "Successfully submitted POW and got bypass cookie" }
    }
  }

  private fun extractAndStoreBypassCookie(
    chanDescriptor: ChanDescriptor,
    headers: Headers
  ) {
    val setCookieHeader = headers
      .filter { (name, _) -> name.equals("set-cookie", ignoreCase = true) }
      .map { (_, value) -> value }

    val bypassCookie = setCookieHeader
      .firstOrNull { captchaCookie -> captchaCookie.startsWith("bypass=", ignoreCase = true) }

    if (bypassCookie.isNullOrEmpty()) {
      Logger.debug(TAG) { "extractAndStoreBypassCookie() bypass not found" }
      return
    }

    val bypassKurobaCookie = KurobaCookie.fromRawCookie(bypassCookie, "bypass")
    if (bypassKurobaCookie == null) {
      Logger.debug(TAG) { "extractAndStoreBypassCookie() failed to create KurobaCookie from '${bypassCookie}'" }
      return
    }

    val site = siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())
      ?: return

    if (site !is BaseLynxchanSite) {
      return
    }

    site.settings.bypassCookie.set(bypassKurobaCookie)
  }

  class LynxchanCaptchaError(message: String) : ClientException(message)
  class LynxchanCaptchaPOWError : ClientException("Proof-of-work required to post")
  class FailedToDoPOW(message: String) : ClientException("Proof-of-work failed: $message")

  private fun String.isBypassCookieValidForPOW(): Boolean {
    val str = this
    // https://gitgud.io/LynxChan/PoWSolver/-/blob/master/src/PowSolver.java?ref_type=heads#L16
    return str.length >= 712
  }

  sealed interface VerifyCaptchaResult {
    data object SolvedCaptcha : VerifyCaptchaResult
    data object SolvedProofOfWork : VerifyCaptchaResult
    data object NotSupported : VerifyCaptchaResult
    data object Failure : VerifyCaptchaResult
  }

  data class LynxchanCaptchaFull(
    val needBlockBypass: Boolean,
    val captchaInfo: CaptchaInfo,
    val captchaImage: BitmapPainter,
  ) {
    data class CaptchaInfo(
      val captchaId: String,
      val kurobaCookie: KurobaCookie,
    )
  }

  data class LynxchanCaptchaBlock(
    val iteration: Int
  )

  sealed interface HashCashInfo {
    val descriptionText: String
    val urlExample: String

    data class Krautchan(
      override val descriptionText: String,
      override val urlExample: String,
      val urlToOpen: String,
    ) : HashCashInfo

    data class GenericLynxchan(
      override val descriptionText: String,
      override val urlExample: String
    ) : HashCashInfo
  }

  // {"status":"hashcash","data":null}
  @JsonClass(generateAdapter = true)
  data class BlockBypassStatus(
    @field:Json(name = "status") val status: String,
    @field:Json(name = "data") val data: String?
  ) {
    val isOk: Boolean = status.equals("ok", ignoreCase = true)
    val isHashcash: Boolean = status.equals("hashcash", ignoreCase = true)
    val isError: Boolean = status.equals("error", ignoreCase = true)
  }

  // {"status":"ok","data":{"valid":false,"mode":1}}
  @JsonClass(generateAdapter = true)
  data class BlockBypassWithStatusJson(
    @field:Json(name = "status") val status: String,
    @field:Json(name = "data") val data: BlockBypassJson
  )

  @JsonClass(generateAdapter = true)
  data class BlockBypassJson(
    @field:Json(name = "valid") val valid: Boolean,
    @field:Json(name = "validated") val validated: Boolean?,
    @field:Json(name = "mode") val mode: Int
  )

  class ViewModelFactory @Inject constructor(
    private val appResources: AppResources,
    private val proxiedOkHttpClient: ProxiedOkHttpClient,
    private val siteManager: SiteManager,
    private val moshi: Moshi,
  ) : ViewModelAssistedFactory<LynxchanCaptchaLayoutViewModel> {
    override fun create(handle: SavedStateHandle): LynxchanCaptchaLayoutViewModel {
      return LynxchanCaptchaLayoutViewModel(
        savedStateHandle = handle,
        appResources = appResources,
        proxiedOkHttpClient = proxiedOkHttpClient,
        siteManager = siteManager,
        moshi = moshi
      )
    }
  }

  companion object {
    private const val TAG = "LynxchanCaptchaLayoutViewModel"
    private const val CAPTCHA_SOLVED_MSG = "<title>Captcha solved.</title>"

    private val Chan8MoeCaptchaTimeFormatter =
      DateTimeFormat.forPattern("EEE MMM dd yyyy HH:mm:ss 'GMT' ZZ '(Indochina Time)'")
  }

}