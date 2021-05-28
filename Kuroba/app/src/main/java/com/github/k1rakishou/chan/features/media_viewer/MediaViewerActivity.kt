package com.github.k1rakishou.chan.features.media_viewer

import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.base.ControllerHostActivity
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.activity.ActivityModule
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.utils.FullScreenUtils.hideSystemUI
import com.github.k1rakishou.chan.utils.FullScreenUtils.isSystemUIHidden
import com.github.k1rakishou.chan.utils.FullScreenUtils.setupEdgeToEdge
import com.github.k1rakishou.chan.utils.FullScreenUtils.setupStatusAndNavBarColors
import com.github.k1rakishou.chan.utils.FullScreenUtils.showSystemUI
import com.github.k1rakishou.chan.utils.FullScreenUtils.toggleSystemUI
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.persist_state.PersistableChanState
import kotlinx.coroutines.launch
import javax.inject.Inject

class MediaViewerActivity : ControllerHostActivity(), MediaViewerController.MediaViewerCallbacks {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private lateinit var activityComponent: ActivityComponent
  private lateinit var viewModelComponent: ViewModelComponent
  private lateinit var mediaViewerController: MediaViewerController

  private val viewModel by viewModels<MediaViewerControllerViewModel>()

  fun getActivityComponent(): ActivityComponent {
    return activityComponent
  }

