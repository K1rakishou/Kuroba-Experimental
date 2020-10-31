package com.github.k1rakishou.chan.ui.controller.crashlogs

import android.content.Context
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.StartActivityComponent
import com.github.k1rakishou.chan.ui.layout.crashlogs.CrashLog
import com.github.k1rakishou.chan.ui.layout.crashlogs.ViewFullCrashLogLayout

class ViewFullCrashLogController(
  context: Context,
  private val crashLog: CrashLog
) : Controller(context), ViewFullCrashLogLayout.ViewFullCrashLogLayoutCallbacks {

  override fun injectDependencies(component: StartActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()
    navigation.setTitle(crashLog.fileName)

    view = ViewFullCrashLogLayout(context, crashLog).apply {
      onCreate(this@ViewFullCrashLogController)
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    (view as ViewFullCrashLogLayout).onDestroy()
  }

  override fun onFinished() {
    navigationController!!.popController()
  }
}