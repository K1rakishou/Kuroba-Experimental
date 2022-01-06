package com.github.k1rakishou.chan.features.issues

import android.content.Context
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.common.updatePaddings
import javax.inject.Inject

class ViewFullCrashLogController(
  context: Context,
  private val reportFile: ReportFile
) : Controller(context), ViewFullReportFileLayout.ViewFullCrashLogLayoutCallbacks, WindowInsetsListener {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()
    navigation.setTitle(reportFile.fileName)

    view = ViewFullReportFileLayout(context, reportFile).apply {
      onCreate(this@ViewFullCrashLogController)
    }

    onInsetsChanged()
    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  override fun onInsetsChanged() {
    val bottomPaddingDp = calculateBottomPaddingForRecyclerInDp(
      globalWindowInsetsManager = globalWindowInsetsManager,
      mainControllerCallbacks = null
    )

    view.updatePaddings(bottom = dp(bottomPaddingDp.toFloat()))
  }

  override fun onDestroy() {
    super.onDestroy()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    (view as ViewFullReportFileLayout).onDestroy()
  }

  override fun onFinished() {
    navigationController!!.popController()
  }
}