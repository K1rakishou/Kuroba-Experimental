package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import android.util.Base64
import android.webkit.MimeTypeMap
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.features.reply.data.ReplyFileMeta
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.chan.utils.HashingUtil
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.persist_state.ReplyMode
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.Lazy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.util.*

class LynxchanReplyHttpCall(
  site: LynxchanSite,
  private val replyChanDescriptor: ChanDescriptor,
  private val replyMode: ReplyMode,
  private val replyManager: Lazy<ReplyManager>,
  private val moshi: Lazy<Moshi>
) : HttpCall(site) {
  val replyResponse = ReplyResponse()

  override fun setup(
    requestBuilder: Request.Builder,
    progressListener: ProgressRequestBody.ProgressRequestListener?
  ) {
    val chanDescriptor = Objects.requireNonNull(
      replyChanDescriptor,
      "replyChanDescriptor == null"
    )

    if (!replyManager.get().containsReply(chanDescriptor)) {
      throw IOException("No reply found for chanDescriptor=$chanDescriptor")
    }

    replyResponse.siteDescriptor = chanDescriptor.siteDescriptor()
    replyResponse.boardCode = chanDescriptor.boardCode()

    replyManager.get().readReply(chanDescriptor) { reply ->
      val threadNo = if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
        chanDescriptor.threadNo
      } else {
        0L
      }

      val captcha = (reply.captchaSolution as CaptchaSolution.SimpleTokenSolution?)
        ?.token
        ?: ""

      val subject = if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
        reply.subject
      } else {
        ""
      }

      val files: MutableList<LynxchanReplyFile>? = if (reply.hasFiles()) {
        val files = mutableListOf<LynxchanReplyFile>()

        reply.iterateFilesOrThrowIfEmpty { _, replyFile ->
          val replyFileMetaResult = replyFile.getReplyFileMeta()
          if (replyFileMetaResult is ModularResult.Error<*>) {
            throw IOException((replyFileMetaResult as ModularResult.Error<ReplyFileMeta>).error)
          }

          val replyFileMeta = (replyFileMetaResult as ModularResult.Value).value

          val content = fileToLynxchanReplyFileContent(replyFile, replyFileMeta)
          if (content == null) {
            throw IOException("Failed to convert reply file into base64 string")
          }

          files += LynxchanReplyFile(
            name = replyFileMeta.fileName,
            spoiler = replyFileMeta.spoiler,
            content = content
          )
        }

        files
      } else {
        null
      }

      val postName = if (reply.postName.isNullOrEmpty()) {
        null
      } else {
        reply.postName
      }

      val threadId = if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
        threadNo.toString()
      } else {
        null
      }

      val lynxchanReplyData = LynxchanReplyData(
        captchaId = captcha,
        parameters = LynxchanReplyDataParameters(
          name = postName,
          subject = subject,
          password = StringUtils.generatePassword(),
          message = reply.comment,
          email = reply.options,
          files = files,
          boardUri = chanDescriptor.boardCode(),
          threadId = threadId
        ),
      )

      val content = moshi.get()
        .adapter(LynxchanReplyData::class.java)
        .toJson(lynxchanReplyData)

      val requestBody = RequestBody.create(
        contentType = "application/json".toMediaType(),
        content = content
      )

      requestBuilder
        .url(site.endpoints().reply(replyChanDescriptor))
        .post(requestBody)
    }
  }

  private fun fileToLynxchanReplyFileContent(replyFile: ReplyFile, replyFileMeta: ReplyFileMeta): String? {
    val extension = StringUtils.extractFileNameExtension(replyFileMeta.originalFileName)
      ?: return null

    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
      ?: return null

    val fileOnDisk = replyFile.fileOnDisk

    val fileBase64 = HashingUtil.fileBase64(inputFile = fileOnDisk, flags = Base64.NO_WRAP)
      ?: return null

    return buildString(capacity = fileBase64.length + 128) {
      // data:image/png;base64,
      append("data:${mimeType};base64,")
      append(fileBase64)
    }
  }

  override fun process(response: Response, result: String) {
    val lynxchanReplyResponse = convertToLynxchanReplyResponse(result)
    if (lynxchanReplyResponse == null) {
      replyResponse.errorMessage = "Failed to convert reply response (response=${result})"
      return
    }

    if (!lynxchanReplyResponse.isStatusOk()) {
      val status = lynxchanReplyResponse.status
      val errorMessage = lynxchanReplyResponse.dataAsString()

      replyResponse.errorMessage = "Failed to post. Status: ${status}, ErrorMessage: \'$errorMessage\'"
      return
    }

    val threadOrPostNo = lynxchanReplyResponse.dataAsLongOrNull()
    if (threadOrPostNo == null) {
      replyResponse.errorMessage = "Failed to post. No ThreadId/PostId found"
      return
    }

    replyResponse.posted = true

    if (replyChanDescriptor is ChanDescriptor.CatalogDescriptor) {
      replyResponse.threadNo = threadOrPostNo
      replyResponse.postNo = threadOrPostNo
    } else {
      replyChanDescriptor as ChanDescriptor.ThreadDescriptor
      replyResponse.threadNo = replyChanDescriptor.threadNo
      replyResponse.postNo = threadOrPostNo
    }
  }

  private fun convertToLynxchanReplyResponse(result: String): LynxchanReplyResponse? {
    Logger.d(TAG, "convertToLynxchanReplyResponse() result=\'$result\'")

    val splitData = result
      .removePrefix("{")
      .removeSuffix("}")
      .split(",")

    val map = mutableMapOf<String, String>()

    splitData.forEach { keyValue ->
      val splitKeyValue = keyValue.split(":").map { it.trim() }
      if (splitKeyValue.size != 2) {
        return@forEach
      }

      val key = splitKeyValue[0].removePrefix("\"").removeSuffix("\"").lowercase(Locale.ENGLISH)
      val value = splitKeyValue[1]

      map[key] = value
    }

    val status = map["status"]?.removePrefix("\"")?.removeSuffix("\"")
    if (status == null || status == "null") {
      Logger.e(TAG, "convertToLynxchanReplyResponse() \'status\' not found")
      return null
    }

    val data = map["data"]
    if (data == null || data == "null") {
      Logger.e(TAG, "convertToLynxchanReplyResponse() \'data\' not found")
      return null
    }

    val isStringData = data.startsWith("\"") || data.endsWith("\"")

    val lynxchanReplyResponseData = if (isStringData) {
      LynxchanReplyResponseData.Message(data.removePrefix("\"").removeSuffix("\""))
    } else {
      val value = data.toLongOrNull()
      if (value == null) {
        Logger.e(TAG, "convertToLynxchanReplyResponse() Failed to convert \'$data\' to long")
        return null
      }

      LynxchanReplyResponseData.Number(value)
    }

    return LynxchanReplyResponse(
      status = status,
      data = lynxchanReplyResponseData
    )
  }

  @JsonClass(generateAdapter = true)
  data class LynxchanReplyData(
    @Json(name = "captchaId") val captchaId: String,
    @Json(name = "parameters") val parameters: LynxchanReplyDataParameters
  )

  data class LynxchanReplyResponse(
    val status: String,
    val data: LynxchanReplyResponseData
  ) {
    fun isStatusOk(): Boolean = status == "ok"

    fun dataAsString(): String {
      return when (data) {
        is LynxchanReplyResponseData.Message -> data.value
        is LynxchanReplyResponseData.Number -> data.value.toString()
      }
    }

    fun dataAsLongOrNull(): Long? {
      return when (data) {
        is LynxchanReplyResponseData.Message -> null
        is LynxchanReplyResponseData.Number -> data.value
      }
    }
  }

  sealed class LynxchanReplyResponseData {
    data class Number(val value: Long) : LynxchanReplyResponseData()
    data class Message(val value: String) : LynxchanReplyResponseData()
  }

  @JsonClass(generateAdapter = true)
  data class LynxchanReplyDataParameters(
    @Json(name = "name") val name: String? = null,
    @Json(name = "flag") val flag: String? = null,
    @Json(name = "subject") val subject: String = "",
    @Json(name = "spoiler") val spoiler: Boolean = false,
    @Json(name = "password") val password: String,
    @Json(name = "message") val message: String,
    @Json(name = "email") val email: String,
    @Json(name = "files") val files: List<LynxchanReplyFile>?,
    @Json(name = "boardUri") val boardUri: String,
    @Json(name = "threadId") val threadId: String?,
  )


  @JsonClass(generateAdapter = true)
  data class LynxchanReplyFile(
    @Json(name = "name") val name: String,
    @Json(name = "content") val content: String,
    @Json(name = "spoiler") val spoiler: Boolean,
  )

  companion object {
    private const val TAG = "LynxchanReplyHttpCall"
  }
}