package com.github.k1rakishou.chan.ui.layout

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import androidx.core.view.GravityCompat
import androidx.core.view.updateLayoutParams
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.ui.view.KurobaBottomNavigationView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.TimeUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.findChild
import com.github.k1rakishou.common.updateMargins
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView
import javax.inject.Inject
import kotlin.random.Random

class MrSkeletonLayout @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : FrameLayout(context, attributeSet, defAttrStyle), WindowInsetsListener {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var cacheHandler: CacheHandler

  private val scope = KurobaCoroutineScope()

  init {
    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(getContext())
        .inject(this)
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    globalWindowInsetsManager.addInsetsUpdatesListener(this)

    if (TimeUtils.isHalloweenToday()) {
      scope.launch { playRandomGif() }
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    scope.cancelChildren()
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    getGifView()?.setImageDrawable(null)
  }

  override fun onInsetsChanged() {
    updateGifViewMargins()
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  private suspend fun playRandomGif() {
    val skeletonUrl = skeletons.random(random)

    val result = imageLoaderV2.loadFromNetworkSuspend(
      context = context,
      url = skeletonUrl,
      cacheFileType = CacheFileType.PostMediaFull,
      imageSize = ImageLoaderV2.ImageSize.Unspecified,
      transformations = emptyList()
    )

    if (result is ModularResult.Error) {
      Logger.e(TAG, "attachGifViewIfNeeded() error: ${result.error.errorMessageOrClassName()}")

      return
    }

    val downloadedGifFile = withContext(Dispatchers.IO) {
      return@withContext cacheHandler.getCacheFileOrNull(
        cacheFileType = CacheFileType.PostMediaFull,
        url = skeletonUrl
      )
    }
    if (downloadedGifFile == null) {
      Logger.e(TAG, "attachGifViewIfNeeded() downloadedGifFile == null, skeletonUrl=${skeletonUrl}")

      return
    }

    var gifView = getGifView()
    if (gifView == null) {
      gifView = GifImageView(context)
      addView(gifView, LayoutParams(GIF_VIEW_SIZE, GIF_VIEW_SIZE))
    }

    updateGifViewMargins()
    gifView.setImageDrawable(GifDrawable(downloadedGifFile))

    gifView.updateLayoutParams<FrameLayout.LayoutParams> {
      gravity = when (random.nextInt(0, 5)) {
        0 -> GravityCompat.START or Gravity.TOP
        1 -> GravityCompat.END or Gravity.TOP
        2 -> GravityCompat.START or Gravity.BOTTOM
        3 -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        4 -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        else -> GravityCompat.END or Gravity.BOTTOM
      }
    }
  }

  private fun updateGifViewMargins() {
    var bottomNavViewHeight = getDimen(R.dimen.navigation_view_size)
    if (!KurobaBottomNavigationView.isBottomNavViewEnabled()) {
      bottomNavViewHeight = 0
    }

    val toolbarViewHeight = getDimen(R.dimen.toolbar_height)

    getGifView()?.updateMargins(
      top = globalWindowInsetsManager.top() + toolbarViewHeight,
      bottom = globalWindowInsetsManager.bottom() + bottomNavViewHeight
    )
  }

  private fun getGifView(): GifImageView? {
    return findChild { view -> view is GifImageView } as GifImageView?
  }

  companion object {
    private const val TAG = "MrSkeletonLayout"

    private val random = Random(System.currentTimeMillis())
    private val GIF_VIEW_SIZE = dp(128f)

    private val skeletons = listOf(
      "https://s.4cdn.org/image/skeletons/1.gif",
      "https://s.4cdn.org/image/skeletons/2.gif",
      "https://s.4cdn.org/image/skeletons/3.gif",
      "https://s.4cdn.org/image/skeletons/4.gif",
      "https://s.4cdn.org/image/skeletons/5.gif",
      "https://s.4cdn.org/image/skeletons/6.gif",
      "https://s.4cdn.org/image/skeletons/7.gif",
      "https://s.4cdn.org/image/skeletons/8.gif",
      "https://s.4cdn.org/image/skeletons/9.gif",
      "https://s.4cdn.org/image/skeletons/10.gif",
      "https://s.4cdn.org/image/skeletons/11.gif",
      "https://s.4cdn.org/image/skeletons/12.gif",
      "https://s.4cdn.org/image/skeletons/13.gif",
      "https://s.4cdn.org/image/skeletons/14.gif",
      "https://s.4cdn.org/image/skeletons/15.gif",
      "https://s.4cdn.org/image/skeletons/16.gif",
      "https://s.4cdn.org/image/skeletons/17.gif",
      "https://s.4cdn.org/image/skeletons/18.gif",
      "https://s.4cdn.org/image/skeletons/19.gif",
      "https://s.4cdn.org/image/skeletons/20.gif",
      "https://s.4cdn.org/image/skeletons/21.gif",
      "https://s.4cdn.org/image/skeletons/22.gif",
      "https://s.4cdn.org/image/skeletons/23.gif"
    )
  }
}