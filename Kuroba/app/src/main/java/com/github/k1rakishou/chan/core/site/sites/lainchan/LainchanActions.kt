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
package com.github.k1rakishou.chan.core.site.sites.lainchan

import android.text.TextUtils
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.common.CommonClientException
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.CommonSite.CommonActions
import com.github.k1rakishou.chan.core.site.common.MultipartHttpCall
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.DeleteResponse
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.features.reply.data.ReplyFileMeta
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import dagger.Lazy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.util.regex.Pattern

open class LainchanActions(
  commonSite: CommonSite,
  private val proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>,
  private val siteManager: SiteManager,
  protected val replyManager: Lazy<ReplyManager>
) : CommonActions(commonSite) {

  override fun setupPost(replyChanDescriptor: ChanDescriptor, call: MultipartHttpCall): ModularResult<Unit> {
    return ModularResult.Try {
      replyManager.get().readReply(replyChanDescriptor) { reply ->
        call.parameter("board", reply.chanDescriptor.boardCode())

        if (reply.chanDescriptor is ChanDescriptor.ThreadDescriptor) {
          call.parameter("thread", reply.chanDescriptor.threadNo.toString())
        }

        // Added with VichanAntispam.
        // call.parameter("post", "Post");
        call.parameter("password", reply.password)
        call.parameter("name", reply.postName)
        call.parameter("email", reply.options)

        if (!TextUtils.isEmpty(reply.subject)) {
          call.parameter("subject", reply.subject)
        }

        call.parameter("body", reply.comment)

        if (reply.hasFiles()) {
          var spoiler = false

          reply.iterateFilesOrThrowIfEmpty { fileIndex, replyFile ->
            val replyFileMetaResult = replyFile.getReplyFileMeta()
            if (replyFileMetaResult is ModularResult.Error<*>) {
              throw IOException((replyFileMetaResult as ModularResult.Error<ReplyFileMeta>).error)
            }

            val replyFileMetaInfo = (replyFileMetaResult as ModularResult.Value).value
            call.fileParameter("file$fileIndex", replyFileMetaInfo.fileName, replyFile.fileOnDisk)

            // Apparently you can't spoiler individual files on Llainchan?
            if (replyFileMetaInfo.spoiler) {
              spoiler = true
            }
          }

          if (spoiler) {
            call.parameter("spoiler", "on")
          }
        }
      }
    }
  }

  override fun requirePrepare(): Boolean {
    return true
  }

  override suspend fun prepare(
    call: MultipartHttpCall,
    replyChanDescriptor: ChanDescriptor,
    replyResponse: ReplyResponse
  ): ModularResult<Unit> {
    val siteDescriptor = replyChanDescriptor.siteDescriptor()

    val site = siteManager.bySiteDescriptor(siteDescriptor)
      ?: return ModularResult.error(CommonClientException("Site ${siteDescriptor} is disabled or not active"))

    val desktopUrl = site.resolvable().desktopUrl(replyChanDescriptor, null)?.toHttpUrl()
      ?: return ModularResult.error(CommonClientException("Failed to get desktopUrl by chanDescriptor: $replyChanDescriptor"))

    val antispam = LainchanAntispam(proxiedOkHttpClient, desktopUrl)

    val antiSpamFieldsResult = antispam.get()
    if (antiSpamFieldsResult is ModularResult.Error) {
      Logger.e(TAG, "Antispam failure", antiSpamFieldsResult.error)
      return ModularResult.error(antiSpamFieldsResult.error)
    }

    antiSpamFieldsResult as ModularResult.Value

    for ((key, value) in antiSpamFieldsResult.value) {
      call.parameter(key, value)
    }

    return ModularResult.value(Unit)
  }

  override fun handlePost(replyResponse: ReplyResponse, response: Response, result: String) {
    val authMatcher = AUTH_PATTERN.matcher(result)
    val errorMatcher = errorPattern().matcher(result)

    when {
      authMatcher.find() -> {
        replyResponse.requireAuthentication = true
        replyResponse.errorMessage = result
      }
      errorMatcher.find() -> {
        replyResponse.errorMessage = Jsoup.parse(errorMatcher.group(1)).body().text()
      }
      else -> {
        val url = response.request.url
        val threadNoMatcher = THREAD_NO_PATTERN.matcher(url.encodedPath)

        try {
          if (!threadNoMatcher.find()) {
            replyResponse.errorMessage = "Failed to find threadNo pattern in server response"
            return
          }

          replyResponse.threadNo = threadNoMatcher.group(1).toLong()
          val fragment = url.encodedFragment
          if (fragment != null) {
            replyResponse.postNo = fragment.toLong()
          } else {
            replyResponse.postNo = replyResponse.threadNo
          }

          replyResponse.posted = true
        } catch (ignored: NumberFormatException) {
          replyResponse.errorMessage = "Error posting: could not find posted thread."
        }
      }
    }
  }

  override fun setupDelete(deleteRequest: DeleteRequest, call: MultipartHttpCall) {
    call.parameter("board", deleteRequest.post.boardDescriptor.boardCode)
    call.parameter("delete", "Delete")
    call.parameter("delete_" + deleteRequest.post.postNo(), "on")
    call.parameter("password", deleteRequest.savedReply.passwordOrEmptyString())

    if (deleteRequest.imageOnly) {
      call.parameter("file", "on")
    }
  }

  override fun handleDelete(response: DeleteResponse, httpResponse: Response, responseBody: String) {
    val err = errorPattern().matcher(responseBody)
    if (err.find()) {
      response.errorMessage = Jsoup.parse(err.group(1)).body().text()
    } else {
      response.deleted = true
    }
  }

  fun errorPattern(): Pattern {
    return ERROR_PATTERN
  }

  override fun postAuthenticate(): SiteAuthentication {
    return SiteAuthentication.fromNone()
  }

  companion object {
    private const val TAG = "VichanActions"

    private val AUTH_PATTERN = Pattern.compile("\"captcha\": ?true")
    private val ERROR_PATTERN = Pattern.compile("<h1[^>]*>Error</h1>.*<h2[^>]*>(.*?)</h2>")
    private val THREAD_NO_PATTERN = Pattern.compile("\\/res\\/(\\d+)\\.html")
  }
}