package com.github.k1rakishou.chan.features.issues

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.common.updatePaddings
import javax.inject.Inject

class ReviewReportFilesController(context: Context)
  : Controller(context), ReviewReportFilesLayoutCallbacks, WindowInsetsListener {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private var loadingViewController: LoadingViewController? = null

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()
    navigation.setTitle(R.string.review_report_files_controller_title)

    view = ReviewReportFilesLayout(context).apply { onCreate(this@ReviewReportFilesController) }

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
    (view as ReviewReportFilesLayout).onDestroy()
  }

  override fun showProgressDialog() {
    hideProgressDialog()

    loadingViewController = LoadingViewController(context, true)
    presentController(loadingViewController!!)
  }

  override fun hideProgressDialog() {
    loadingViewController?.stopPresenting()
    loadingViewController = null
  }

  override fun onReportFileClicked(reportFile: ReportFile) {
    navigationController!!.pushController(ViewFullCrashLogController(context, reportFile))
  }

  override fun onFinished() {
    navigationController!!.popController()
  }
}