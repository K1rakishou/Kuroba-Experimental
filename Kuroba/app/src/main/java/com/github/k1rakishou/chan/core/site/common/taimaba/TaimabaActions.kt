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
package com.github.k1rakishou.chan.core.site.common.taimaba

import android.text.TextUtils
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.CommonSite.CommonActions
import com.github.k1rakishou.chan.core.site.common.MultipartHttpCall
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.features.reply.data.ReplyFileMeta
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import dagger.Lazy
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.util.regex.Pattern

open class TaimabaActions(
  commonSite: CommonSite,
  private val replyManager: Lazy<ReplyManager>
) : CommonActions(commonSite) {
  @Volatile
  var threadNo = 0L
  @Volatile
  var password: String? = null

  override fun setupPost(
    replyChanDescriptor: ChanDescriptor,
    call: MultipartHttpCall
  ): ModularResult<Unit> {
    return Try {
      if (!replyManager.get().containsReply(replyChanDescriptor)) {
        throw IOException("No reply found for chanDescriptor=$replyChanDescriptor")
      }

      replyManager.get().readReply(replyChanDescriptor) { reply ->
        // pass threadNo & password with correct variables
        threadNo = reply.threadNo()
        password = reply.password

        call.parameter("fart", ((Math.random() * 15000).toInt() + 5000).toString())
        call.parameter("board", reply.chanDescriptor.boardCode())
        call.parameter("task", "post")

        if (replyChanDescriptor is ThreadDescriptor) {
          call.parameter("parent", reply.threadNo().toString())
        }

        call.parameter("password", reply.password)
        call.parameter("field1", reply.postName)

        if (!TextUtils.isEmpty(reply.subject)) {
          call.parameter("field3", reply.subject)
        }

        call.parameter("field4", reply.comment)

        val replyFile = reply.firstFileOrNull()
        if (replyFile != null) {
          val replyFileMetaResult = replyFile.getReplyFileMeta()
          if (replyFileMetaResult is ModularResult.Error<*>) {
            throw IOException((replyFileMetaResult as ModularResult.Error<ReplyFileMeta>).error)
          }

          val replyFileMetaInfo = (replyFileMetaResult as ModularResult.Value).value
          call.fileParameter("file", replyFileMetaInfo.fileName, replyFile.fileOnDisk)
        }

        if (reply.options == "sage") {
          call.parameter("sage", "on")
        }
      }
    }
  }

  override fun handlePost(replyResponse: ReplyResponse, response: Response, result: String) {
    val err = errorPattern.matcher(result)
    if (err.find()) {
      replyResponse.errorMessage = Jsoup.parse(err.group(1)).body().text()
    } else {
      replyResponse.threadNo = threadNo
      replyResponse.password = password ?: ""
      replyResponse.posted = true
    }
  }

  override fun postAuthenticate(): SiteAuthentication {
    return SiteAuthentication.fromNone()
  }

  companion object {
    private val errorPattern = Pattern.compile("<pre.*?>([\\s\\S]*?)</pre>")
  }
}