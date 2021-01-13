package com.github.k1rakishou.chan.core.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.github.k1rakishou.PersistableChanState
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger

class SelectedFilePickerBroadcastReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context?, intent: Intent?) {
    if (context == null || intent == null) {
      return
    }

    if (!AndroidUtils.isAndroidL_MR1()) {
      return
    }

    val component = intent.getParcelableExtra<ComponentName>(Intent.EXTRA_CHOSEN_COMPONENT)
      ?: return

    Logger.d(TAG, "Setting lastRememberedFilePicker to " +
      "(packageName=${component.packageName}, className=${component.className})")

    PersistableChanState.lastRememberedFilePicker.set(component.packageName)
  }

  companion object {
    private const val TAG = "SelectedFilePickerBroadcastReceiver"
  }
}