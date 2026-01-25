package com.github.k1rakishou.chan.ui.captcha.lynxchan.chan8moe

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.preprocessor.SitePreprocessing
import com.github.k1rakishou.chan.core.site.preprocessor.SitePreprocessingEventQueue
import com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8.Chan8Moe
import com.github.k1rakishou.chan.ui.captcha.lynxchan.pow.Chan8MoeProofOfWork
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.common.StringUtils.asFormattedToken
import com.github.k1rakishou.common.addOrReplaceCookieHeader
import com.github.k1rakishou.common.isContentTypeTextHtml
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.common.tryExtractMediaType
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.atomic.AtomicBoolean

class Chan8MoePreprocessor(
  private val okHttpClient: ProxiedOkHttpClient,
  override val sitePreprocessingEventQueue: SitePreprocessingEventQueue
) : SitePreprocessing.Preprocessor {
  private val _alreadyChecked = AtomicBoolean(false)
  private val _noRedirectHttpClient = okHttpClient.okHttpClient()
    .newBuilder()
    .followRedirects(false)
    .build()

  override val siteDescriptor: SiteDescriptor
    get() = Chan8Moe.DESCRIPTOR

  override suspend fun <T : Site> preprocess(site: T) {
    val chan8Moe = site as Chan8Moe

    coroutineScope {
      try {
        processSiteProofOfWork(chan8Moe)
      } catch (error: Throwable) {
        Logger.error(TAG, error) {
          "processSiteProofOfWork(${chan8Moe.siteDescriptor()}) error"
        }

        return@coroutineScope
      }
    }
  }

  override fun <T : Site> stop(site: T) {
    // TODO: cancel this somehow
  }

  private suspend fun processSiteProofOfWork(chan8Moe: Chan8Moe) {
    if (!_alreadyChecked.compareAndSet(false, true)) {
      Logger.debug(TAG) { "processSiteProofOfWork() already checked" }
      return
    }

    val initialResponse = run {
      // Initial request to check whether or not we need to bypass the POW block
      val initialRequest = Request.Builder()
        .url("${chan8Moe.domainString}/".toHttpUrl())
        .get()

      chan8Moe.requestModifier()
        .modifyCaptchaGetRequest(chan8Moe, initialRequest)

      val response = _noRedirectHttpClient.suspendCall(initialRequest.build())
      if (!response.isSuccessful) {
        val message = response.body.string()
        throw SitePreprocessing.Preprocessor.Exception(
          siteDescriptor = siteDescriptor,
          message = "Bad response: ${response.code}, message: '${message}'"
        )
      }

      when (val powBlockStatus = response.header("X-PoWBlock-Status")?.lowercase()) {
        null,
        PowBlockStatusCompleted -> {
          Logger.debug(TAG) { "POW bypass is not required for ${siteDescriptor}, exiting" }
          return
        }
        PowBlockStatusRequired -> {
          Logger.debug(TAG) { "POW bypass is required for ${siteDescriptor}" }
        }
        else -> {
          throw SitePreprocessing.Preprocessor.Exception(
            siteDescriptor = siteDescriptor,
            message = "Unknown 'X-PoWBlock-Status' value: '${powBlockStatus}'"
          )
        }
      }

      if (!response.isContentTypeTextHtml()) {
        throw SitePreprocessing.Preprocessor.Exception(
          siteDescriptor = siteDescriptor,
          message = "Expected text/html media type but got '${response.tryExtractMediaType()}'"
        )
      }

      return@run response
    }

    chan8Moe.powToken.set(null)
    chan8Moe.powId.set(null)

    val (solution, token) = run {
      val htmlMaybe = initialResponse.body.string()
      val doc = Jsoup.parse(htmlMaybe)

      val tokenElement = doc.select("pre#c").first()
      val difficultyElement = doc.select("pre#d").first()

      val token = tokenElement?.text()
        ?.takeIf { token -> token.isNotBlank() }
        ?: throw SitePreprocessing.Preprocessor.Exception(siteDescriptor, "Token not found")
      val difficulty = difficultyElement
        ?.text()
        ?.toIntOrNull()
        ?.takeIf { difficulty -> difficulty >= 0 }
        ?: throw SitePreprocessing.Preprocessor.Exception(siteDescriptor, "Difficulty not found")

      val solution = Chan8MoeProofOfWork(
        token = token,
        difficulty = difficulty
      ).find()

      if (solution == null) {
        throw SitePreprocessing.Preprocessor.Exception(siteDescriptor, "Failed to find POW solution")
      }

      Logger.debug(TAG) { "Found solution: ${solution} for token: ${token.asFormattedToken()}" }

      return@run solution to token
    }

    // First solution submission request. This will return us POW_TOKEN and POW_ID cookies which we need to apply
    // for the second submission request. It will also return a 'X-PoWBlock-Status' with the solution status.
    val firstSubmitSolutionRequest = Request.Builder()
      .url("${chan8Moe.domainString}/?pow=${solution}&t=${token}")
      .header("Referer", chan8Moe.domainString)
      .get()

    chan8Moe.requestModifier()
      .modifyCaptchaGetRequest(chan8Moe, firstSubmitSolutionRequest)

    val firstSubmitSolutionResponse = _noRedirectHttpClient.suspendCall(firstSubmitSolutionRequest.build())
    if (!firstSubmitSolutionResponse.isRedirect) {
      val message = firstSubmitSolutionResponse.body.string()
      throw SitePreprocessing.Preprocessor.Exception(
        siteDescriptor = siteDescriptor,
        message = "Bad response: ${firstSubmitSolutionResponse.code}, message: '${message}'"
      )
    }

    val powBlockStatus = firstSubmitSolutionResponse.header("X-PoWBlock-Status")
    if (powBlockStatus?.equals(PowBlockStatusCompleted, ignoreCase = true) != true) {
      // TODO: consider showing the whole thing again
      throw SitePreprocessing.Preprocessor.Exception(
        siteDescriptor = siteDescriptor,
        message = "Didn't manage to complete POW block, got status: '${powBlockStatus}'"
      )
    }

    val redirectPath = firstSubmitSolutionResponse.header("Location")
    if (redirectPath.isNullOrBlank()) {
      throw SitePreprocessing.Preprocessor.Exception(
        siteDescriptor = siteDescriptor,
        message = "redirectPath is null or blank"
      )
    }

    val cookies = firstSubmitSolutionResponse.headers("Set-Cookie")

    val powTokenCookie = cookies
      .firstOrNull { cookie -> cookie.startsWith(Chan8Moe.POW_TOKEN) }
      ?.let { rawCookie -> KurobaCookie.fromRawCookie(rawCookie, Chan8Moe.POW_TOKEN) }
    if (powTokenCookie == null) {
      throw SitePreprocessing.Preprocessor.Exception(
        siteDescriptor = siteDescriptor,
        message = "Failed to find/parse powToken cookie (cookies: ${cookies.joinToString(separator = "; ")})"
      )
    }

    val powIdCookie = cookies
      .firstOrNull { cookie -> cookie.startsWith(Chan8Moe.POW_ID) }
      ?.let { rawCookie -> KurobaCookie.fromRawCookie(rawCookie, Chan8Moe.POW_ID) }
    if (powIdCookie == null) {
      throw SitePreprocessing.Preprocessor.Exception(
        siteDescriptor = siteDescriptor,
        message = "Failed to find/parse powIdCookie cookie (cookies: ${cookies.joinToString(separator = "; ")})"
      )
    }

    chan8Moe.powToken.setSync(powTokenCookie)
    chan8Moe.powId.setSync(powIdCookie)

    // Now we follow the redirect with POW_TOKEN and POW_ID cookies applied. This will return us 'inbound' cookie
    // which we need to apply and follow the next redirect which should display 'disclaimer.html' page
    val disclaimerRedirectResult = followRedirect(
      chan8Moe = chan8Moe,
      redirectPath = redirectPath,
      additionalCookiesToSet = emptyList(),
      expectedCookieKeys = listOf("inbound")
    )

    val disclaimerRedirectPath = disclaimerRedirectResult.nextRedirectPath
    val disclaimerCookies = disclaimerRedirectResult.cookies

    if (disclaimerRedirectPath.isNullOrBlank() || !disclaimerRedirectPath.contains("disclaimer.html")) {
      throw SitePreprocessing.Preprocessor.Exception(
        siteDescriptor = siteDescriptor,
        message = "Expected disclaimer redirect but got ${disclaimerRedirectResult}"
      )
    }

    // Now we follow the redirect to 'disclaimer.html' page
    followRedirect(
      chan8Moe = chan8Moe,
      redirectPath = disclaimerRedirectPath,
      additionalCookiesToSet = disclaimerCookies,
      expectedCookieKeys = listOf()
    )

    Logger.debug(TAG) { "Successfully passed 8chan.moe POW block!" }
  }

  private suspend fun followRedirect(
    chan8Moe: Chan8Moe,
    redirectPath: String,
    additionalCookiesToSet: List<KurobaCookie>,
    expectedCookieKeys: List<String>
  ): RedirectResult {
    val actualRedirectPath = redirectPath
      .removePrefix("${chan8Moe.domainString}")
      .removePrefix("/")

    val redirectRequest = Request.Builder()
      .url("${chan8Moe.domainString}/${actualRedirectPath}")
      .header("Referer", chan8Moe.domainString)
      .get()

    chan8Moe.requestModifier()
      .modifyCaptchaGetRequest(chan8Moe, redirectRequest)

    for (kurobaCookie in additionalCookiesToSet) {
      redirectRequest.addOrReplaceCookieHeader(kurobaCookie.value)
    }

    val redirectResponse = _noRedirectHttpClient.suspendCall(redirectRequest.build())
    if (!redirectResponse.isRedirect) {
      throw SitePreprocessing.Preprocessor.Exception(
        siteDescriptor = siteDescriptor,
        message = "Bad response: ${redirectResponse.code} for redirect: '${redirectResponse.request.url}'"
      )
    }

    val setCookies = if (expectedCookieKeys.isNotEmpty()) {
      val setCookies = redirectResponse.headers("Set-Cookie")
        .mapNotNull { rawCookie ->
          for (expectedCookieKey in expectedCookieKeys) {
            if (rawCookie.startsWith(expectedCookieKey, ignoreCase = true)) {
              return@mapNotNull KurobaCookie.fromRawCookie(rawCookie, expectedCookieKey)
            }
          }

          return@mapNotNull null
        }

      if (expectedCookieKeys.size != setCookies.size) {
        throw SitePreprocessing.Preprocessor.Exception(
          siteDescriptor = siteDescriptor,
          message = "Expected cookies: ${expectedCookieKeys}, got cookies: ${setCookies}"
        )
      }

      setCookies
    } else {
      emptyList()
    }

    return RedirectResult(
      cookies = setCookies,
      nextRedirectPath = redirectResponse.header("Location")
    )
  }

  sealed interface Chan8PreprocessorEvent : SitePreprocessingEventQueue.Event {
    override val siteDescriptor: SiteDescriptor
      get() = Chan8Moe.DESCRIPTOR
  }

  private data class RedirectResult(
    val cookies: List<KurobaCookie>,
    val nextRedirectPath: String?
  )

  companion object {
    private const val TAG = "Chan8MoePreprocessor"

    private const val PowBlockStatusRequired = "required"
    private const val PowBlockStatusCompleted = "completed"
  }
}