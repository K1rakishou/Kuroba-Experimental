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
package com.github.k1rakishou.chan.core.site.sites.dvach

import android.text.TextUtils
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.common.CommonReplyHttpCall
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody.ProgressRequestListener
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.features.posting.LastReplyRepository
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.features.reply.data.ReplyFileMeta
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.persist_state.ReplyMode
import com.github.k1rakishou.prefs.OptionsSetting
import com.github.k1rakishou.prefs.StringSetting
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class DvachReplyCall internal constructor(
  site: Dvach,
  replyChanDescriptor: ChanDescriptor,
  val replyMode: ReplyMode,
  private val replyManager: ReplyManager
) : CommonReplyHttpCall(site, replyChanDescriptor) {

  override fun addParameters(
    formBuilder: MultipartBody.Builder,
    progressListener: ProgressRequestListener?
  ) {
    val chanDescriptor: ChanDescriptor = Objects.requireNonNull(
      replyChanDescriptor,
      "reply.chanDescriptor == null"
    )

    if (!replyManager.containsReply(chanDescriptor)) {
      throw IOException("No reply found for chanDescriptor=$chanDescriptor")
    }

    replyManager.readReply(chanDescriptor) { reply ->
      val threadNo = if (chanDescriptor is ThreadDescriptor) {
        chanDescriptor.threadNo
      } else {
        0L
      }

      formBuilder.addFormDataPart("task", "post")
      formBuilder.addFormDataPart("board", chanDescriptor.boardCode())
      formBuilder.addFormDataPart("comment", reply.comment)
      formBuilder.addFormDataPart("thread", threadNo.toString())
      formBuilder.addFormDataPart("name", reply.postName)
      formBuilder.addFormDataPart("email", reply.options)

      if (chanDescriptor is CatalogDescriptor && !TextUtils.isEmpty(reply.subject)) {
        formBuilder.addFormDataPart("subject", reply.subject)
      }

      if (reply.captchaResponse != null) {
        formBuilder.addFormDataPart("captcha_type", "recaptcha")

        val replyMode = site.requireSettingBySettingId<OptionsSetting<ReplyMode>>(
          SiteSetting.SiteSettingId.LastUsedReplyMode
        ).get()

        if (replyMode == ReplyMode.ReplyModeSendWithoutCaptcha) {
          formBuilder.addFormDataPart("captcha_key", Dvach.INVISIBLE_CAPTCHA_KEY)
        } else {
          formBuilder.addFormDataPart("captcha_key", Dvach.NORMAL_CAPTCHA_KEY)
        }

        val captchaChallenge = reply.captchaChallenge
        val captchaResponse = reply.captchaResponse

        if (captchaChallenge != null && captchaResponse != null) {
          formBuilder.addFormDataPart("recaptcha_challenge_field", captchaChallenge)
          formBuilder.addFormDataPart("recaptcha_response_field", captchaResponse)
        } else if (captchaResponse != null) {
          formBuilder.addFormDataPart("g-recaptcha-response", captchaResponse)
        }
      }

      if (reply.hasFiles()) {
        val filesCount = reply.filesCount()

        reply.iterateFilesOrThrowIfEmpty { fileIndex, replyFile ->
          val replyFileMetaResult = replyFile.getReplyFileMeta()
          if (replyFileMetaResult is ModularResult.Error<*>) {
            throw IOException((replyFileMetaResult as ModularResult.Error<ReplyFileMeta>).error)
          }

          val replyFileMetaInfo = (replyFileMetaResult as ModularResult.Value).value

          attachFile(
            formBuilder = formBuilder,
            fileIndex = fileIndex + 1,
            totalFiles = filesCount,
            progressListener = progressListener,
            replyFile = replyFile,
            replyFileMeta = replyFileMetaInfo
          )
        }
      }
    }
  }

  private fun attachFile(
    formBuilder: MultipartBody.Builder,
    fileIndex: Int,
    totalFiles: Int,
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
        fileIndex,
        totalFiles,
        fileOnDisk.asRequestBody(mediaType),
        progressListener
      )
    }

    formBuilder.addFormDataPart("formimages[]", replyFileMeta.fileName, requestBody)
  }

  override fun process(response: Response, result: String) {
    val errorMessageMatcher = ERROR_MESSAGE.matcher(result)
    if (errorMessageMatcher.find()) {
      val errorCode = errorMessageMatcher.group(1).toInt()
      val errorText = errorMessageMatcher.group(2)

      if (errorCode == INVALID_CAPTCHA_ERROR_CODE || errorText.equals(INVALID_CAPTCHA_ERROR_TEXT, ignoreCase = true)) {
        replyResponse.requireAuthentication = true
        return
      }

      if (replyChanDescriptor is ThreadDescriptor) {
        // Only check for rate limits when replying in threads. Do not do this when creating new
        // threads.
        if (errorText != null && errorText.contains(RATE_LIMITED_PATTERN, ignoreCase = true)) {
          replyResponse.rateLimitInfo = ReplyResponse.RateLimitInfo(
            actualTimeToWaitMs = POSTING_COOLDOWN_MS,
            cooldownInfo = LastReplyRepository.CooldownInfo(
              boardDescriptor = replyChanDescriptor.boardDescriptor,
              currentPostingCooldownMs = POSTING_COOLDOWN_MS
            )
          )

          return
        }
      }

      replyResponse.errorMessage = Jsoup.parse(errorText).body().text()
      replyResponse.probablyBanned = replyResponse.errorMessage?.contains(PROBABLY_BANNED_TEXT) ?: false
      return
    }

    if (!response.isSuccessful) {
      replyResponse.errorMessage = "Failed to post, bad response status code" + response.code
      return
    }

    val postMessageMatcher = POST_MESSAGE.matcher(result)
    if (postMessageMatcher.find()) {
      if (replyChanDescriptor is ThreadDescriptor) {
        replyResponse.threadNo = replyChanDescriptor.threadNo
      }

      replyResponse.postNo = postMessageMatcher.group(1).toInt().toLong()
      replyResponse.posted = true

      storeUserCodeCookieIfNeeded(response.headers)
      return
    }

    val threadMessageMatcher = THREAD_MESSAGE.matcher(result)
    if (threadMessageMatcher.find()) {
      val threadNo = threadMessageMatcher.group(1).toInt()

      replyResponse.threadNo = threadNo.toLong()
      replyResponse.postNo = threadNo.toLong()
      replyResponse.posted = true

      storeUserCodeCookieIfNeeded(response.headers)
      return
    }

    if (result.contains(ANTI_SPAM_SCRIPT_TAG)) {
      replyResponse.errorMessage = "2ch.hk anti spam script detected"
      replyResponse.additionalResponseData = ReplyResponse.AdditionalResponseData.DvachAntiSpamCheckDetected
      return
    }

    Logger.e(TAG, "Couldn't handle server response! response = \"$result\"")
    replyResponse.errorMessage = "Failed to post, see the logs for more info"
  }

  // usercode_auth=1234567890abcdef
  private fun storeUserCodeCookieIfNeeded(headers: Headers) {
    val userCodeSetting = site.getSettingBySettingId<StringSetting>(
      SiteSetting.SiteSettingId.DvachUserCodeCookie
    )

    if (userCodeSetting == null || userCodeSetting.get().isNotEmpty()) {
      return
    }

    val userCodeCookie = headers
      .firstOrNull { cookie -> cookie.second.startsWith(Dvach.USER_CODE_COOKIE_KEY) }

    if (userCodeCookie == null) {
      return
    }

    val userCodeCookieValue = userCodeCookie.second
      .split(";")
      .firstOrNull { value -> value.startsWith(Dvach.USER_CODE_COOKIE_KEY) }
      ?.removePrefix("${Dvach.USER_CODE_COOKIE_KEY}=")

    if (userCodeCookieValue == null || userCodeCookieValue.isEmpty()) {
      return
    }

    userCodeSetting.set(userCodeCookieValue)
  }

  companion object {
    private const val TAG = "DvachReplyCall"

    private const val INVALID_CAPTCHA_ERROR_CODE = -5
    private const val INVALID_CAPTCHA_ERROR_TEXT = "Капча невалидна"
    private const val RATE_LIMITED_PATTERN = "Вы постите слишком быстро"

    private val POSTING_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(35)

    private val ERROR_MESSAGE = Pattern.compile("^\\{\"Error\":(-?\\d+),\"Reason\":\"(.*)\"")
    private val POST_MESSAGE =
      Pattern.compile("^\\{\"Error\":null,\"Status\":\"OK\",\"Num\":(\\d+)")
    private val THREAD_MESSAGE =
      Pattern.compile("^\\{\"Error\":null,\"Status\":\"Redirect\",\"Target\":(\\d+)")
    private const val PROBABLY_BANNED_TEXT = "banned"
    const val ANTI_SPAM_SCRIPT_TAG = "<title>Проверка...</title>"
  }
}