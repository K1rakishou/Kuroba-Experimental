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
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString

class CatalogStatusCell @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
  private val loadView: LoadView

  private val progressView: LinearLayout
  private val errorView: LinearLayout
  private val catalogEndReachedView: LinearLayout

  private var _error: String? = null
  private var postAdapterCallback: PostAdapter.PostAdapterCallback? = null
  private var catalogPage: Int = 1
  private var endReached = false

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
    ) as LinearLayout

    errorView = AppModuleAndroidUtils.inflate(
      context,
      R.layout.cell_post_catalog_status_error,
      this,
      false
    ) as LinearLayout

    catalogEndReachedView = AppModuleAndroidUtils.inflate(
      context,
      R.layout.cell_post_catalog_status_end_reached,
      this,
      false
    ) as LinearLayout

    setProgress(catalogPage)
  }

  fun setPostAdapterCallback(postAdapterCallback: PostAdapter.PostAdapterCallback) {
    this.postAdapterCallback = postAdapterCallback
  }

  fun setError(error: String?) {
    if (this._error == error) {
      return
    }

    this._error = error
    endReached = false

    if (error == null) {
      return
    }

    loadView.setView(errorView)

    val errorText = errorView.findViewById<ColorizableTextView>(R.id.error_text)
    val reloadButton = errorView.findViewById<ColorizableButton>(R.id.reload_button)

    errorText.setText(error)
    reloadButton.setOnClickListener {
      postAdapterCallback?.loadCatalogPage()

      setProgress(catalogPage)
    }
  }

  fun setProgress(nextPage: Int) {
    if (catalogPage == nextPage) {
      return
    }

    _error = null
    endReached = false
    catalogPage = nextPage

    loadView.setView(progressView)

    val indicator = progressView.findViewById<ColorizableTextView>(R.id.next_page_indicator)
    indicator.text = getString(R.string.catalog_load_page, nextPage)
  }

  fun onCatalogEndReached() {
    if (endReached) {
      return
    }

    endReached = true
    loadView.setView(catalogEndReachedView)
  }

}