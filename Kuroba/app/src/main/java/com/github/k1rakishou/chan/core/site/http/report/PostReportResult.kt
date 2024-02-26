package com.github.k1rakishou.chan.core.site.http.report

sealed class PostReportResult {
  data object NotSupported : PostReportResult()
  data object Success : PostReportResult()
  data object CaptchaRequired : PostReportResult()
  data object AuthRequired : PostReportResult()
  data object CloudFlareDetected : PostReportResult()
  data class Error(val errorMessage: String) : PostReportResult()
}