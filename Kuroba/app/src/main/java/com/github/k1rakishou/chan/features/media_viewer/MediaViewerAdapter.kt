package com.github.k1rakishou.chan.features.media_viewer

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.github.k1rakishou.chan.features.media_viewer.media_view.FullImageMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.GifMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.MediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.UnsupportedMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.VideoMediaView
import com.github.k1rakishou.chan.ui.view.ViewPagerAdapter
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger

class MediaViewerAdapter(
  private val context: Context,
) : ViewPagerAdapter() {
  private val loadedViews = mutableListOf<MediaView<ViewableMedia>>()
  private val viewableMediaList = mutableListWithCap<ViewableMedia>(32)

  fun setViewableMediaList(viewableMediaList: List<ViewableMedia>) {
    this.viewableMediaList.clear()
    this.viewableMediaList.addAll(viewableMediaList)

    notifyDataSetChanged()
  }

  fun onDestroy() {
    Logger.d(TAG, "onDestroy()")

    loadedViews.forEach { loadedView ->
      if (loadedView.bound) {
        loadedView.onUnbind()
      }
    }

    loadedViews.clear()
  }

  override fun getCount(): Int = viewableMediaList.size

  override fun getView(position: Int, parent: ViewGroup?): View {
    val mediaView = when (val viewableMedia = viewableMediaList[position]) {
      is ViewableMedia.Image -> {
        FullImageMediaView(context, viewableMedia, position, count).apply {
          startPreloading()
        }
      }
      is ViewableMedia.Gif -> {
        GifMediaView(context, viewableMedia, position, count).apply {
          startPreloading()
        }
      }
      is ViewableMedia.Video -> {
        VideoMediaView(context, viewableMedia, position, count).apply {
          startPreloading()
        }
      }
      is ViewableMedia.Unsupported -> {
        UnsupportedMediaView(context, viewableMedia, position, count).apply {
          startPreloading()
        }
      }
    }

    loadedViews.add(mediaView as MediaView<ViewableMedia>)
    Logger.d(TAG, "getView(position: ${position}), loadedViewsCount=${loadedViews.size}")

    return mediaView
  }

  fun doBind(position: Int) {
    val viewableMedia = viewableMediaList[position]
    Logger.d(TAG, "doBind(position: ${position})")

    val view = loadedViews
      .firstOrNull { loadedView -> loadedView.viewableMedia == viewableMedia }
      ?: return

    when (viewableMedia) {
      is ViewableMedia.Image -> (view as FullImageMediaView).onBind()
      is ViewableMedia.Gif -> (view as GifMediaView).onBind()
      is ViewableMedia.Video -> (view as VideoMediaView).onBind()
      is ViewableMedia.Unsupported -> (view as UnsupportedMediaView).onBind()
    }

    loadedViews.forEach { loadedView ->
      if (loadedView.viewableMedia != view.viewableMedia) {
        view.onHide()
      }
    }
  }

  override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
    super.destroyItem(container, position, obj)

    (obj as MediaView<ViewableMedia>).let { mediaView ->
      if (mediaView.bound) {
        mediaView.onUnbind()
      }
    }

    loadedViews.remove(obj)
    Logger.d(TAG, "destroyItem(position: ${position}), loadedViewsCount=${loadedViews.size}")
  }

  companion object {
    private const val TAG = "MediaViewerAdapter"
  }
}