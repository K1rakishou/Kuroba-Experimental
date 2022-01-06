package com.github.k1rakishou.chan.features.issues

import android.content.Context
import android.widget.FrameLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.ReportManager
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableListView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject


internal class ReviewReportFilesLayout(context: Context) : FrameLayout(context), ReportFilesListCallbacks {

  @Inject
  lateinit var reportManager: ReportManager
  @Inject
  lateinit var themeEngine: ThemeEngine

  private lateinit var compositeDisposable: CompositeDisposable
  private var callbacks: ReviewReportFilesLayoutCallbacks? = null
  private val crashLogsList: ColorizableListView
  private val deleteReportFilesButton: ColorizableBarButton
  private val sendReportFilesButton: ColorizableBarButton

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.controller_review_report_files, this).apply {
      crashLogsList = findViewById(R.id.review_reports_controller_reports_list)
      deleteReportFilesButton = findViewById(R.id.review_reports_controller_delete_reports_button)
      sendReportFilesButton = findViewById(R.id.review_reports_controller_send_reports_button)

      deleteReportFilesButton.setTextColor(themeEngine.chanTheme.textColorPrimary)
      sendReportFilesButton.setTextColor(themeEngine.chanTheme.textColorPrimary)

      val reportFiles = reportManager.getReportFiles()
        .map { crashLogFile -> ReportFile(crashLogFile, crashLogFile.name, false) }

      val adapter = ReportFilesListArrayAdapter(
        context,
        reportFiles,
        this@ReviewReportFilesLayout
      )

      crashLogsList.adapter = adapter
      adapter.updateAll()

      deleteReportFilesButton.setOnClickListener { onDeleteReportFilesButtonClicked(adapter) }
      sendReportFilesButton.setOnClickListener { onSendReportFilesButtonClicked(adapter) }
    }
  }

  private fun onDeleteReportFilesButtonClicked(adapter: ReportFilesListArrayAdapter) {
    val selectedCrashLogs = adapter.getSelectedCrashLogs()
    if (selectedCrashLogs.isEmpty()) {
      return
    }

    reportManager.deleteReportFiles(selectedCrashLogs)

    val newCrashLogsAmount = adapter.deleteSelectedCrashLogs(selectedCrashLogs)
    if (newCrashLogsAmount == 0) {
      callbacks?.onFinished()
    }

    showToast(context, getString(R.string.deleted_n_report_files, selectedCrashLogs.size))
  }

  private fun onSendReportFilesButtonClicked(adapter: ReportFilesListArrayAdapter) {
    val selectedCrashLogs = adapter.getSelectedCrashLogs()
    if (selectedCrashLogs.isEmpty()) {
      return
    }

    callbacks?.showProgressDialog()

    reportManager.sendCrashLogs(selectedCrashLogs) { result ->
      BackgroundUtils.ensureMainThread()
      callbacks?.hideProgressDialog()

      when (result) {
        is ModularResult.Value -> {
          if (selectedCrashLogs.size == adapter.count) {
            callbacks?.onFinished()
          } else {
            adapter.deleteSelectedCrashLogs(selectedCrashLogs)
          }

          showToast(context, getString(R.string.sent_n_report_files, selectedCrashLogs.size))
        }
        is ModularResult.Error -> {
          val message = "Error while trying to send logs: ${result.error.message}"
          Logger.e(TAG, message, result.error)
          showToast(context, message)
        }
      }
    }
  }

  fun onCreate(callbacks: ReviewReportFilesLayoutCallbacks) {
    this.callbacks = callbacks
    this.compositeDisposable = CompositeDisposable()
  }

  fun onDestroy() {
    callbacks = null
    compositeDisposable.dispose()
    (crashLogsList.adapter as ReportFilesListArrayAdapter).onDestroy()
  }

  override fun onReportFileClicked(reportFile: ReportFile) {
    callbacks?.onReportFileClicked(reportFile)
  }

  companion object {
    private const val TAG = "ReviewCrashLogsLayout"
  }
}
