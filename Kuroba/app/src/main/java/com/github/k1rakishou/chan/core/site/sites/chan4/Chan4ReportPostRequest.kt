package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.common.CommonClientException
import com.github.k1rakishou.chan.core.site.http.report.PostReportData
import com.github.k1rakishou.chan.core.site.http.report.PostReportResult
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.suspendConvertIntoJsoupDocument
import com.github.k1rakishou.core_logger.Logger
import dagger.Lazy
import okhttp3.MultipartBody
import okhttp3.Request
import java.util.*

class Chan4ReportPostRequest(
  private val siteManager: SiteManager,
  private val _proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>,
  private val postReportData: PostReportData.Chan4
) {

  private val proxiedOkHttpClient: RealProxiedOkHttpClient
    get() = _proxiedOkHttpClient.get()

  suspend fun execute(): PostReportResult {
    val postDescriptor = postReportData.postDescriptor
    val selectedCategoryId = postReportData.catId
    val captchaInfo = postReportData.captchaInfo

    val site = siteManager.bySiteDescriptor(postDescriptor.siteDescriptor())
      ?: return PostReportResult.Error("Site is not active")

    val endpoints = site.endpoints() as? Chan4.Chan4Endpoints
      ?: return PostReportResult.Error("Bad endpoints()")

    val sysEndpoint = endpoints.getSysEndpoint(postDescriptor.boardDescriptor())

    val result: ModularResult<PostReportResult> = ModularResult.Try {
      val reportPostEndpoint = String.format(
        Locale.ENGLISH,
        REPORT_POST_ENDPOINT_FORMAT,
        sysEndpoint,
        postDescriptor.boardDescriptor().boardCode,
        postDescriptor.postNo
      )

      Logger.d(TAG, "reportPost($postDescriptor, $selectedCategoryId) reportPostEndpoint=$reportPostEndpoint")

      val requestBuilder = with(MultipartBody.Builder()) {
        setType(MultipartBody.FORM)
        addFormDataPart("cat_id", selectedCategoryId.toString())

        when (captchaInfo) {
          is PostReportData.Chan4.CaptchaInfo.Solution -> {
            addFormDataPart("t-challenge", captchaInfo.captchaSolution.challenge)
            addFormDataPart("t-response", captchaInfo.captchaSolution.solution)
          }
          PostReportData.Chan4.CaptchaInfo.UsePasscode -> {
            // no-op
          }
        }

        addFormDataPart("board", postDescriptor.boardDescriptor().boardCode)
        addFormDataPart("no", postDescriptor.postNo.toString())

        val body = build()

        return@with Request.Builder()
          .url(reportPostEndpoint)
          .post(body)
      }

      site.requestModifier().modifyPostReportRequest(site, requestBuilder)

      val document = proxiedOkHttpClient.okHttpClient()
        .suspendConvertIntoJsoupDocument(requestBuilder.build())
        .unwrap()

      val bodyElement = document.getElementsByTag("body").firstOrNull()
        ?: throw CommonClientException("<body> not found in response")

      val errorText = bodyElement.getElementsByTag("font").firstOrNull()?.wholeText()
        ?: throw CommonClientException("<font> not found inside of <body> tag")

      Logger.d(TAG, "reportPost($postDescriptor, $selectedCategoryId) errorText='$errorText'")

      if (errorText.contains(SUCCESS_TEXT, ignoreCase = true)) {
        Logger.d(TAG, "reportPost($postDescriptor, $selectedCategoryId) success")
        return@Try PostReportResult.Success
      }

      if (errorText.contains(CAPTCHA_REQUIRED_ERROR_TEXT, ignoreCase = true)) {
        return@Try PostReportResult.CaptchaRequired
      }

      return@Try PostReportResult.Error(errorMessage = errorText)
    }

    if (result is ModularResult.Error) {
      Logger.e(TAG, "reportPost($postDescriptor, $selectedCategoryId) error", result.error)
      return PostReportResult.Error(result.error.errorMessageOrClassName())
    }

    result as ModularResult.Value
    return result.value
  }

  companion object {
    private const val TAG = "Chan4ReportPostRequest"
    private const val SUCCESS_TEXT = "Report submitted"
    private const val CAPTCHA_REQUIRED_ERROR_TEXT = "You seem to have mistyped the CAPTCHA"

    const val REPORT_POST_ENDPOINT_FORMAT = "%s%s/imgboard.php?mode=report&no=%d"
  }

}