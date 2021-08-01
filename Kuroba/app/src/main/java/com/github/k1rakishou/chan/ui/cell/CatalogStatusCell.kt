package com.github.k1rakishou.chan.ui.cell

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.adapter.PostAdapter
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.ui.view.LoadView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen

class CatalogStatusCell @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
  private val loadView: LoadView

  private val progressView: FrameLayout
  private val errorView: LinearLayout

  private var _error: String? = null
  private var postAdapterCallback: PostAdapter.PostAdapterCallback? = null

  val isError: Boolean
    get() = _error != null

  init {
    AppModuleAndroidUtils.inflate(
      context,
      R.layout.cell_post_catalog_status,
      this,
      true
    )

    layoutParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      getDimen(R.dimen.cell_post_catalog_status_height)
    )

    loadView = findViewById<LoadView>(R.id.load_view)

    progressView = AppModuleAndroidUtils.inflate(
      context,
      R.layout.cell_post_catalog_status_progress,
      this,
      false
    ) as FrameLayout

    errorView = AppModuleAndroidUtils.inflate(
      context,
      R.layout.cell_post_catalog_status_error,
      this,
      false
    ) as LinearLayout

    setProgress()
  }

  fun setError(error: String?) {
    this._error = error

    if (error == null) {
      return
    }

    loadView.setView(errorView)

    val errorText = errorView.findViewById<ColorizableTextView>(R.id.error_text)
    val reloadButton = errorView.findViewById<ColorizableButton>(R.id.reload_button)

    errorText.setText(error)
    reloadButton.setOnClickListener {
      postAdapterCallback?.infiniteCatalogLoadPage()

      setProgress()
    }
  }

  fun setProgress() {
    _error = null

    loadView.setView(progressView)
  }

  fun setPostAdapterCallback(postAdapterCallback: PostAdapter.PostAdapterCallback) {
    this.postAdapterCallback = postAdapterCallback
  }

}