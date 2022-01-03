package com.github.k1rakishou.chan.features.media_viewer

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.utils.AnimationUtils.fadeIn
import com.github.k1rakishou.chan.utils.AnimationUtils.fadeOut
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.setEnabledFast
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MediaViewerBottomActionStrip @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : ConstraintLayout(context, attributeSet) {

  @Inject
  lateinit var chanThreadManager: ChanThreadManager

  private val toolbarGoToPostButton: AppCompatImageButton
  private val toolbarReloadButton: AppCompatImageButton
  private val toolbarDownloadButton: AppCompatImageButton
  private val toolbarPostRepliesButton: AppCompatImageButton
  private val toolbarOptionsButton: AppCompatImageButton

  private val toolbarGoToPostButtonContainer: FrameLayout
  private val toolbarReloadButtonContainer: FrameLayout
  private val toolbarDownloadButtonContainer: FrameLayout
  private val toolbarPostRepliesButtonContainer: FrameLayout
  private val toolbarOptionsButtonContainer: FrameLayout
  private val repliesCountTextView: TextView

  private val scope = KurobaCoroutineScope()
  private val controllerViewModel by (context as ComponentActivity).viewModels<MediaViewerControllerViewModel>()

  private var mediaViewerStripCallbacks: MediaViewerBottomActionStripCallbacks? = null
  private var chanDescriptor: ChanDescriptor? = null
  private var currentViewableMedia: ViewableMedia? = null
  private var postRepliesProcessJob: Job? = null
  private var hideShowAnimation: ValueAnimator? = null

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_viewer_bottom_action_strip, this)

    toolbarGoToPostButton = findViewById(R.id.toolbar_go_to_post_button)
    toolbarReloadButton = findViewById(R.id.toolbar_reload_button)
    toolbarDownloadButton = findViewById(R.id.toolbar_download_button)
    toolbarPostRepliesButton = findViewById(R.id.toolbar_post_replies_button)
    toolbarOptionsButton = findViewById(R.id.toolbar_options_button)

    toolbarGoToPostButtonContainer = findViewById(R.id.toolbar_go_to_post_button_container)
    toolbarReloadButtonContainer = findViewById(R.id.toolbar_reload_button_container)
    toolbarDownloadButtonContainer = findViewById(R.id.toolbar_download_button_container)
    toolbarPostRepliesButtonContainer = findViewById(R.id.toolbar_post_replies_button_container)
    toolbarOptionsButtonContainer = findViewById(R.id.toolbar_options_button_container)
    repliesCountTextView = findViewById(R.id.replies_count_text)

    toolbarGoToPostButton.setEnabledFast(false)
    toolbarReloadButton.setEnabledFast(false)
    toolbarDownloadButton.setEnabledFast(false)
    toolbarPostRepliesButtonContainer.setVisibilityFast(View.VISIBLE)

    toolbarGoToPostButton.setOnClickListener {
      if (mediaViewerStripCallbacks == null || chanDescriptor == null) {
        return@setOnClickListener
      }

      val postDescriptor = currentViewableMedia?.viewableMediaMeta?.ownerPostDescriptor
        ?: return@setOnClickListener

      mediaViewerStripCallbacks?.onGoToPostMediaClick(currentViewableMedia!!, postDescriptor)
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

    toolbarOptionsButton.setOnClickListener { mediaViewerStripCallbacks?.onOptionsButtonClick() }
    toolbarPostRepliesButton.setOnClickListener {
      val postDescriptor = currentViewableMedia?.viewableMediaMeta?.ownerPostDescriptor
        ?: return@setOnClickListener

      mediaViewerStripCallbacks?.onShowRepliesButtonClick(postDescriptor)
    }

    scope.launch {
      controllerViewModel.mediaViewerOptions.collect { mediaViewerOptions ->
        updateToolbarStateFromViewOptions(mediaViewerOptions, chanDescriptor)
      }
    }

    setVisibilityFast(View.GONE)
  }

  fun markMediaAsDownloaded() {
    toolbarDownloadButton.setEnabledFast(false)
  }

  fun isDownloadAllowed(): Boolean = toolbarDownloadButton.isEnabled

  fun downloadMedia() {
    fireOnDownloadButtonClickCallback(isLongClick = false)
  }

  fun attach(
    chanDescriptor: ChanDescriptor?,
    viewableMedia: ViewableMedia, callbacks: MediaViewerBottomActionStripCallbacks
  ) {
    if (this.mediaViewerStripCallbacks != null) {
      return
    }
    
    this.chanDescriptor = chanDescriptor
    this.currentViewableMedia = viewableMedia
    this.mediaViewerStripCallbacks = callbacks

    postRepliesProcessJob?.cancel()
    postRepliesProcessJob = null

    toolbarReloadButton.setEnabledFast(viewableMedia.canReloadMedia())
    toolbarDownloadButton.setEnabledFast(viewableMedia.canMediaBeDownloaded())
    toolbarGoToPostButton.setEnabledFast(viewableMedia.hasPostDescriptor())

    updateToolbarStateFromViewOptions(controllerViewModel.mediaViewerOptions.value, chanDescriptor)

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      // We need to offload this to a background thread because it may become a reason of micro-freezes
      postRepliesProcessJob = scope.launch(Dispatchers.Default) {
        processPostRepliesButton(viewableMedia)
      }
    } else {
      toolbarPostRepliesButtonContainer.setVisibilityFast(View.GONE)
    }
  }

  fun detach() {
    postRepliesProcessJob?.cancel()
    postRepliesProcessJob = null

    this.mediaViewerStripCallbacks = null
    this.chanDescriptor = null
  }

  fun onDestroy() {
    this.mediaViewerStripCallbacks = null
    this.currentViewableMedia = null

    postRepliesProcessJob?.cancel()
    postRepliesProcessJob = null
    scope.cancelChildren()
  }

  private suspend fun processPostRepliesButton(viewableMedia: ViewableMedia) {
    BackgroundUtils.ensureBackgroundThread()

    val repliesFromCount = viewableMedia.postDescriptor?.let { postDescriptor ->
      return@let chanThreadManager.getPost(postDescriptor)?.repliesFromCount
    } ?: 0

    withContext(Dispatchers.Main) {
      BackgroundUtils.ensureMainThread()

      toolbarPostRepliesButtonContainer.setVisibilityFast(VISIBLE)

      if (repliesFromCount > 0) {
        val repliesFromCountString = if (repliesFromCount > 99) {
          "99+"
        } else {
          repliesFromCount.toString()
        }

        repliesCountTextView.setVisibilityFast(VISIBLE)
        repliesCountTextView.text = repliesFromCountString
      } else {
        repliesCountTextView.setVisibilityFast(GONE)
      }
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

    toolbarGoToPostButtonContainer.setVisibilityFast(visibility)
  }

  private fun fireOnReloadButtonClickCallback() {
    if (!toolbarReloadButton.isEnabled) {
      return
    }

    scope.launch {
      toolbarReloadButton.setEnabledFast(false)

      try {
        mediaViewerStripCallbacks?.reloadMedia()
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
        val startedDownloading = mediaViewerStripCallbacks?.downloadMedia(isLongClick = isLongClick)
          ?: false

        // Only enable the button back if we didn't start the image downloading
        toolbarDownloadButton.setEnabledFast(startedDownloading.not())
      } catch (error: Throwable) {
        toolbarDownloadButton.setEnabledFast(true)
      }
    }
  }

  fun updateWithViewableMedia(pagerPosition: Int, totalPageItemsCount: Int, viewableMedia: ViewableMedia) {
    // no-op
  }

  fun hide() {
    hideShowAnimation = fadeOut(
      duration = MediaViewerToolbar.ANIMATION_DURATION_MS,
      animator = hideShowAnimation,
      onEnd = { hideShowAnimation = null }
    )
  }

  fun show() {
    hideShowAnimation = fadeIn(
      duration = MediaViewerToolbar.ANIMATION_DURATION_MS,
      animator = hideShowAnimation,
      onEnd = { hideShowAnimation = null }
    )
  }

  interface MediaViewerBottomActionStripCallbacks {
    suspend fun reloadMedia()
    suspend fun downloadMedia(isLongClick: Boolean): Boolean
    fun onOptionsButtonClick()
    fun onShowRepliesButtonClick(postDescriptor: PostDescriptor)
    fun onGoToPostMediaClick(viewableMedia: ViewableMedia, postDescriptor: PostDescriptor)
  }

}