  fun getViewModelComponent(): ViewModelComponent {
    return viewModelComponent
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    activityComponent = Chan.getComponent()
      .activityComponentBuilder()
      .activity(this)
      .activityModule(ActivityModule())
      .build()
      .also { component -> component.inject(this) }

    viewModelComponent = Chan.getComponent()
      .viewModelComponentBuilder()
      .build()
      .also { component -> component.inject(viewModel) }

    contentView = findViewById(android.R.id.content)
    AndroidUtils.getWindow(this).addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    window.setupEdgeToEdge()
    window.setupStatusAndNavBarColors(themeEngine.chanTheme)

    if (PersistableChanState.imageViewerImmersiveModeEnabled.get()) {
      window.hideSystemUI(themeEngine.chanTheme)
    } else {
      window.showSystemUI(themeEngine.chanTheme)
    }

    mediaViewerController = MediaViewerController(
      context = this,
      mediaViewerCallbacks = this
    ).apply {
      onCreate()
      onShow()
    }

    globalWindowInsetsManager.listenForWindowInsetsChanges(window, null)

    setContentView(mediaViewerController.view)
    pushController(mediaViewerController)

    lifecycleScope.launch {
      if (!handleNewIntent(isNotActivityRecreation = savedInstanceState == null, intent = intent)) {
        finish()
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    AndroidUtils.getWindow(this).clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  override fun finish() {
    super.finish()

    overridePendingTransition(0, 0)
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)

    lifecycleScope.launch {
      handleNewIntent(isNotActivityRecreation = false, intent = intent)
    }
  }

  override fun finishActivity() {
    finish()
  }

  override fun toggleFullScreenMode() {
    if (::themeEngine.isInitialized && ::mediaViewerController.isInitialized) {
      window.toggleSystemUI(themeEngine.chanTheme)

      mediaViewerController.onSystemUiVisibilityChanged(window.isSystemUIHidden())
      PersistableChanState.imageViewerImmersiveModeEnabled.set(window.isSystemUIHidden())
    }
  }

  private suspend fun handleNewIntent(isNotActivityRecreation: Boolean, intent: Intent?): Boolean {
    if (intent == null) {
      return false
    }

    val action = intent.action
    if (action == null) {
      return false
    }

    if (!::mediaViewerController.isInitialized) {
      Logger.e(TAG, "handleNewIntent() mediaViewerController is not initialized")
      return false
    }

    val viewableMediaParcelableHolder = tryExtractViewableMediaParcelableHolderOrNull(intent)
    if (viewableMediaParcelableHolder == null) {
      Logger.e(TAG, "handleNewIntent() Failed to extract viewableMedia from intent \'$intent\'")
      return false
    }

    val success = viewModel.showMedia(
      isNotActivityRecreation = isNotActivityRecreation,
      viewableMediaParcelableHolder = viewableMediaParcelableHolder
    )

    Logger.d(TAG, "handleNewIntent() viewModel.showMedia() -> $success")
    return success
  }

  private fun tryExtractViewableMediaParcelableHolderOrNull(intent: Intent): ViewableMediaParcelableHolder? {
    when (val action = intent.action!!) {
      VIEW_CATALOG_MEDIA_ACTION -> {
        Logger.d(TAG, "handleNewIntent() action=${action}")

        return intent.extras
          ?.getBundle(MEDIA_VIEWER_PARAMS)
          ?.getParcelable<ViewableMediaParcelableHolder.CatalogMediaParcelableHolder>(CATALOG_DESCRIPTOR_PARAM)
      }
      VIEW_THREAD_MEDIA_ACTION -> {
        Logger.d(TAG, "handleNewIntent() action=${action}")

        return intent.extras
          ?.getBundle(MEDIA_VIEWER_PARAMS)
          ?.getParcelable<ViewableMediaParcelableHolder.ThreadMediaParcelableHolder>(THREAD_DESCRIPTOR_PARAM)
      }
      else -> {
        Logger.e(TAG, "handleNewIntent() Unknown action: \'$action\' was passed to activity $TAG")
        return null
      }
    }
  }

  companion object {
    private const val TAG = "MediaViewerActivity"

    private const val VIEW_CATALOG_MEDIA_ACTION = "${BuildConfig.APPLICATION_ID}_internal.view.catalog.media.action"
    private const val VIEW_THREAD_MEDIA_ACTION = "${BuildConfig.APPLICATION_ID}_internal.view.thread.media.action"

    private const val MEDIA_VIEWER_PARAMS = "MEDIA_VIEWER_PARAMS"
    private const val CATALOG_DESCRIPTOR_PARAM = "CATALOG_DESCRIPTOR"
    private const val THREAD_DESCRIPTOR_PARAM = "THREAD_DESCRIPTOR"

    fun catalogAlbum(
      context: Context,
      catalogDescriptor: ChanDescriptor.CatalogDescriptor,
      initialImageUrl: String?,
      transitionThumbnailUrl: String,
      lastTouchCoordinates: Point,
    ) {
      val intent = Intent(context, MediaViewerActivity::class.java)
      intent.action = VIEW_CATALOG_MEDIA_ACTION
      intent.putExtra(
        MEDIA_VIEWER_PARAMS,
        bundleOf(
          Pair(
            CATALOG_DESCRIPTOR_PARAM,
            ViewableMediaParcelableHolder.CatalogMediaParcelableHolder.fromCatalogDescriptor(
              catalogDescriptor = catalogDescriptor,
              initialImageUrl = initialImageUrl,
              transitionInfo = ViewableMediaParcelableHolder.TransitionInfo(
                transitionThumbnailUrl = transitionThumbnailUrl,
                lastTouchPosX = lastTouchCoordinates.x,
                lastTouchPosY = lastTouchCoordinates.y,
              )
            )
          )
        )
      )

      context.startActivity(intent)
    }

    fun threadAlbum(
      context: Context,
      threadDescriptor: ChanDescriptor.ThreadDescriptor,
      initialImageUrl: String?,
      transitionThumbnailUrl: String,
      lastTouchCoordinates: Point,
    ) {
      val intent = Intent(context, MediaViewerActivity::class.java)
      intent.action = VIEW_THREAD_MEDIA_ACTION
      intent.putExtra(
        MEDIA_VIEWER_PARAMS,
        bundleOf(
          Pair(
            THREAD_DESCRIPTOR_PARAM,
            ViewableMediaParcelableHolder.ThreadMediaParcelableHolder.fromThreadDescriptor(
              threadDescriptor = threadDescriptor,
              initialImageUrl = initialImageUrl,
              transitionInfo = ViewableMediaParcelableHolder.TransitionInfo(
                transitionThumbnailUrl = transitionThumbnailUrl,
                lastTouchPosX = lastTouchCoordinates.x,
                lastTouchPosY = lastTouchCoordinates.y,
              )
            )
          )
        )
      )

      context.startActivity(intent)
    }

  }

}