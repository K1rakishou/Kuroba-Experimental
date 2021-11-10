package com.github.k1rakishou.chan.features.media_viewer

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerGoToImagePostHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerOpenThreadHelper
import com.github.k1rakishou.chan.ui.widget.SimpleAnimatorListener
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setEnabledFast
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

class MediaViewerToolbar @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : LinearLayout(context, attributeSet), WindowInsetsListener {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var mediaViewerGoToImagePostHelper: MediaViewerGoToImagePostHelper
  @Inject
  lateinit var mediaViewerOpenThreadHelper: MediaViewerOpenThreadHelper

  private val toolbarViewModel by (context as ComponentActivity).viewModels<MediaViewerToolbarViewModel>()
  private val controllerViewModel by (context as ComponentActivity).viewModels<MediaViewerControllerViewModel>()

  private val toolbarCloseButton: AppCompatImageButton
  private val toolbarTitle: TextView
  private val toolbarSubTitle: TextView
  private val toolbarGoToPostButton: AppCompatImageButton
  private val toolbarReloadButton: AppCompatImageButton
  private val toolbarDownloadButton: AppCompatImageButton
  private val toolbarOptionsButton: AppCompatImageButton

  private val scope = KurobaCoroutineScope()

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
    toolbarGoToPostButton = findViewById(R.id.toolbar_go_to_post_button)
    toolbarReloadButton = findViewById(R.id.toolbar_reload_button)
    toolbarDownloadButton = findViewById(R.id.toolbar_download_button)
    toolbarOptionsButton = findViewById(R.id.toolbar_options_button)

    toolbarGoToPostButton.setEnabledFast(false)
    toolbarReloadButton.setEnabledFast(false)
    toolbarDownloadButton.setEnabledFast(false)

    toolbarCloseButton.setOnClickListener { mediaViewerToolbarCallbacks?.onCloseButtonClick() }
    toolbarGoToPostButton.setOnClickListener {
      if (mediaViewerToolbarCallbacks == null || chanDescriptor == null) {
        return@setOnClickListener
      }

      val postDescriptor = currentViewableMedia?.viewableMediaMeta?.ownerPostDescriptor
        ?: return@setOnClickListener

      val mediaViewerOptions = controllerViewModel.mediaViewerOptions.value
      var closeMediaViewer = false

      if (mediaViewerOptions.mediaViewerOpenedFromAlbum) {
        val mediaLocation = currentViewableMedia?.mediaLocation
          ?: return@setOnClickListener

        if (mediaViewerGoToImagePostHelper.tryGoToPost(chanDescriptor, postDescriptor, mediaLocation)) {
          closeMediaViewer = true
        }
      } else if (chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
        if (mediaViewerOpenThreadHelper.tryToOpenThread(postDescriptor)) {
          closeMediaViewer = true
        }
      }

      if (closeMediaViewer) {
        mediaViewerToolbarCallbacks?.onCloseButtonClick()
      }
    }

    toolbarReloadButton.setOnClickListener {
      fireOnReloadButtonClickCallback()
    }

    toolbarDownloadButton.setOnClickListener {
      fireOnDownloadButtonClickCallback(isLongClick = false)
    }

    toolbarDownloadButton.setOnLongClickListener {
      fireOnDownloadButtonClickCallback(isLongClick = true)
      return@setOnLongClickListener true
    }

    toolbarOptionsButton.setOnClickListener { mediaViewerToolbarCallbacks?.onOptionsButtonClick() }

    scope.launch {
      controllerViewModel.mediaViewerOptions.collect { mediaViewerOptions ->
        updateToolbarStateFromViewOptions(mediaViewerOptions, chanDescriptor)
      }
    }

