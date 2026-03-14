package com.github.k1rakishou.chan.features.view.media.strip

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.v2.parameters.ReorderableMediaViewerActions

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

    val reorderableMediaViewerActions = kurobaSettings.internal.reorderableMediaViewerActions.readBlocking()

    reorderableMediaViewerActions.mediaViewerActionButtons().forEach { reorderableMediaViewerAction ->
      when (reorderableMediaViewerAction) {
        ReorderableMediaViewerActions.MediaViewerActionButton.GoToPost -> {
          container.addView(toolbarGoToPostButtonContainer)
        }
        ReorderableMediaViewerActions.MediaViewerActionButton.Replies -> {
          container.addView(toolbarPostRepliesButtonContainer)
        }
        ReorderableMediaViewerActions.MediaViewerActionButton.Reload -> {
          container.addView(toolbarReloadButtonContainer)
        }
        ReorderableMediaViewerActions.MediaViewerActionButton.Download -> {
          container.addView(toolbarDownloadButtonContainer)
        }
        ReorderableMediaViewerActions.MediaViewerActionButton.Settings -> {
          container.addView(toolbarOptionsButtonContainer)
        }
      }
    }
  }

}