package com.github.k1rakishou.chan.features.media_viewer.strip

import MediaViewerActionButton
import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.persist_state.PersistableChanState

class MediaViewerLeftActionStrip @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : MediaViewerActionStrip(context, attributeSet) {

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_viewer_left_action_strip, this)
    super.init()
  }

  override fun reorder() {
    val container = findViewById<LinearLayout>(R.id.media_viewer_actions_container)
    container.removeAllViews()

    val reorderableMediaViewerActions = PersistableChanState.reorderableMediaViewerActions.get()

    reorderableMediaViewerActions.mediaViewerActionButtons().forEach { reorderableMediaViewerAction ->
      when (reorderableMediaViewerAction) {
        MediaViewerActionButton.GoToPost -> container.addView(toolbarGoToPostButtonContainer)
        MediaViewerActionButton.Replies -> container.addView(toolbarPostRepliesButtonContainer)
        MediaViewerActionButton.Reload -> container.addView(toolbarReloadButtonContainer)
        MediaViewerActionButton.Download -> container.addView(toolbarDownloadButtonContainer)
        MediaViewerActionButton.Settings -> container.addView(toolbarOptionsButtonContainer)
      }
    }
  }

}