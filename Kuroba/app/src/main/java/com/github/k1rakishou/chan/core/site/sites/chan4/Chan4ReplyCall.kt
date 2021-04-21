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
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.common.CommonReplyHttpCall
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody.ProgressRequestListener
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.features.posting.LastReplyRepository
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.features.reply.data.ReplyFileMeta
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
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
  site: Site?,
  replyChanDescriptor: ChanDescriptor?,
  private val replyManager: ReplyManager,
  private val boardManager: BoardManager,
  private val appConstants: AppConstants
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

    if (!replyManager.containsReply(chanDescriptor)) {
      throw IOException("No reply found for chanDescriptor=$chanDescriptor")
    }

    replyManager.readReply(chanDescriptor) { reply ->
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

      if (reply.captchaResponse != null) {
        if (reply.captchaChallenge != null) {
          formBuilder.addFormDataPart("recaptcha_challenge_field", reply.captchaChallenge!!)
          formBuilder.addFormDataPart("recaptcha_response_field", reply.captchaResponse!!)
        } else {
          formBuilder.addFormDataPart("g-recaptcha-response", reply.captchaResponse!!)
        }
      }

      if (site is Chan4 && reply.chanDescriptor.boardCode() == "pol") {
        if (reply.flag.isNotEmpty()) {
          formBuilder.addFormDataPart("flag", reply.flag)
        } else {
          formBuilder.addFormDataPart("flag", site.flagType.get())
        }
      }

      check(reply.filesCount() <= 1) { "Bad files count: ${reply.filesCount()}" }

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
    if (result.contains(CAPTCHA_REQUIRED_TEXT1, ignoreCase = true)
      || result.contains(CAPTCHA_REQUIRED_TEXT2, ignoreCase = true)) {
      replyResponse.requireAuthentication = true
      return
    }

    val errorMessageMatcher = ERROR_MESSAGE_PATTERN.matcher(result)
    if (errorMessageMatcher.find()) {
      val errorMessage = Jsoup.parse(errorMessageMatcher.group(1)).body().text()
      replyResponse.errorMessage = errorMessage
      replyResponse.probablyBanned = checkIfBanned()

      if (replyChanDescriptor is ThreadDescriptor) {
        // Only check for rate limits when replying in threads. Do not do this when creating new
        // threads.
        val rateLimitMatcher = RATE_LIMITED_PATTERN.matcher(errorMessage)
        if (rateLimitMatcher.find()) {
          replyResponse.rateLimitInfo = createRateLimitInfo(rateLimitMatcher)
          return
        }
      }

      return
    }

    val threadNoMatcher = THREAD_NO_PATTERN.matcher(result)
    if (!threadNoMatcher.find()) {
      Logger.e(TAG, "Couldn't handle server response! response = \"$result\"")
      return
    }

    try {
      replyResponse.threadNo = threadNoMatcher.group(1).toInt().toLong()
      replyResponse.postNo = threadNoMatcher.group(2).toInt().toLong()

      if (replyResponse.threadNo == 0L) {
        replyResponse.threadNo = replyResponse.postNo
      }
    } catch (error: NumberFormatException) {
      Logger.e(TAG, "ReplyResponse parsing error", error)
    }

    if (replyResponse.threadNo > 0 && replyResponse.postNo > 0) {
      replyResponse.posted = true
      return
    }

    Logger.e(TAG, "Couldn't handle server response! response = \"$result\"")
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
    private const val CAPTCHA_REQUIRED_TEXT1 = "Error: You forgot to solve the CAPTCHA"
    private const val CAPTCHA_REQUIRED_TEXT2 = "Error: You seem to have mistyped the CAPTCHA"

    // Not used.
    private const val NEW_THREAD_CREATION_RATE_LIMIT_TEXT = "Error: You must wait longer before posting a new thread"

    private val THREAD_NO_PATTERN = Pattern.compile("<!-- thread:([0-9]+),no:([0-9]+) -->")
    private val ERROR_MESSAGE_PATTERN = Pattern.compile("\"errmsg\"[^>]*>(.*?)</span")
    private val RATE_LIMITED_PATTERN = Pattern.compile("must wait (?:(\\d+)\\s+minutes)?.*?(?:(\\d+)\\s+seconds)")
  }
}