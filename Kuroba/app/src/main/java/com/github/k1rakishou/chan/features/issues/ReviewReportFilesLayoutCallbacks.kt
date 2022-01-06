package com.github.k1rakishou.chan.features.issues

internal interface ReviewReportFilesLayoutCallbacks {
  fun onReportFileClicked(reportFile: ReportFile)
  fun showProgressDialog()
  fun hideProgressDialog()
  fun onFinished()
}