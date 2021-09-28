package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.ui.layout.ReportProblemLayout
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.common.updatePaddings
import javax.inject.Inject

class ReportProblemController(context: Context)
  : Controller(context), ReportProblemLayout.ReportProblemControllerCallbacks, WindowInsetsListener {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private var loadingViewController: LoadingViewController? = null

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()
    navigation.setTitle(R.string.report_controller_report_an_error_problem)

    val reportProblemLayout = ReportProblemLayout(context).apply {
      onReady(this@ReportProblemController)
    }

    view = reportProblemLayout

    navigation
      .buildMenu(context)
      .withItem(
        ACTION_SEND_REPORT,
        R.drawable.ic_send_white_24dp,
        { reportProblemLayout.onSendReportClick() }
      )
      .build()

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
    (view as ReportProblemLayout).destroy()
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

  override fun onFinished() {
    this.navigationController!!.popController()
  }

  companion object {
    private const val ACTION_SEND_REPORT = 1
  }
}