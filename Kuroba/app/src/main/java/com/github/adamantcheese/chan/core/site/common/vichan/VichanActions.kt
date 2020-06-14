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
package com.github.adamantcheese.chan.core.site.common.vichan

import android.text.TextUtils
import com.github.adamantcheese.chan.core.di.NetModule.ProxiedOkHttpClient
import com.github.adamantcheese.chan.core.site.SiteAuthentication
import com.github.adamantcheese.chan.core.site.common.CommonSite
import com.github.adamantcheese.chan.core.site.common.CommonSite.CommonActions
import com.github.adamantcheese.chan.core.site.common.MultipartHttpCall
import com.github.adamantcheese.chan.core.site.http.DeleteRequest
import com.github.adamantcheese.chan.core.site.http.DeleteResponse
import com.github.adamantcheese.chan.core.site.http.Reply
import com.github.adamantcheese.chan.core.site.http.ReplyResponse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import java.util.regex.Pattern

open class VichanActions(
  commonSite: CommonSite,
  private val okHttpClient: ProxiedOkHttpClient
) : CommonActions(commonSite) {

  override fun setupPost(reply: Reply, call: MultipartHttpCall) {
    call.parameter("board", reply.loadable.boardCode)

    if (reply.loadable.isThreadMode) {
      call.parameter("thread", reply.loadable.no.toString())
    }

    // Added with VichanAntispam.
    // call.parameter("post", "Post");
    call.parameter("password", reply.password)
    call.parameter("name", reply.name)
    call.parameter("email", reply.options)

    if (!TextUtils.isEmpty(reply.subject)) {
      call.parameter("subject", reply.subject)
    }

    call.parameter("body", reply.comment)

    if (reply.file != null) {
      call.fileParameter("file", reply.fileName, reply.file)
    }

    if (reply.spoilerImage) {
      call.parameter("spoiler", "on")
    }
  }

  override fun requirePrepare(): Boolean {
    return true
  }

  override suspend fun prepare(call: MultipartHttpCall, reply: Reply, replyResponse: ReplyResponse) {
    val antispam = VichanAntispam(
      okHttpClient,
      reply.loadable.desktopUrl().toHttpUrl()
    )

    antispam.addDefaultIgnoreFields()

    for ((key, value) in antispam.get()) {
      call.parameter(key, value)
    }
  }

  override fun handlePost(replyResponse: ReplyResponse, response: Response, result: String) {
    val auth = Pattern.compile("\"captcha\": ?true").matcher(result)
    val err = errorPattern().matcher(result)

    when {
      auth.find() -> {
        replyResponse.requireAuthentication = true
        replyResponse.errorMessage = result
      }
      err.find() -> {
        replyResponse.errorMessage = Jsoup.parse(err.group(1)).body().text()
      }
      else -> {
        val url = response.request.url
        val m = Pattern.compile("/\\w+/\\w+/(\\d+).html").matcher(url.encodedPath)
        try {
          if (m.find()) {
            replyResponse.threadNo = m.group(1).toInt()
            val fragment = url.encodedFragment
            if (fragment != null) {
              replyResponse.postNo = fragment.toInt()
            } else {
              replyResponse.postNo = replyResponse.threadNo
            }
            replyResponse.posted = true
          }
        } catch (ignored: NumberFormatException) {
          replyResponse.errorMessage = "Error posting: could not find posted thread."
        }
      }
    }
  }

  override fun setupDelete(deleteRequest: DeleteRequest, call: MultipartHttpCall) {
    call.parameter("board", deleteRequest.post.board.code)
    call.parameter("delete", "Delete")
    call.parameter("delete_" + deleteRequest.post.no, "on")
    call.parameter("password", deleteRequest.savedReply.password)

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
    return Pattern.compile("<h1[^>]*>Error</h1>.*<h2[^>]*>(.*?)</h2>")
  }

  override fun postAuthenticate(): SiteAuthentication {
    return SiteAuthentication.fromNone()
  }

}