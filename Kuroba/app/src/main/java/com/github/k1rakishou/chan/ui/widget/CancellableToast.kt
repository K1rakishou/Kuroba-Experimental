package com.github.k1rakishou.chan.ui.widget

import android.content.Context
import android.widget.Toast
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.BackgroundUtils
import java.lang.ref.WeakReference

class CancellableToast {
  private var toastRef: WeakReference<Toast> = WeakReference(null)

  fun showToast(context: Context, message: String) {
    showToast(context, message, Toast.LENGTH_SHORT)
  }

  fun showToast(context: Context, msgResId: Int) {
    showToast(context, msgResId, Toast.LENGTH_SHORT)
  }

  fun showToast(context: Context, msgResId: Int, duration: Int) {
    showToast(context, getString(msgResId), duration)
  }

  fun showToast(context: Context, message: String, duration: Int) {
    BackgroundUtils.ensureMainThread()

    toastRef.get()?.cancel()
    toastRef.clear()

    val newToast = Toast.makeText(context.applicationContext, message, duration)
      .apply { show() }

    toastRef = WeakReference(newToast)
  }

  fun cancel() {
    toastRef.get()?.cancel()
    toastRef.clear()
  }
}