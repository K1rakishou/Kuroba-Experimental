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
  private val loadedViews = mutableListOf<MediaView<*, ViewableMedia>>()
  private val viewableMediaList = mutableListWithCap<ViewableMedia>(32)

  fun setViewableMediaList(viewableMediaList: List<ViewableMedia>) {
    this.viewableMediaList.clear()
    this.viewableMediaList.addAll(viewableMediaList)

    notifyDataSetChanged()
  }

  fun onDestroy() {
    loadedViews.forEach { loadedView -> loadedView.onUnbind() }
    loadedViews.clear()
  }

  override fun getCount(): Int = viewableMediaList.size

  override fun getView(position: Int, parent: ViewGroup?): View {
    val mediaView = when (val viewableMedia = viewableMediaList[position]) {
      is ViewableMedia.Image -> {
        FullImageMediaView(context, viewableMedia, position, count).apply {
          val params = FullImageMediaView.FullImageMediaViewParams(viewableMedia.mediaLocation)
          startPreloading(params)
        }
      }
      is ViewableMedia.Gif -> {
        GifMediaView(context, viewableMedia, position, count).apply {
          val params = GifMediaView.GifMediaViewParams(viewableMedia.mediaLocation)
          startPreloading(params)
        }
      }
      is ViewableMedia.Video -> {
        VideoMediaView(context, viewableMedia, position, count).apply {
          val params = VideoMediaView.VideoMediaViewParams(viewableMedia.mediaLocation)
          startPreloading(params)
        }
      }
      is ViewableMedia.Unsupported -> {
        UnsupportedMediaView(context, viewableMedia, position, count).apply {
          val params = UnsupportedMediaView.UnsupportedMediaViewParams()
          startPreloading(params)
        }
      }
    }

    loadedViews.add(mediaView as MediaView<*, ViewableMedia>)
    Logger.d(TAG, "getView(position: ${position})")

    return mediaView
  }

  fun doBind(position: Int) {
    val viewableMedia = viewableMediaList[position]
    Logger.d(TAG, "doBind(position: ${position})")

    val view = loadedViews
      .firstOrNull { loadedView -> loadedView.viewableMedia == viewableMedia }
      ?: return

    when (viewableMedia) {
      is ViewableMedia.Image -> {
        val params = FullImageMediaView.FullImageMediaViewParams(viewableMedia.mediaLocation)
        (view as FullImageMediaView).onBind(params)
      }
      is ViewableMedia.Gif -> {
        val params = GifMediaView.GifMediaViewParams(viewableMedia.mediaLocation)
        (view as GifMediaView).onBind(params)
      }
      is ViewableMedia.Video -> {
        val params = VideoMediaView.VideoMediaViewParams(viewableMedia.mediaLocation)
        (view as VideoMediaView).onBind(params)
      }
      is ViewableMedia.Unsupported -> {
        val params = UnsupportedMediaView.UnsupportedMediaViewParams()
        (view as UnsupportedMediaView).onBind(params)
      }
    }

    loadedViews.forEach { loadedView ->
      if (loadedView.viewableMedia != view.viewableMedia) {
        view.onHide()
      }
    }
  }

  override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
    super.destroyItem(container, position, obj)

    (obj as MediaView<*, ViewableMedia>).onUnbind()
    loadedViews.remove(obj)
  }

  companion object {
    private const val TAG = "MediaViewerAdapter"
  }
}