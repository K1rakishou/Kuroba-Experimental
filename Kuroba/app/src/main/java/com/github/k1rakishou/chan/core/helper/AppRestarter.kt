package com.github.k1rakishou.chan.core.helper

import androidx.appcompat.app.AppCompatActivity
import com.github.k1rakishou.common.removeIfKt
import kotlin.system.exitProcess

class AppRestarter {
  private val activities = ArrayList<AppCompatActivity>()

  fun attachActivity(activity: AppCompatActivity) {
    this.activities += activity
  }

  fun detachActivity(activity: AppCompatActivity) {
    this.activities.removeIfKt { it === activity }
  }

  fun restart() {
    activities.firstOrNull()?.let { componentActivity ->
      val intent = componentActivity.packageManager.getLaunchIntentForPackage(componentActivity.packageName)
        ?: return@let

      componentActivity.finishAffinity()
      componentActivity.startActivity(intent)
      exitProcess(0)
    }
  }

}