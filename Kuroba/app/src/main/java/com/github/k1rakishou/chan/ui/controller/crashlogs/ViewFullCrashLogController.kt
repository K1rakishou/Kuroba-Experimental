package com.github.k1rakishou.chan.ui.controller.crashlogs

import android.content.Context
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.layout.crashlogs.ReportFile
import com.github.k1rakishou.chan.ui.layout.crashlogs.ViewFullReportFileLayout

class ViewFullCrashLogController(
  context: Context,
  private val reportFile: ReportFile
) : Controller(context), ViewFullReportFileLayout.ViewFullCrashLogLayoutCallbacks {

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()
    navigation.setTitle(reportFile.fileName)

    view = ViewFullReportFileLayout(context, reportFile).apply {
      onCreate(this@ViewFullCrashLogController)
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    (view as ViewFullReportFileLayout).onDestroy()
  }

  override fun onFinished() {
    navigationController!!.popController()
  }
}