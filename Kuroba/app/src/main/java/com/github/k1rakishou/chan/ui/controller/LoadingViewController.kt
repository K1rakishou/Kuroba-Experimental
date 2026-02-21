package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.view.View
import android.widget.ProgressBar
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.controller.base.BaseFloatingController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView

class LoadingViewController : BaseFloatingController {
  private lateinit var loadingControllerTitle: ColorizableTextView
  private lateinit var loadingControllerMessage: ColorizableTextView
  private lateinit var cancelButton: ColorizableBarButton
  private lateinit var progressBar: ProgressBar

  private var _title: String
  private var _indeterminate: Boolean
  private var _cancelAllowed = false
  private var _cancellationFunc: Function0<Unit>? = null

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun getLayoutId(): Int {
    return R.layout.controller_loading_view
  }

  constructor(context: Context, indeterminate: Boolean) : super(context) {
    _indeterminate = indeterminate
    _title = appResources.string(R.string.doing_heavy_lifting_please_wait)
  }

  constructor(context: Context, indeterminate: Boolean, title: String) : super(context) {
    _indeterminate = indeterminate
    _title = title
  }

  override fun onCreate() {
    super.onCreate()

    loadingControllerTitle = view.findViewById<ColorizableTextView>(R.id.loading_controller_title)
    progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
    cancelButton = view.findViewById<ColorizableBarButton>(R.id.loading_controller_cancel_button)

    loadingControllerTitle.text = _title
    loadingControllerMessage = view.findViewById<ColorizableTextView>(R.id.loading_controller_message)

    if (_cancelAllowed) {
      cancelButton.visibility = View.VISIBLE
    } else {
      cancelButton.visibility = View.GONE
    }

    cancelButton.setOnClickListener {
      if (cancelButton.visibility != View.VISIBLE || !_cancelAllowed) {
        return@setOnClickListener
      }

      if (_cancellationFunc != null) {
        _cancellationFunc?.invoke()
        _cancellationFunc = null
      }

      pop()
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    _cancellationFunc = null
  }

  fun enableCancellation(cancellationFunc: Function0<Unit>) {
    _cancellationFunc = cancellationFunc
    _cancelAllowed = true
  }

  // Disable the back button for this controller unless otherwise requested by the above
  override fun onBack(): Boolean {
    if (_cancelAllowed) {
      if (_cancellationFunc != null) {
        _cancellationFunc?.invoke()
        _cancellationFunc = null
      }

      pop()
    }

    return true
  }

  /**
   * Shows a progress bar with percentage in the center (cannot be used with indeterminate)
   */
  fun updateProgress(percent: Int) {
    if (_indeterminate) {
      return
    }

    loadingControllerMessage.visibility = View.VISIBLE
    progressBar.visibility = View.VISIBLE
    loadingControllerMessage.text = if (percent > 0) {
      percent.toString()
    } else {
      "0"
    }
  }

  /**
   * Hide a progress bar and instead of percentage any text may be shown
   * (cannot be used with indeterminate)
   */
  fun updateWithText(text: String?) {
    if (_indeterminate) {
      return
    }

    loadingControllerMessage.visibility = View.VISIBLE
    progressBar.visibility = View.GONE
    loadingControllerMessage.text = text
  }
}