    doOnPreDraw { onInsetsChanged() }
    setVisibilityFast(View.GONE)
  }

  fun attach(chanDescriptor: ChanDescriptor?, viewableMedia: ViewableMedia, callbacks: MediaViewerToolbarCallbacks) {
    check(this.mediaViewerToolbarCallbacks == null) { "Callbacks are already set!" }
    this.chanDescriptor = chanDescriptor
    this.currentViewableMedia = viewableMedia
    this.mediaViewerToolbarCallbacks = callbacks

    updateToolbarStateFromViewOptions(controllerViewModel.mediaViewerOptions.value, chanDescriptor)

    val toolbarState = toolbarViewModel.restore(viewableMedia.mediaLocation)
    if (toolbarState != null) {
      toolbarGoToPostButton.setEnabledFast(toolbarState.goToPostButtonEnabled)
      toolbarReloadButton.setEnabledFast(toolbarState.reloadButtonEnabled)
      toolbarDownloadButton.setEnabledFast(toolbarState.downloadButtonEnabled)
      setVisibilityFast(if (toolbarState.toolbarShown) View.VISIBLE else View.GONE)

      return
    }

    toolbarReloadButton.setEnabledFast(viewableMedia.canReloadMedia())
    toolbarDownloadButton.setEnabledFast(viewableMedia.canMediaBeDownloaded())
    toolbarGoToPostButton.setEnabledFast(viewableMedia.canGoToMediaPost())
  }

  fun detach() {
    currentViewableMedia?.let { viewableMedia ->
      val toolbarState = MediaViewerToolbarViewModel.ToolbarState(
        goToPostButtonEnabled = toolbarGoToPostButton.isEnabled,
        reloadButtonEnabled = toolbarReloadButton.isEnabled,
        downloadButtonEnabled = toolbarDownloadButton.isEnabled,
        toolbarShown = visibility == View.VISIBLE
      )

      toolbarViewModel.store(viewableMedia.mediaLocation, toolbarState)
    }

    this.mediaViewerToolbarCallbacks = null
    this.chanDescriptor = null
  }

  fun onCreate() {
    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  fun onDestroy() {
    this.mediaViewerToolbarCallbacks = null
    this.currentViewableMedia = null
    scope.cancelChildren()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  fun isDownloadAllowed(): Boolean = toolbarDownloadButton.isEnabled

  fun downloadMedia() {
    fireOnDownloadButtonClickCallback(isLongClick = false)
  }

  fun updateWithViewableMedia(currentIndex: Int, totalMediaCount: Int, viewableMedia: ViewableMedia) {
    updateToolbarTitleAndSubtitle(currentIndex, totalMediaCount, viewableMedia)
  }

  fun hideToolbar() {
    if (hideShowAnimation != null) {
      hideShowAnimation?.end()
      hideShowAnimation = null
    }

    if (visibility == View.GONE) {
      return
    }

    hideShowAnimation = ValueAnimator.ofFloat(1f, 0f).apply {
      duration = ANIMATION_DURATION_MS
      addUpdateListener { animation ->
        alpha = animation.animatedValue as Float
      }
      addListener(object : SimpleAnimatorListener() {
        override fun onAnimationStart(animation: Animator?) {
          alpha = 1f
        }

        override fun onAnimationEnd(animation: Animator?) {
          alpha = 0f
          setVisibilityFast(View.GONE)
          hideShowAnimation = null
          toolbarViewModel.updateToolbarVisibilityGlobal(nowVisible = false)
        }
      })
      start()
    }
  }

  fun showToolbar() {
    if (hideShowAnimation != null) {
      hideShowAnimation?.end()
      hideShowAnimation = null
    }

    if (visibility == View.VISIBLE) {
      return
    }

    hideShowAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = ANIMATION_DURATION_MS
      addUpdateListener { animation ->
        alpha = animation.animatedValue as Float
      }
      addListener(object : SimpleAnimatorListener() {
        override fun onAnimationStart(animation: Animator?) {
          alpha = 0f
        }

        override fun onAnimationEnd(animation: Animator?) {
          alpha = 1f
          setVisibilityFast(View.VISIBLE)
          hideShowAnimation = null
          toolbarViewModel.updateToolbarVisibilityGlobal(nowVisible = true)
        }
      })
      start()
    }
  }

  fun markMediaAsDownloaded() {
    toolbarDownloadButton.setEnabledFast(false)
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

  private fun updateToolbarStateFromViewOptions(
    mediaViewerOptions: MediaViewerOptions,
    chanDescriptor: ChanDescriptor?
  ) {
    val visibility = if (mediaViewerOptions.mediaViewerOpenedFromAlbum || chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
      View.VISIBLE
    } else {
      View.GONE
    }

    toolbarGoToPostButton.setVisibilityFast(visibility)
  }

  private fun fireOnReloadButtonClickCallback() {
    if (!toolbarReloadButton.isEnabled) {
      return
    }

    scope.launch {
      toolbarReloadButton.setEnabledFast(false)

      try {
        mediaViewerToolbarCallbacks?.reloadMedia()
      } finally {
        toolbarReloadButton.setEnabledFast(true)
      }
    }
  }

  private fun fireOnDownloadButtonClickCallback(isLongClick: Boolean) {
    if (!toolbarDownloadButton.isEnabled) {
      return
    }

    scope.launch {
      toolbarDownloadButton.setEnabledFast(false)

      try {
        val startedDownloading = mediaViewerToolbarCallbacks?.downloadMedia(isLongClick = isLongClick)
          ?: false

        // Only enable the button back if we didn't start the image downloading
        toolbarDownloadButton.setEnabledFast(startedDownloading.not())
      } catch (error: Throwable) {
        toolbarDownloadButton.setEnabledFast(true)
      }
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

      if (viewableMediaMeta.mediaWidth != null && viewableMediaMeta.mediaHeight != null) {
        append(", ")

        append(viewableMediaMeta.mediaWidth)
        append("x")
        append(viewableMediaMeta.mediaHeight)
      }

      viewableMediaMeta.mediaSize?.let { mediaSize ->
        append(", ")
        append(ChanPostUtils.getReadableFileSize(mediaSize))
      }
    }
  }

  interface MediaViewerToolbarCallbacks {
    fun onCloseButtonClick()
    suspend fun reloadMedia()
    suspend fun downloadMedia(isLongClick: Boolean): Boolean
    fun onOptionsButtonClick()
  }

  companion object {
    const val ANIMATION_DURATION_MS = 200L
  }

}