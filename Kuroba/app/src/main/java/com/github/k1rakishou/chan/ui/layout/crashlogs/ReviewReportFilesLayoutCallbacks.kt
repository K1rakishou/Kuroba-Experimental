package com.github.k1rakishou.chan.ui.layout.crashlogs

internal interface ReviewReportFilesLayoutCallbacks {
  fun onReportFileClicked(reportFile: ReportFile)
  fun showProgressDialog()
  fun hideProgressDialog()
  fun onFinished()
}