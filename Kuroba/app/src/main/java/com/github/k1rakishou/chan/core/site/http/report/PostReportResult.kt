package com.github.k1rakishou.chan.core.site.http.report

sealed class PostReportResult {
  object NotSupported : PostReportResult()
  object Success : PostReportResult()
  object CaptchaRequired : PostReportResult()
  object AuthRequired : PostReportResult()
  data class Error(val errorMessage: String) : PostReportResult()
}