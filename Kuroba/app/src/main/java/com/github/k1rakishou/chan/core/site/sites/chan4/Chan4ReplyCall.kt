/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.site.sites.chan4

import android.text.TextUtils
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.repository.BoardFlagInfoRepository
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.common.CommonReplyHttpCall
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody.ProgressRequestListener
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.features.posting.LastReplyRepository
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.features.reply.data.ReplyFileMeta
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils.formatToken
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.persist_state.ReplyMode
import dagger.Lazy
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern


class Chan4ReplyCall(
  site: Site,
  replyChanDescriptor: ChanDescriptor,
  val replyMode: ReplyMode,
  private val replyManager: Lazy<ReplyManager>,
  private val boardFlagInfoRepository: Lazy<BoardFlagInfoRepository>
) : CommonReplyHttpCall(site, replyChanDescriptor) {

  @Throws(IOException::class)
  override fun addParameters(
    formBuilder: MultipartBody.Builder,
    progressListener: ProgressRequestListener?
  ) {
    val chanDescriptor = Objects.requireNonNull(
      replyChanDescriptor,
      "replyChanDescriptor == null"
    )

    if (!replyManager.get().containsReply(chanDescriptor)) {
      throw IOException("No reply found for chanDescriptor=$chanDescriptor")
    }

    replyManager.get().readReply(chanDescriptor) { reply ->
      formBuilder.addFormDataPart("mode", "regist")
      formBuilder.addFormDataPart("pwd", replyResponse.password)

      if (chanDescriptor is ThreadDescriptor) {
        val threadNo = chanDescriptor.threadNo
        formBuilder.addFormDataPart("resto", threadNo.toString())
      }

      formBuilder.addFormDataPart("name", reply.postName)
      formBuilder.addFormDataPart("email", reply.options)

      if (chanDescriptor is CatalogDescriptor
        && !TextUtils.isEmpty(reply.subject)) {
        formBuilder.addFormDataPart("sub", reply.subject)
      }

      formBuilder.addFormDataPart("com", reply.comment)

      if (reply.captchaSolution != null) {
        when (val captchaSolution = reply.captchaSolution!!) {
          is CaptchaSolution.SimpleTokenSolution -> {
            if (reply.captchaChallenge != null) {
              formBuilder.addFormDataPart("recaptcha_challenge_field", reply.captchaChallenge!!)
              formBuilder.addFormDataPart("recaptcha_response_field", captchaSolution.token)
            } else {
              formBuilder.addFormDataPart("g-recaptcha-response", captchaSolution.token)
            }
          }
          is CaptchaSolution.ChallengeWithSolution -> {
            formBuilder.addFormDataPart("t-challenge", captchaSolution.challenge)
            formBuilder.addFormDataPart("t-response", captchaSolution.solution)
          }
        }
      }

      if (site is Chan4) {
        if (reply.flag.isNotEmpty()) {
          formBuilder.addFormDataPart("flag", reply.flag)
        } else {
          val lastUsedFlag = boardFlagInfoRepository.get()
            .getLastUsedFlagKey(replyChanDescriptor.boardDescriptor())

          if (lastUsedFlag.isNotNullNorEmpty()) {
            formBuilder.addFormDataPart("flag", lastUsedFlag)
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
        attachFile(formBuilder, progressListener, replyFile, replyFileMetaInfo)

        if (replyFileMetaInfo.spoiler) {
          formBuilder.addFormDataPart("spoiler", "on")
        }
      }
    }
  }

  override fun process(response: Response, result: String) {
    setChan4CaptchaHeader(response.headers)

    val forgotCaptcha = result.contains(FORGOT_TO_SOLVE_CAPTCHA, ignoreCase = true)
    val mistypedCaptcha = result.contains(MISTYPED_CAPTCHA, ignoreCase = true)

    if (forgotCaptcha || mistypedCaptcha) {
      replyResponse.requireAuthentication = true
      Logger.e(TAG, "process() requireAuthentication (forgotCaptcha=$forgotCaptcha, mistypedCaptcha=$mistypedCaptcha)")
      return
    }

    val errorMessageMatcher = ERROR_MESSAGE_PATTERN.matcher(result)
    if (errorMessageMatcher.find()) {
      val errorMessage = Jsoup.parse(errorMessageMatcher.group(1)).body().text()
      replyResponse.errorMessage = errorMessage
      replyResponse.probablyBanned = checkIfBanned()

      Logger.e(TAG, "process() error (errorMessage=${errorMessage})")

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

    val threadNoMatcher = THREAD_NO_PATTERN.matcher(result)
    if (!threadNoMatcher.find()) {
      Logger.e(TAG, "process() Couldn't handle server response! response = \"$result\"")
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
          "threadNo: ${replyResponse.threadNo}, postNo: ${replyResponse.postNo}"
      return
    }

    if (replyResponse.threadNo > 0 && replyResponse.postNo > 0) {
      replyResponse.posted = true
      return
    }

    Logger.e(TAG, "process() Failed to handle server response. response: \"$result\"")
    replyResponse.errorMessage = "Failed to handle server response. Report this with logs attached!"
  }

  private fun setChan4CaptchaHeader(headers: Headers) {
    val chan4 = site as Chan4
    val chan4CaptchaSettings = chan4.chan4CaptchaSettings.get()

    if (!chan4CaptchaSettings.rememberCaptchaCookies) {
      Logger.d(TAG, "setChan4CaptchaHeader() rememberCaptchaCookies is false")
      return
    }

    val now = System.currentTimeMillis()
    val cookieReceivedOn = chan4CaptchaSettings.cookieReceivedOn
    val expired = (now - cookieReceivedOn) > Chan4CaptchaSettings.COOKIE_LIFE_TIME

    val wholeCookieHeader = headers
      .filter { (key, _) -> key.contains(SET_COOKIE_HEADER, ignoreCase = true) }
      .firstOrNull { (_, value) -> value.startsWith(CAPTCHA_COOKIE_PREFIX) }
      ?.second

    val newCookie = wholeCookieHeader
      ?.substringAfter(CAPTCHA_COOKIE_PREFIX)
      ?.substringBefore(';')

    val domain = wholeCookieHeader
      ?.substringAfter(DOMAIN_PREFIX)
      ?.substringBefore(';')

    Logger.d(TAG, "setChan4CaptchaHeader() newCookie='${newCookie}', " +
      "domain='${domain}', wholeCookieHeader='${wholeCookieHeader}'")

    if (domain == null) {
      Logger.d(TAG, "setChan4CaptchaHeader() domain is null")
      return
    }

    val oldCookie = when {
      domain.contains("4channel") -> chan4.channel4CaptchaCookie.get()
      domain.contains("4chan") -> chan4.chan4CaptchaCookie.get()
      else -> {
        Logger.e(TAG, "setChan4CaptchaHeader() unexpected domain: '$domain'")
        null
      }
    }

    Logger.d(TAG, "oldCookie='${formatToken(oldCookie)}', newCookie='${formatToken(newCookie)}', domain='${domain}'")

    if (oldCookie != null && oldCookie.isNotEmpty() && !expired) {
      Logger.d(TAG, "setChan4CaptchaHeader() cookie is still ok. " +
        "oldCookie='${formatToken(oldCookie)}', now=$now, cookieReceivedOn=$cookieReceivedOn, " +
        "delta=${now - cookieReceivedOn}, lifetime=${Chan4CaptchaSettings.COOKIE_LIFE_TIME}")
      return
    }

    Logger.d(TAG, "setChan4CaptchaHeader() cookie needs to be updated. " +
      "oldCookie='${formatToken(oldCookie)}', domain='${domain}', now=$now, cookieReceivedOn=$cookieReceivedOn, " +
      "delta=${now - cookieReceivedOn}, lifetime=${Chan4CaptchaSettings.COOKIE_LIFE_TIME}")

    if (domain.isNullOrEmpty() || newCookie.isNullOrEmpty()) {
      Logger.d(TAG, "setChan4CaptchaHeader() failed to parse 4chan_pass cookie (${formatToken(newCookie)}) or domain (${domain})")
      return
    }

    when {
      domain.contains("4channel") -> chan4.channel4CaptchaCookie.set(newCookie)
      domain.contains("4chan") -> chan4.chan4CaptchaCookie.set(newCookie)
      else -> Logger.e(TAG, "setChan4CaptchaHeader() unexpected domain: '$domain'")
    }

    chan4.chan4CaptchaSettings.set(chan4CaptchaSettings.copy(cookieReceivedOn = now))
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

  private fun checkIfBanned(): Boolean {
    val errorMessage = replyResponse.errorMessage
      ?: return false

    val isBannedFound = errorMessage.contains(PROBABLY_BANNED_TEXT)
    if (isBannedFound) {
      return true
    }

    if (!replyChanDescriptor.siteDescriptor().is4chan()) {
      return false
    }

    return errorMessage.contains(PROBABLY_IP_RANGE_BLOCKED)
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

  companion object {
    private const val TAG = "Chan4ReplyCall"

    private const val PROBABLY_BANNED_TEXT = "banned"
    private const val PROBABLY_IP_RANGE_BLOCKED = "Posting from your IP range has been blocked due to abuse"
    private const val FORGOT_TO_SOLVE_CAPTCHA = "Error: You forgot to solve the CAPTCHA"
    private const val MISTYPED_CAPTCHA = "Error: You seem to have mistyped the CAPTCHA"

    private const val SET_COOKIE_HEADER = "set-cookie"
    private const val CAPTCHA_COOKIE_PREFIX = "4chan_pass="
    private const val DOMAIN_PREFIX = "domain="

    private val THREAD_NO_PATTERN = Pattern.compile("<!-- thread:([0-9]+),no:([0-9]+) -->")
    private val ERROR_MESSAGE_PATTERN = Pattern.compile("\"errmsg\"[^>]*>(.*?)</span")

    // Error: You must wait 1 minute 17 seconds before posting a duplicate reply.
    // Error: You must wait 2 minutes 1 second before posting a duplicate reply.
    // Error: You must wait 17 seconds before posting a duplicate reply.
    private val RATE_LIMITED_PATTERN = Pattern.compile("must wait (?:(\\d+)\\s+minutes?)?.*?(?:(\\d+)\\s+seconds?)")
  }
}