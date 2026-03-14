package com.github.k1rakishou.chan.core.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettings
import javax.inject.Inject

class SelectedFilePickerBroadcastReceiver : BroadcastReceiver() {

  @Inject
  lateinit var kurobaSettings: KurobaSettings

  init {
    Chan.getComponent()
      .inject(this)
  }

  override fun onReceive(context: Context?, intent: Intent?) {
    if (context == null || intent == null) {
      return
    }

    val component = intent.getParcelableExtra<ComponentName>(Intent.EXTRA_CHOSEN_COMPONENT)
    if (component == null) {
      Logger.d(TAG, "component == null")
      return
    }

    Logger.d(TAG, "Setting lastRememberedFilePicker to " +
      "(packageName=${component.packageName}, className=${component.className})")

    kurobaSettings.internal.lastRememberedFilePicker.writeBlocking(component.packageName)
  }

  companion object {
    private const val TAG = "SelectedFilePickerBroadcastReceiver"
  }
}