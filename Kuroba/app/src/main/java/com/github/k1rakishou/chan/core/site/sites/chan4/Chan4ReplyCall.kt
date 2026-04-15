package com.github.k1rakishou.chan.core.site.sites.chan4

import android.text.SpannableStringBuilder
import android.text.TextUtils
import androidx.core.text.set
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.repository.BoardFlagInfoRepository
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.common.CommonReplyHttpCall
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.features.posting.LastReplyRepository
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.features.reply.data.ReplyFileMeta
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.chan.utils.WebViewLink
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils.formatToken
import com.github.k1rakishou.common.domainOrHost
import com.github.k1rakishou.common.fixUrlOrNull
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.network.ProgressRequestBody
import com.github.k1rakishou.common.network.ProgressRequestBody.ProgressRequestListener
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.ReplyMode
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.IOException
import java.util.Objects
import java.util.regex.Matcher
import java.util.regex.Pattern

class Chan4ReplyCall(
  site: Site,
  replyChanDescriptor: ChanDescriptor,
  val replyMode: ReplyMode,
) : CommonReplyHttpCall(site, replyChanDescriptor) {
  private val replyManager: ReplyManager
    get() = site.dependencies.replyManager
  private val boardFlagInfoRepository: BoardFlagInfoRepository
    get() = site.dependencies.boardFlagInfoRepository
  private val appConstants: AppConstants
    get() = site.dependencies.appConstants
  private val kurobaSettings: KurobaSettings
    get() = site.dependencies.kurobaSettings
  private val chan4SiteSettings: Chan4SiteSettings
    get() = site.requireSiteSettings(Chan4SiteSettings::class.java)

  @get:Synchronized
  @set:Synchronized
  private var captchaSolution: CaptchaSolution? = null

  override suspend fun addParameters(
    builder: MultipartBody.Builder,
    progressListener: ProgressRequestListener?
  ) {
    val chanDescriptor = Objects.requireNonNull(
      replyChanDescriptor,
      "replyChanDescriptor == null"
    )

    if (!replyManager.containsReply(chanDescriptor)) {
      throw IOException("No reply found for chanDescriptor=$chanDescriptor")
    }

    replyManager.readReply(chanDescriptor) { reply ->
      builder.addFormDataPart("mode", "regist")
      builder.addFormDataPart("pwd", replyResponse.password)

      if (chanDescriptor is ThreadDescriptor) {
        val threadNo = chanDescriptor.threadNo
        builder.addFormDataPart("resto", threadNo.toString())
      }

      builder.addFormDataPart("name", reply.postName)
      builder.addFormDataPart("email", reply.options)

      if (chanDescriptor is CatalogDescriptor
        && !TextUtils.isEmpty(reply.subject)) {
        builder.addFormDataPart("sub", reply.subject)
      }

      builder.addFormDataPart("com", reply.comment)

      if (reply.captchaSolution != null) {
        when (val captchaSolution = reply.captchaSolution!!) {
          is CaptchaSolution.SimpleTokenSolution -> {
            if (reply.captchaChallenge != null) {
              builder.addFormDataPart("recaptcha_challenge_field", reply.captchaChallenge!!)
              builder.addFormDataPart("recaptcha_response_field", captchaSolution.token)
            } else {
              builder.addFormDataPart("g-recaptcha-response", captchaSolution.token)
            }
          }
          is CaptchaSolution.ChallengeWithSolution -> {
            builder.addFormDataPart("t-challenge", captchaSolution.challenge)
            builder.addFormDataPart("t-response", captchaSolution.solution)
          }
        }

        this.captchaSolution = reply.captchaSolution
      }

      if (site is Chan4) {
        if (reply.flag.isNotEmpty()) {
          builder.addFormDataPart("flag", reply.flag)
        } else {
          val lastUsedFlag = boardFlagInfoRepository
            .getLastUsedFlagKey(replyChanDescriptor.boardDescriptor())

          if (lastUsedFlag.isNotNullNorEmpty()) {
            builder.addFormDataPart("flag", lastUsedFlag)
          }
        }
      }

      val replyFile = reply.firstFileOrNull()
      if (replyFile != null) {
        val replyFileMetaResult = replyFile.getReplyFileMeta()
        if (replyFileMetaResult is ModularResult.Error<*>) {
          throw IOException((replyFileMetaResult as ModularResult.Error<ReplyFileMeta>).error)
        }

        val replyFileMetaInfo = (replyFileMetaResult as ModularResult.Value).value
        attachFile(builder, progressListener, replyFile, replyFileMetaInfo)

        if (replyFileMetaInfo.spoiler) {
          builder.addFormDataPart("spoiler", "on")
        }
      }
    }
  }

  override suspend fun addHeaders(requestBuilder: Request.Builder, boundary: String) {
    arrayOf("Referer", "User-Agent", "Accept-Encoding", "Cookie",
      "Content-Type", "Content-Length", "Host", "Connection")
      .forEach { header -> requestBuilder.removeHeader(header) }

    val replyUrl = requireNotNull(site.endpoints.reply(replyChanDescriptor))

    requestBuilder.addHeader("Host", "sys.4chan.org")
    requestBuilder.addHeader("User-Agent", appConstants.userAgentMightBeOverridden)
    requestBuilder.addHeader("Accept-Encoding", "gzip")
    requestBuilder.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif," +
      "image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
    requestBuilder.addHeader("Accept-Language", "en-US,en;q=0.5")
    requestBuilder.addHeader("Content-Type", "multipart/form-data; boundary=${boundary}")
    requestBuilder.addHeader("Origin", "https://boards.4chan.org")
    requestBuilder.addHeader("Connection", "Keep-Alive")
    requestBuilder.addHeader("Referer", replyUrl.toString())
    requestBuilder.addHeader("Cookie", readCookies(replyUrl))
    requestBuilder.addHeader("Sec-Fetch-Dest", "document")
    requestBuilder.addHeader("Sec-Fetch-Mode", "navigate")
    requestBuilder.addHeader("Sec-Fetch-Site", "none")
    requestBuilder.addHeader("Sec-Fetch-User", "?1")

    site.requestModifier.modifyHttpCall(this, requestBuilder)
  }

  override suspend fun process(response: Response, result: String) {
    setChan4CaptchaHeader(response.headers)

    if (kurobaSettings.application.verboseLogs.readBlocking()) {
      Logger.d(TAG, "process() result:")

      result
        .filter { char -> char != '\n' }
        .chunked(256)
        .forEach { chunk ->
          Logger.d(TAG, chunk)
        }
    }

    val forgotCaptcha = result.contains(FORGOT_TO_SOLVE_CAPTCHA, ignoreCase = true)
    val mistypedCaptcha = result.contains(MISTYPED_CAPTCHA, ignoreCase = true)

    if (forgotCaptcha || mistypedCaptcha) {
      replyResponse.requireAuthentication = true
      Logger.e(TAG, "process() requireAuthentication (forgotCaptcha: ${forgotCaptcha}, mistypedCaptcha: ${mistypedCaptcha})")
      return
    }

    val errorMessageHtml = Jsoup.parse(result).selectFirst("span[id=errmsg]")
    if (errorMessageHtml != null) {
      val errorMessage = parseErrorMessageHtml(errorMessageHtml)
      replyResponse.errorMessage = errorMessage
      replyResponse.banInfo = checkIfBanned()

      Logger.e(TAG, "process() error (errorMessage: '${errorMessage}')")

      if (replyChanDescriptor is ThreadDescriptor) {
        // Only check for rate limits when replying in threads. Do not do this when creating new
        // threads.
        val rateLimitMatcher = RATE_LIMITED_PATTERN.matcher(errorMessage)
        if (rateLimitMatcher.find()) {
          val rateLimitInfo = createRateLimitInfo(rateLimitMatcher)
          replyResponse.rateLimitInfo = rateLimitInfo
          return
        }
      }

      return
    }

    if (!response.isSuccessful) {
      Logger.e(TAG, "process() Bad status code! code: '${response.code}'")
      replyResponse.errorMessage = "Bad response status code: ${response.code}"
      return
    }

    val threadNoMatcher = THREAD_NO_PATTERN.matcher(result)
    if (!threadNoMatcher.find()) {
      Logger.e(TAG, "process() Couldn't handle server response! response: '$result'")
      replyResponse.errorMessage = "Error trying to parse server response"
      return
    }

    val threadNoString = threadNoMatcher.groupOrNull(1) ?: ""
    val threadNo = threadNoString.toIntOrNull()?.toLong()
    if (threadNo == null) {
      Logger.e(TAG, "process() Failed to convert threadNo from string to int (threadNoString: \'${threadNoString}\')")
      replyResponse.errorMessage = "Failed to convert threadNo from string to int (threadNoString: '${threadNoString}')"
      return
    }

    val postNoString = threadNoMatcher.groupOrNull(2) ?: ""
    val postNo = postNoString.toIntOrNull()?.toLong()
    if (postNo == null) {
      Logger.e(TAG, "process() Failed to convert postNo from string to int (postNoString: \'${postNoString}\')")
      replyResponse.errorMessage = "Failed to convert postNo from string to int (postNoString: '${postNoString}')"
      return
    }

    replyResponse.threadNo = threadNo
    replyResponse.postNo = postNo

    if (replyResponse.threadNo == 0L) {
      replyResponse.threadNo = replyResponse.postNo
    }

    // Some boards of 4chan return postNo == 0 when creating threads (threadNo is not zero so we can
    if (replyChanDescriptor is CatalogDescriptor && replyResponse.postNo == 0L) {
      replyResponse.postNo = replyResponse.threadNo
    }

    if (replyResponse.threadNo == 0L || replyResponse.postNo == 0L) {
      Logger.e(TAG, "process() Server returned incorrect thread/post id! response = \"$result\"")
      replyResponse.errorMessage = "Server returned incorrect thread/post id. " +
              "On some boards this happens when you are trying to make a post with an image and the server thinks you are a spammer.\n" +
              "Try to make some regular posts without images.\n\n" +
              "threadNo: ${replyResponse.threadNo}, postNo: ${replyResponse.postNo}"
      return
    }

    if (replyResponse.threadNo > 0 && replyResponse.postNo > 0) {
      replyResponse.posted = true
      replyResponse.captchaSolution = this.captchaSolution
      return
    }

    Logger.e(TAG, "process() Failed to handle server response. response: \"$result\"")
    replyResponse.errorMessage = "Failed to handle server response. Report this with logs attached!"
  }

  private fun parseErrorMessageHtml(errorMessageHtml: Element): CharSequence {
    val builder = SpannableStringBuilder()
    parseErrorMessageHtmlInternal(builder, errorMessageHtml)
    return builder
  }

  private fun parseErrorMessageHtmlInternal(
    builder: SpannableStringBuilder,
    currentNode: Node
  ) {
    for (node in currentNode.childNodes()) {
      if (node is TextNode) {
        builder.append(node.text())
        continue
      }

      if (node is Element) {
        val tagName = node.tagName().lowercase()
        if (tagName == "a") {
          val start = builder.length
          parseErrorMessageHtmlInternal(builder, node)
          val end = builder.length

          val link = fixUrlOrNull(node.attr("href").takeIf { it.isNotBlank() })
          if (end > start && link.isNotNullNorBlank()) {
            builder.set(start, end, WebViewLink(WebViewLink.Type.BanMessage, link))
          }
        } else if (tagName == "br") {
          builder.append("\n")
        }
      }
    }
  }

  private suspend fun setChan4CaptchaHeader(headers: Headers) {
    val chan4CaptchaSettings = chan4SiteSettings.captchaSettings.read()
    if (!chan4CaptchaSettings.rememberCaptchaCookies) {
      Logger.d(TAG, "setChan4CaptchaHeader() rememberCaptchaCookies is false")
      return
    }

    val wholeCookieHeader = headers
      .filter { (key, _) -> key.contains(SET_COOKIE_HEADER, ignoreCase = true) }
      .firstOrNull { (_, value) -> value.startsWith(CAPTCHA_COOKIE_PREFIX) }
      ?.second

    val newCookie = wholeCookieHeader
      ?.substringAfter(CAPTCHA_COOKIE_PREFIX)
      ?.substringBefore(';')

    val headersDebugString = headers.joinToString(separator = ";") { (key, value) -> "${key}=${value}" }

    Logger.d(TAG, "setChan4CaptchaHeader() " +
              "newCookie='${newCookie}', " +
              "wholeCookieHeader='${wholeCookieHeader}', " +
              "headersDebugString='${headersDebugString}'")

    val oldCookie = chan4SiteSettings.captchaCookie.read()
    Logger.d(TAG, "oldCookie='${formatToken(oldCookie)}', newCookie='${formatToken(newCookie)}'")

    if (newCookie.isNullOrEmpty()) {
      Logger.d(TAG, "setChan4CaptchaHeader() failed to parse 4chan_pass cookie (${formatToken(newCookie)})")
      return
    }

    chan4SiteSettings.captchaCookie.write(newCookie)
  }

  private fun createRateLimitInfo(rateLimitMatcher: Matcher): ReplyResponse.RateLimitInfo {
    val minutes = (rateLimitMatcher.groupOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 60)
    val seconds = (rateLimitMatcher.groupOrNull(2)?.toIntOrNull() ?: 0).coerceIn(0, 60)
    val currentPostingCooldownMs = ((minutes * 60) + seconds) * 1000L

    val cooldownInfo = LastReplyRepository.CooldownInfo(
      replyChanDescriptor.boardDescriptor(),
      currentPostingCooldownMs
    )

    return ReplyResponse.RateLimitInfo(currentPostingCooldownMs, cooldownInfo)
  }

  private fun checkIfBanned(): ReplyResponse.BanInfo? {
    val errorMessage = replyResponse.errorMessage
      ?: return null

    val isBannedFound = errorMessage.contains(PROBABLY_BANNED_TEXT)
    if (isBannedFound) {
      return ReplyResponse.BanInfo.Banned
    }

    val isWarnedFound = errorMessage.contains(PROBABLY_WARNED_TEXT)
    if (isWarnedFound) {
      return ReplyResponse.BanInfo.Warned
    }

    if (!replyChanDescriptor.siteDescriptor().is4chan()) {
      return null
    }

    if (errorMessage.contains(PROBABLY_IP_RANGE_BLOCKED)) {
      return ReplyResponse.BanInfo.Banned
    }

    return null
  }

  private fun attachFile(
    formBuilder: MultipartBody.Builder,
    progressListener: ProgressRequestListener?,
    replyFile: ReplyFile,
    replyFileMeta: ReplyFileMeta
  ) {
    val mediaType = "application/octet-stream".toMediaType()
    val fileOnDisk = replyFile.fileOnDisk

    val requestBody = if (progressListener == null) {
      replyFile.fileOnDisk.asRequestBody(mediaType)
    } else {
      ProgressRequestBody(
        fileOnDisk.asRequestBody(mediaType),
        progressListener
      )
    }

    formBuilder.addFormDataPart("upfile", replyFileMeta.fileName, requestBody)
  }

  private suspend fun readCookies(requestUrl: HttpUrl): String {
    val domainOrHost = requestUrl.domainOrHost()
    val cloudflareCookie = site.commonSettings.cloudFlareClearanceCookieMap.read().get(domainOrHost)

    return buildString {
      if (cloudflareCookie.isNotNullNorEmpty()) {
        Logger.d(TAG, "readCookies() domainOrHost: ${domainOrHost}, cf_clearance: ${formatToken(cloudflareCookie)}")
        append(cloudflareCookie)
      }

      val chan4CaptchaSettings = chan4SiteSettings.captchaSettings.read()
      val rememberCaptchaCookies = chan4CaptchaSettings.rememberCaptchaCookies

      if (rememberCaptchaCookies) {
        val captchaCookie = chan4SiteSettings.captchaCookie.read()
        if (captchaCookie.isNotBlank()) {
          Logger.d(TAG, "readCookies() domainOrHost: ${domainOrHost}, captchaCookie: ${formatToken(captchaCookie)}")

          if (isNotEmpty()) {
            append("; ")
          }

          append("${Chan4.CAPTCHA_COOKIE_KEY}=${captchaCookie}")
        }
      }
    }
  }

  companion object {
    private const val TAG = "Chan4ReplyCall"

    private const val PROBABLY_BANNED_TEXT = "banned"
    private const val PROBABLY_WARNED_TEXT = "warned"
    private const val PROBABLY_IP_RANGE_BLOCKED = "Posting from your IP range has been blocked due to abuse"
    private const val FORGOT_TO_SOLVE_CAPTCHA = "Error: You forgot to solve the CAPTCHA"
    private const val MISTYPED_CAPTCHA = "Error: You seem to have mistyped the CAPTCHA"

    private const val SET_COOKIE_HEADER = "set-cookie"
    private const val CAPTCHA_COOKIE_PREFIX = "4chan_pass="

    private val THREAD_NO_PATTERN = Pattern.compile("<!-- thread:([0-9]+),no:([0-9]+) -->")

    // Error: You must wait 1 minute 17 seconds before posting a duplicate reply.
    // Error: You must wait 2 minutes 1 second before posting a duplicate reply.
    // Error: You must wait 17 seconds before posting a duplicate reply.
    private val RATE_LIMITED_PATTERN = Pattern.compile("must wait (?:(\\d+)\\s+minutes?)?.*?(?:(\\d+)\\s+seconds?)")
  }
}