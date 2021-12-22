package com.github.k1rakishou.chan.core.site.sites.dvach

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.site.common.CommonClientException
import com.github.k1rakishou.chan.core.site.http.report.PostReportData
import com.github.k1rakishou.chan.core.site.http.report.PostReportResult
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.common.useBufferedSource
import com.github.k1rakishou.core_logger.Logger
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request

class DvachReportPostRequest(
  private val site: Dvach,
  private val _moshi: Lazy<Moshi>,
  private val _proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>,
  private val postReportData: PostReportData.Dvach
) {
  private val moshi: Moshi
    get() = _moshi.get()
  private val proxiedOkHttpClient: RealProxiedOkHttpClient
    get() = _proxiedOkHttpClient.get()

  suspend fun execute(): PostReportResult {
    val postDescriptor = postReportData.postDescriptor

    val result: ModularResult<PostReportResult> = ModularResult.Try {
      val siteHost = (site.endpoints() as Dvach.DvachEndpoints).siteHost
      val requestModifier = (site.requestModifier() as Dvach.DvachSiteRequestModifier)
      val reportReason = postReportData.message

      val reportPostEndpoint = HttpUrl.Builder()
        .scheme("https")
        .host(siteHost)
        .addPathSegment("makaba")
        .addPathSegment("makaba.fcgi")
        .addQueryParameter("json", "1")
        .build()

      Logger.d(TAG, "reportPost($postDescriptor) reportPostEndpoint=$reportPostEndpoint")

      val body = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("task", "report")
        .addFormDataPart("board", postDescriptor.boardDescriptor().boardCode)
        .addFormDataPart("thread", postDescriptor.threadDescriptor().threadNo.toString())
        .addFormDataPart("posts", postDescriptor.postNo.toString())
        .addFormDataPart("comment", reportReason)
        .build()

      val requestBuilder = Request.Builder()
        .url(reportPostEndpoint)
        .post(body)

      requestModifier.modifyPostReportRequest(site, requestBuilder)

      val reportResponseData = sendDvachReportRequest(
        proxiedOkHttpClient.okHttpClient(),
        requestBuilder.build(),
        moshi.adapter(ReportResponseData::class.java)
      ).unwrap()

      if (reportResponseData == null) {
        throw CommonClientException("Failed to convert server response into ReportResponseData")
      }

      if (reportResponseData.isOk()) {
        return@Try PostReportResult.Success
      }

      val errorMessage = if (!reportResponseData.bothNullOrEmpty()) {
        buildString {
          if (reportResponseData.messageTitle.isNotNullNorEmpty()) {
            append("title: ")
            append(reportResponseData.messageTitle)
          }

          if (reportResponseData.message.isNotNullNorEmpty()) {
            if (isNotEmpty()) {
              append(", ")
            }

            append("message: ")
            append(reportResponseData.message)
          }
        }
      } else {
        "Unknown error"
      }

      return@Try PostReportResult.Error(errorMessage)
    }

    when (result) {
      is ModularResult.Error -> {
        if (result.error is DvachAuthRequiredException) {
          Logger.e(TAG, "reportPost($postDescriptor) auth required")
          return PostReportResult.AuthRequired
        }

        Logger.e(TAG, "reportPost($postDescriptor) error", result.error)
        return PostReportResult.Error(result.error.errorMessageOrClassName())
      }
      is ModularResult.Value -> {
        Logger.e(TAG, "reportPost($postDescriptor) success")
        return result.value
      }
    }
  }

  private suspend fun sendDvachReportRequest(
    okHttpClient: OkHttpClient,
    request: Request,
    adapter: JsonAdapter<ReportResponseData>
  ): ModularResult<ReportResponseData?> {
    return withContext(Dispatchers.IO) {
      return@withContext ModularResult.Try {
        val response = okHttpClient.suspendCall(request)

        if (!response.isSuccessful) {
          throw BadStatusResponseException(response.code)
        }

        val body = response.body
        if (body == null) {
          throw EmptyBodyResponseException()
        }

        Logger.d(TAG, "sendDvachReportRequest() contentType=${body.contentType()}")

        if (body.contentType()?.type?.equals("application", ignoreCase = true) == false) {
          throw DvachAuthRequiredException()
        }

        if (body.contentType()?.subtype?.equals("json", ignoreCase = true) == false) {
          throw DvachAuthRequiredException()
        }

        return@Try body.useBufferedSource { bufferedSource ->
          adapter.fromJson(bufferedSource) as ReportResponseData
        }
      }
    }
  }

  @JsonClass(generateAdapter = true)
  data class ReportResponseData(
    @Json(name = "message") val message: String?,
    @Json(name = "message_title") val messageTitle: String?
  ) {

    fun bothNullOrEmpty(): Boolean {
      return messageTitle.isNullOrEmpty() && message.isNullOrEmpty()
    }

    fun isOk(): Boolean {
      return messageTitle.equals(NO_ERRORS_MSG, ignoreCase = true)
    }

  }

  class DvachAuthRequiredException : Exception("Dvach Auth required")

  companion object {
    private const val TAG = "DvachReportPostRequest"
    private const val NO_ERRORS_MSG = "Ошибок нет"
  }

}