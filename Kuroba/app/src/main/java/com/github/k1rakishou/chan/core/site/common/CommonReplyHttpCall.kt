package com.github.k1rakishou.chan.core.site.common

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.utils.Generators.generateHttpBoundary
import com.github.k1rakishou.chan.utils.Generators.generateRandomHexString
import com.github.k1rakishou.common.network.ProgressRequestBody.ProgressRequestListener
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

abstract class CommonReplyHttpCall(
  site: Site,
  val replyChanDescriptor: ChanDescriptor
) : HttpCall(site) {
  val replyResponse = ReplyResponse()

  init {
    this.replyResponse.siteDescriptor = replyChanDescriptor.siteDescriptor()
    this.replyResponse.boardCode = replyChanDescriptor.boardCode()
  }

  @Throws(IOException::class)
  override suspend fun setup(
    requestBuilder: Request.Builder,
    progressListener: ProgressRequestListener?
  ) {
    replyResponse.password = generateRandomHexString(16)

    val boundary = "------WebKitFormBoundary" + generateHttpBoundary()
    val formBuilder = MultipartBody.Builder(boundary)
    formBuilder.setType(MultipartBody.FORM)

    val replyUrl = site.endpoints.reply(this.replyChanDescriptor)
      ?: error("Posting is not supported by ${replyChanDescriptor.siteName()}")

    requestBuilder.url(replyUrl)

    addParameters(formBuilder, progressListener)
    addHeaders(requestBuilder, boundary)

    requestBuilder.post(formBuilder.build())
  }

  abstract override suspend fun process(response: Response, result: String)

  @Throws(IOException::class)
  abstract suspend fun addParameters(
    builder: MultipartBody.Builder,
    progressListener: ProgressRequestListener?
  )

  @Throws(IOException::class)
  abstract suspend fun addHeaders(
    requestBuilder: Request.Builder,
    boundary: String
  )
}
