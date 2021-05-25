package com.github.k1rakishou.chan.features.media_viewer

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.github.k1rakishou.chan.features.media_viewer.media_view.FullImageMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.GifMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaViewContract
import com.github.k1rakishou.chan.features.media_viewer.media_view.UnsupportedMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.VideoMediaView
import com.github.k1rakishou.chan.ui.view.ViewPagerAdapter
import com.github.k1rakishou.core_logger.Logger

class MediaViewerAdapter(
  private val context: Context,
  private val mediaViewContract: MediaViewContract,
  private val viewableMediaList: List<ViewableMedia>
) : ViewPagerAdapter() {
  private val loadedViews = mutableListOf<MediaView<ViewableMedia>>()
  private var firstUpdateHappened = false

  fun onDestroy() {
    Logger.d(TAG, "onDestroy()")

    loadedViews.forEach { loadedView ->
      loadedView.onUnbind()
    }

    loadedViews.clear()
  }

  override fun getCount(): Int = viewableMediaList.size

  @Suppress("UNCHECKED_CAST")
  override fun getView(position: Int, parent: ViewGroup?): View {
    val mediaView = when (val viewableMedia = viewableMediaList[position]) {
      is ViewableMedia.Image -> FullImageMediaView(context, mediaViewContract, viewableMedia, position, count)
      is ViewableMedia.Gif -> GifMediaView(context, viewableMedia, position, count)
      is ViewableMedia.Video -> VideoMediaView(context, viewableMedia, position, count)
      is ViewableMedia.Unsupported -> UnsupportedMediaView(context, viewableMedia, position, count)
    }

    mediaView.startPreloading()
    loadedViews.add(mediaView as MediaView<ViewableMedia>)

    return mediaView
  }

  fun doBind(position: Int) {
    val viewableMedia = viewableMediaList[position]
    Logger.d(TAG, "doBind(position: ${position})")

    val view = loadedViews
      .firstOrNull { loadedView -> loadedView.viewableMedia == viewableMedia }
      ?: return

    if (!view.shown) {
      view.onShow()
    }

    if (!view.bound) {
      view.onBind()
    }

    loadedViews.forEach { loadedView ->
      if (loadedView.viewableMedia != view.viewableMedia) {
        if (loadedView.shown) {
          loadedView.onHide()
        }
      }
    }

    Logger.d(TAG, "doBind(position: ${position}), loadedViewsCount=${loadedViews.size}, " +
      "boundCount=${loadedViews.count { it.bound }}, showCount=${loadedViews.count { it.shown }}")
  }

  override fun finishUpdate(container: ViewGroup) {
    super.finishUpdate(container)

    if (firstUpdateHappened) {
      return
    }

    loadedViews.forEach { loadedView ->
      if (!loadedView.shown) {
        loadedView.onShow()
      }

      if (!loadedView.bound) {
        loadedView.onBind()
      }
    }

    if (loadedViews.isNotEmpty()) {
      firstUpdateHappened = true
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
    super.destroyItem(container, position, obj)

    (obj as MediaView<ViewableMedia>).onUnbind()
    loadedViews.remove(obj)
    
    Logger.d(TAG, "destroyItem(position: ${position}), loadedViewsCount=${loadedViews.size}, " +
      "boundCount=${loadedViews.count { it.bound }}, showCount=${loadedViews.count { it.shown }}")
  }

  companion object {
    private const val TAG = "MediaViewerAdapter"
  }
}