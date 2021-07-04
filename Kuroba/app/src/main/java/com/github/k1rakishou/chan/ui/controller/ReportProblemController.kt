package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.ui.layout.ReportProblemLayout
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

    view = ReportProblemLayout(context).apply {
      onReady(this@ReportProblemController)
    }

    onInsetsChanged()
    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  override fun onInsetsChanged() {
    if (ChanSettings.isSplitLayoutMode()) {
      view.updatePaddings(bottom = globalWindowInsetsManager.bottom())
    }
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
}