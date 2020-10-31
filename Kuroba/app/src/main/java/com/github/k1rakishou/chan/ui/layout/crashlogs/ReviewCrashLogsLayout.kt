package com.github.k1rakishou.chan.ui.layout.crashlogs

import android.content.Context
import android.widget.FrameLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.ReportManager
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableListView
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.AndroidUtils.getString
import com.github.k1rakishou.chan.utils.AndroidUtils.showToast
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.chan.utils.plusAssign
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject


internal class ReviewCrashLogsLayout(context: Context) : FrameLayout(context), CrashLogsListCallbacks {

  @Inject
  lateinit var reportManager: ReportManager
  @Inject
  lateinit var themeEngine: ThemeEngine

  private lateinit var compositeDisposable: CompositeDisposable
  private var callbacks: ReviewCrashLogsLayoutCallbacks? = null
  private val crashLogsList: ColorizableListView
  private val deleteCrashLogsButton: ColorizableBarButton
  private val sendCrashLogsButton: ColorizableBarButton

  init {
    AndroidUtils.extractStartActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.controller_review_crashlogs, this).apply {
      crashLogsList = findViewById(R.id.review_crashlogs_controller_crashlogs_list)
      deleteCrashLogsButton = findViewById(R.id.review_crashlogs_controller_delete_crashlogs_button)
      sendCrashLogsButton = findViewById(R.id.review_crashlogs_controller_send_crashlogs_button)

      deleteCrashLogsButton.setTextColor(themeEngine.chanTheme.textColorPrimary)
      sendCrashLogsButton.setTextColor(themeEngine.chanTheme.textColorPrimary)

      val crashLogs = reportManager.getCrashLogs()
        .map { crashLogFile -> CrashLog(crashLogFile, crashLogFile.name, false) }

      val adapter = CrashLogsListArrayAdapter(
        context,
        crashLogs,
        this@ReviewCrashLogsLayout
      )

      crashLogsList.adapter = adapter
      adapter.updateAll()

      deleteCrashLogsButton.setOnClickListener { onDeleteCrashLogsButtonClicked(adapter) }
      sendCrashLogsButton.setOnClickListener { onSendCrashLogsButtonClicked(adapter) }
    }
  }

  private fun onDeleteCrashLogsButtonClicked(adapter: CrashLogsListArrayAdapter) {
    val selectedCrashLogs = adapter.getSelectedCrashLogs()
    if (selectedCrashLogs.isEmpty()) {
      return
    }

    reportManager.deleteCrashLogs(selectedCrashLogs)

    val newCrashLogsAmount = adapter.deleteSelectedCrashLogs(selectedCrashLogs)
    if (newCrashLogsAmount == 0) {
      callbacks?.onFinished()
    }

    showToast(context, getString(R.string.deleted_n_crashlogs, selectedCrashLogs.size))
  }

  private fun onSendCrashLogsButtonClicked(adapter: CrashLogsListArrayAdapter) {
    val selectedCrashLogs = adapter.getSelectedCrashLogs()
    if (selectedCrashLogs.isEmpty()) {
      return
    }

    compositeDisposable += reportManager.sendCrashLogs(selectedCrashLogs)
      .observeOn(AndroidSchedulers.mainThread())
      .doOnSubscribe { callbacks?.showProgressDialog() }
      .subscribe({
        callbacks?.hideProgressDialog()

        if (selectedCrashLogs.size == adapter.count) {
          callbacks?.onFinished()
        } else {
          adapter.deleteSelectedCrashLogs(selectedCrashLogs)
        }

        showToast(context, getString(R.string.sent_n_crashlogs, selectedCrashLogs.size))
      }, { error ->
        val message = "Error while trying to send logs: ${error.message}"
        Logger.e(TAG, message, error)
        showToast(context, message)

        callbacks?.hideProgressDialog()
      })
  }

  fun onCreate(callbacks: ReviewCrashLogsLayoutCallbacks) {
    this.callbacks = callbacks
    this.compositeDisposable = CompositeDisposable()
  }

  fun onDestroy() {
    callbacks = null
    compositeDisposable.dispose()
    (crashLogsList.adapter as CrashLogsListArrayAdapter).onDestroy()
  }

  override fun onCrashLogClicked(crashLog: CrashLog) {
    callbacks?.onCrashLogClicked(crashLog)
  }

  companion object {
    private const val TAG = "ReviewCrashLogsLayout"
  }
}
