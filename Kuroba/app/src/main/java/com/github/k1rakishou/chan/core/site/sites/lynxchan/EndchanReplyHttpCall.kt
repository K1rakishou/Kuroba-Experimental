package com.github.k1rakishou.chan.core.site.sites.lynxchan

import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.BaseLynxchanReplyHttpCall
import com.github.k1rakishou.chan.features.reply.data.Reply
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.network.ProgressRequestBody
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class EndchanReplyHttpCall(
  site: Endchan,
  replyChanDescriptor: ChanDescriptor,
  replyManager: ReplyManager,
  moshi: Moshi
) : BaseLynxchanReplyHttpCall(
  site = site,
  replyChanDescriptor = replyChanDescriptor,
  replyManager = replyManager,
  moshi = moshi
) {

  override fun createRequestBody(
    reply: Reply,
    chanDescriptor: ChanDescriptor,
    threadNo: Long,
    captcha: String?,
    subject: String?,
    progressListener: ProgressRequestBody.ProgressRequestListener?
  ): RequestBody {
    val threadId = if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      threadNo.toString()
    } else {
      null
    }

    val replyData = EndchanReplyData(
      captchaId = captcha,
      parameters = EndchanReplyDataParameters(
        name = reply.postName.takeIf { postName -> postName.isNotEmpty() },
        flag = null,
        captcha = captcha,
        roleSignatureRequested = false,
        subject = subject ?: "",
        spoiler = false,
        password = StringUtils.generatePassword(),
        message = reply.comment,
        email = reply.options,
        files = collectJsonReplyFiles(reply) ?: emptyList(),
        boardUri = chanDescriptor.boardCode(),
        threadId = threadId
      ),
      auth = emptyMap()
    )

    val content = moshi
      .adapter(EndchanReplyData::class.java)
      // The site sends nulls explicitly (e.g. "name": null)
      .serializeNulls()
      .toJson(replyData)

    return content.toRequestBody("application/json".toMediaType())
  }

  @JsonClass(generateAdapter = true)
  data class EndchanReplyData(
    @Json(name = "captchaId") val captchaId: String?,
    @Json(name = "parameters") val parameters: EndchanReplyDataParameters,
    @Json(name = "auth") val auth: Map<String, String>
  )

  @JsonClass(generateAdapter = true)
  data class EndchanReplyDataParameters(
    @Json(name = "name") val name: String?,
    @Json(name = "flag") val flag: String?,
    @Json(name = "captcha") val captcha: String?,
    @Json(name = "roleSignatureRequested") val roleSignatureRequested: Boolean,
    @Json(name = "subject") val subject: String,
    @Json(name = "spoiler") val spoiler: Boolean,
    @Json(name = "password") val password: String,
    @Json(name = "message") val message: String,
    @Json(name = "email") val email: String,
    @Json(name = "files") val files: List<LynxchanReplyFile>,
    @Json(name = "boardUri") val boardUri: String,
    @Json(name = "threadId") val threadId: String?,
  )
}
