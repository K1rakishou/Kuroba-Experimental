package com.github.k1rakishou.chan.ui.controller.crashlogs

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.StartActivityComponent
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.layout.crashlogs.CrashLog
import com.github.k1rakishou.chan.ui.layout.crashlogs.ReviewCrashLogsLayout
import com.github.k1rakishou.chan.ui.layout.crashlogs.ReviewCrashLogsLayoutCallbacks

class ReviewCrashLogsController(context: Context) : Controller(context), ReviewCrashLogsLayoutCallbacks {
  private var loadingViewController: LoadingViewController? = null

  override fun injectDependencies(component: StartActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()
    navigation.setTitle(R.string.review_crashlogs_controller_title)

    view = ReviewCrashLogsLayout(context).apply { onCreate(this@ReviewCrashLogsController) }
  }

  override fun onDestroy() {
    super.onDestroy()

    (view as ReviewCrashLogsLayout).onDestroy()
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

  override fun onCrashLogClicked(crashLog: CrashLog) {
    navigationController!!.pushController(ViewFullCrashLogController(context, crashLog))
  }

  override fun onFinished() {
    navigationController!!.popController()
  }
}