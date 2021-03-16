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
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.features.reply.data.ReplyFileMeta
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException
import java.util.*


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
}