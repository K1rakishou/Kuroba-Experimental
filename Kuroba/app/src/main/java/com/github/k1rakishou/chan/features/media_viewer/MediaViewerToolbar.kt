package com.github.k1rakishou.chan.features.media_viewer

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.utils.AnimationUtils.fadeIn
import com.github.k1rakishou.chan.utils.AnimationUtils.fadeOut
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.util.ChanPostUtils
import java.util.*
import javax.inject.Inject

class MediaViewerToolbar @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : LinearLayout(context, attributeSet), WindowInsetsListener {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val toolbarCloseButton: AppCompatImageButton
  private val toolbarTitle: TextView
  private val toolbarSubTitle: TextView

  private var mediaViewerToolbarCallbacks: MediaViewerToolbarCallbacks? = null
  private var chanDescriptor: ChanDescriptor? = null
  private var currentViewableMedia: ViewableMedia? = null
  private var hideShowAnimation: ValueAnimator? = null

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_toolbar, this)
    setBackgroundColor(0xCC000000L.toInt())

    toolbarCloseButton = findViewById(R.id.toolbar_close_button)
    toolbarTitle = findViewById(R.id.toolbar_title)
    toolbarSubTitle = findViewById(R.id.toolbar_subtitle)
    toolbarCloseButton.setOnClickListener { mediaViewerToolbarCallbacks?.onCloseButtonClick() }

    doOnPreDraw { onInsetsChanged() }
    setVisibilityFast(View.GONE)
  }

  fun attach(
    chanDescriptor: ChanDescriptor?,
    viewableMedia: ViewableMedia,
    callbacks: MediaViewerToolbarCallbacks
  ) {
    if (this.mediaViewerToolbarCallbacks != null) {
      return
    }

    this.chanDescriptor = chanDescriptor
    this.currentViewableMedia = viewableMedia
    this.mediaViewerToolbarCallbacks = callbacks
  }

  fun detach() {
    this.mediaViewerToolbarCallbacks = null
    this.chanDescriptor = null
  }

  fun onCreate() {
    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  fun onDestroy() {
    this.mediaViewerToolbarCallbacks = null
    this.currentViewableMedia = null

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  fun updateWithViewableMedia(currentIndex: Int, totalMediaCount: Int, viewableMedia: ViewableMedia) {
    updateToolbarTitleAndSubtitle(currentIndex, totalMediaCount, viewableMedia)
  }

  fun hide() {
    hideShowAnimation = fadeOut(
      duration = ANIMATION_DURATION_MS,
      animator = hideShowAnimation,
      onEnd = { hideShowAnimation = null }
    )
  }

  fun show() {
    hideShowAnimation = fadeIn(
      duration = ANIMATION_DURATION_MS,
      animator = hideShowAnimation,
      onEnd = { hideShowAnimation = null }
    )
  }

  fun toolbarHeight(): Int = height

  override fun onInsetsChanged() {
    val topPadding = if (ChanSettings.mediaViewerDrawBehindNotch.get()) {
      globalWindowInsetsManager.top()
    } else {
      0
    }

    updatePaddings(
      left = globalWindowInsetsManager.left(),
      right = globalWindowInsetsManager.right(),
      top = topPadding
    )

    updateLayoutParams<ViewGroup.LayoutParams> {
      height = AppModuleAndroidUtils.getDimen(R.dimen.toolbar_height) + topPadding
    }
  }

  private fun updateToolbarTitleAndSubtitle(currentIndex: Int, totalMediaCount: Int, viewableMedia: ViewableMedia) {
    val viewableMediaMeta = viewableMedia.viewableMediaMeta

    val mediaName = viewableMediaMeta.originalMediaName
      ?: viewableMediaMeta.serverMediaName

    if (mediaName.isNotNullNorEmpty()) {
      toolbarTitle.text = mediaName
    }

    toolbarSubTitle.text = buildString {
      append((currentIndex + 1))
      append("/")
      append(totalMediaCount)

      if (viewableMediaMeta.extension.isNotNullNorEmpty()) {
        append(", ")
        append(viewableMediaMeta.extension.uppercase(Locale.ENGLISH))
      }

      val mediaWidth = viewableMediaMeta.mediaWidth
      val mediaHeight = viewableMediaMeta.mediaHeight

      if (mediaWidth != null && mediaHeight != null && (mediaWidth > 0 || mediaHeight > 0)) {
        append(", ")

        append(viewableMediaMeta.mediaWidth)
        append("x")
        append(viewableMediaMeta.mediaHeight)
      }

      val mediaSize = viewableMediaMeta.mediaSize
        ?.takeIf { mediaSize -> mediaSize > 0 }
        ?: viewableMediaMeta.mediaOnDiskSize

      if (mediaSize != null) {
        append(", ")
        append(ChanPostUtils.getReadableFileSize(mediaSize))
      }
    }
  }

  interface MediaViewerToolbarCallbacks {
    fun onCloseButtonClick()
  }

  companion object {
    const val ANIMATION_DURATION_MS = 200L
  }

}