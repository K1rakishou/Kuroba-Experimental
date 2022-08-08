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
import com.github.k1rakishou.chan.features.reply.data.Reply
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.features.reply.data.ReplyFileMeta
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.persist_state.ReplyMode
import com.github.k1rakishou.prefs.OptionsSetting
import com.github.k1rakishou.prefs.StringSetting
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.Lazy
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

class DvachReplyCall internal constructor(
  site: Dvach,
  replyChanDescriptor: ChanDescriptor,
  val replyMode: ReplyMode,
  private val moshi: Lazy<Moshi>,
  private val replyManager: Lazy<ReplyManager>
) : CommonReplyHttpCall(site, replyChanDescriptor) {

  override fun addParameters(
    formBuilder: MultipartBody.Builder,
    progressListener: ProgressRequestListener?
  ) {
    val chanDescriptor: ChanDescriptor = Objects.requireNonNull(
      replyChanDescriptor,
      "reply.chanDescriptor == null"
    )

    if (!replyManager.get().containsReply(chanDescriptor)) {
      throw IOException("No reply found for chanDescriptor=$chanDescriptor")
    }

    replyManager.get().readReply(chanDescriptor) { reply ->
      val threadNo = if (chanDescriptor is ThreadDescriptor) {
        chanDescriptor.threadNo
      } else {
        0L
      }

      formBuilder.addFormDataPart("board", chanDescriptor.boardCode())
      formBuilder.addFormDataPart("thread", threadNo.toString())
      formBuilder.addFormDataPart("name", reply.postName)
      formBuilder.addFormDataPart("email", reply.options)
      formBuilder.addFormDataPart("comment", reply.comment)

      if (chanDescriptor is CatalogDescriptor && !TextUtils.isEmpty(reply.subject)) {
        formBuilder.addFormDataPart("subject", reply.subject)
      }

      if (reply.captchaSolution != null) {
        when (val captchaSolution = reply.captchaSolution!!) {
          is CaptchaSolution.SimpleTokenSolution -> {
            recaptchaAuth(formBuilder, reply, captchaSolution)
          }
          is CaptchaSolution.ChallengeWithSolution -> {
            dvachCaptchaAuth(formBuilder, captchaSolution)
          }
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

  private fun dvachCaptchaAuth(
    formBuilder: MultipartBody.Builder,
    captchaSolution: CaptchaSolution.ChallengeWithSolution
  ) {
    formBuilder.addFormDataPart("captcha_type", "2chcaptcha")
    formBuilder.addFormDataPart("2chcaptcha_value", captchaSolution.solution)
    formBuilder.addFormDataPart("2chcaptcha_id", captchaSolution.challenge)
  }

  private fun recaptchaAuth(
    formBuilder: MultipartBody.Builder,
    reply: Reply,
    captchaSolution: CaptchaSolution.SimpleTokenSolution
  ) {
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
    val token = captchaSolution.token

    if (captchaChallenge != null) {
      formBuilder.addFormDataPart("recaptcha_challenge_field", captchaChallenge)
      formBuilder.addFormDataPart("recaptcha_response_field", token)
    } else {
      formBuilder.addFormDataPart("g-recaptcha-response", token)
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

    formBuilder.addFormDataPart("file[]", replyFileMeta.fileName, requestBody)
  }

  override fun process(response: Response, result: String) {
    val postingResult = try {
      moshi.get()
        .adapter(PostingResult::class.java)
        .fromJson(result)
    } catch (error: Throwable) {
      Logger.e(TAG, "Couldn't handle server response! response = \"$result\"")
      replyResponse.errorMessage = "Failed process server response, error: ${error.errorMessageOrClassName()}"
      return
    }

    if (postingResult == null) {
      Logger.e(TAG, "Couldn't handle server response! response = \"$result\"")
      replyResponse.errorMessage = "Failed process server response (postingResult == null)"
      return
    }

    if (postingResult.error != null) {
      val errorCode = postingResult.error.code
      val errorText = postingResult.error.message

      if (errorCode == INVALID_CAPTCHA_ERROR_CODE || errorText.equals(INVALID_CAPTCHA_ERROR_TEXT, ignoreCase = true)) {
        replyResponse.requireAuthentication = true
        return
      }

      if (replyChanDescriptor is ThreadDescriptor) {
        // Only check for rate limits when replying in threads. Do not do this when creating new
        // threads.
        if (errorCode == RATE_LIMITED_ERROR_CODE || errorText.contains(RATE_LIMITED_PATTERN, ignoreCase = true)) {
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

      replyResponse.errorMessage = errorText
      replyResponse.probablyBanned = replyResponse.errorMessage
        ?.contains(PROBABLY_BANNED_TEXT, ignoreCase = true)
        ?: false

      return
    }

    if (!response.isSuccessful) {
      replyResponse.errorMessage = "Failed to post, bad response status code: " + response.code
      return
    }

    if (postingResult.postNo != null) {
      if (replyChanDescriptor is ThreadDescriptor) {
        replyResponse.threadNo = replyChanDescriptor.threadNo
      }

      replyResponse.postNo = postingResult.postNo
      replyResponse.posted = true

      storeUserCodeCookieIfNeeded(response.headers)
      return
    }

    if (postingResult.threadNo != null) {
      val threadNo = postingResult.threadNo

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

  @JsonClass(generateAdapter = true)
  data class PostingResult(
    val result: Int,
    val error: DvachError?,
    @Json(name = "thread")
    val threadNo: Long?,
    @Json(name = "num")
    val postNo: Long?
  )

  @JsonClass(generateAdapter = true)
  data class DvachError(
    val code: Int,
    val message: String
  )

  companion object {
    private const val TAG = "DvachReplyCall"

    private const val INVALID_CAPTCHA_ERROR_CODE = -5
    private const val RATE_LIMITED_ERROR_CODE = -8

    private const val INVALID_CAPTCHA_ERROR_TEXT = "Капча невалидна"
    private const val RATE_LIMITED_PATTERN = "Вы постите слишком быстро"

    private val POSTING_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(35)

    private const val PROBABLY_BANNED_TEXT = "Постинг запрещён"
    const val ANTI_SPAM_SCRIPT_TAG = "<title>Проверка...</title>"
  }
}