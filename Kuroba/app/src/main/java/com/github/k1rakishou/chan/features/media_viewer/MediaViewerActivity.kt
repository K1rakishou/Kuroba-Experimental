package com.github.k1rakishou.chan.features.media_viewer

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Point
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.base.ControllerHostActivity
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.activity.ActivityModule
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.FullScreenUtils.hideSystemUI
import com.github.k1rakishou.chan.utils.FullScreenUtils.isSystemUIHidden
import com.github.k1rakishou.chan.utils.FullScreenUtils.setupEdgeToEdge
import com.github.k1rakishou.chan.utils.FullScreenUtils.setupStatusAndNavBarColors
import com.github.k1rakishou.chan.utils.FullScreenUtils.showSystemUI
import com.github.k1rakishou.chan.utils.FullScreenUtils.toggleSystemUI
import com.github.k1rakishou.chan.utils.startActivitySafe
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.FSAFActivityCallbacks
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.persist_state.PersistableChanState
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

class MediaViewerActivity : ControllerHostActivity(),
  MediaViewerController.MediaViewerCallbacks,
  ThemeEngine.ThemeChangesListener,
  FSAFActivityCallbacks {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var appRestarter: AppRestarter

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
    AndroidUtils.getWindow(this)
      .addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    mediaViewerController = MediaViewerController(
      context = this,
      mediaViewerCallbacks = this
    ).apply {
      onCreate()
      onShow()
    }
    dialogFactory.containerController = mediaViewerController

    themeEngine.setRootView(this, mediaViewerController.view)
    themeEngine.addListener(this)
    fileChooser.setCallbacks(this)
    appRestarter.attachActivity(this)
    setupContext(this, themeEngine.chanTheme)

    window.setupEdgeToEdge()
    window.setupStatusAndNavBarColors(themeEngine.chanTheme)

    if (PersistableChanState.imageViewerImmersiveModeEnabled.get()) {
      window.hideSystemUI(themeEngine.chanTheme)
    } else {
      window.showSystemUI(themeEngine.chanTheme)
    }

    mediaViewerController.onSystemUiVisibilityChanged(window.isSystemUIHidden())

    globalWindowInsetsManager.listenForWindowInsetsChanges(window, null)
    globalWindowInsetsManager.updateDisplaySize(this)

    setContentView(mediaViewerController.view)
    pushController(mediaViewerController)

    lifecycleScope.launch {
      if (!handleNewIntent(isNotActivityRecreation = savedInstanceState == null, intent = intent)) {
        finish()
      }
    }
  }

  override fun onResume() {
    super.onResume()

    if (::mediaViewerController.isInitialized) {
      mediaViewerController.onResume()
    }
  }

  override fun onPause() {
    super.onPause()

    if (::mediaViewerController.isInitialized) {
      mediaViewerController.onPause()
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    if (::themeEngine.isInitialized) {
      themeEngine.removeRootView(this)
      themeEngine.removeListener(this)
    }

    if (::fileChooser.isInitialized) {
      fileChooser.removeCallbacks()
    }

    if (::appRestarter.isInitialized) {
      appRestarter.detachActivity(this)
    }

    AppModuleAndroidUtils.cancelLastToast()

    AndroidUtils.getWindow(this)
      .clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

  override fun isSystemUiHidden(): Boolean {
    return window.isSystemUIHidden()
  }

  override fun toggleFullScreenMode() {
    if (::themeEngine.isInitialized && ::mediaViewerController.isInitialized) {
      window.toggleSystemUI(themeEngine.chanTheme)

      mediaViewerController.onSystemUiVisibilityChanged(window.isSystemUIHidden())
      PersistableChanState.imageViewerImmersiveModeEnabled.set(window.isSystemUIHidden())
    }
  }

  override fun onThemeChanged() {
    window.setupStatusAndNavBarColors(themeEngine.chanTheme)
  }

  override fun fsafStartActivityForResult(intent: Intent, requestCode: Int) {
    startActivityForResult(intent, requestCode)
  }

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (fileChooser.onActivityResult(requestCode, resultCode, data)) {
      return
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    runtimePermissionsHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
  }

  override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
    globalWindowInsetsManager.updateLastTouchCoordinates(ev)
    return super.dispatchTouchEvent(ev)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    globalWindowInsetsManager.updateDisplaySize(this)
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
      val errorMessage = "Failed to extract viewableMedia from intent '$intent'"

      Logger.e(TAG, "handleNewIntent() $errorMessage")
      Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_LONG).show()

      return false
    }

    val success = viewModel.showMedia(
      isNotActivityRecreation = isNotActivityRecreation,
      viewableMediaParcelableHolder = viewableMediaParcelableHolder
    )

    Logger.d(TAG, "handleNewIntent() viewModel.showMedia() -> $success")

    if (!success) {
      val errorMessage = "Failed to display viewableMedia (no media to show was found)"
      Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_LONG).show()
    }

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
      VIEW_COMPOSITE_CATALOG_MEDIA_ACTION -> {
        Logger.d(TAG, "handleNewIntent() action=${action}")

        return intent.extras
          ?.getBundle(MEDIA_VIEWER_PARAMS)
          ?.getParcelable<ViewableMediaParcelableHolder.CompositeCatalogMediaParcelableHolder>(COMPOSITE_CATALOG_DESCRIPTOR_PARAM)
      }
      VIEW_THREAD_MEDIA_ACTION -> {
        Logger.d(TAG, "handleNewIntent() action=${action}")

        return intent.extras
          ?.getBundle(MEDIA_VIEWER_PARAMS)
          ?.getParcelable<ViewableMediaParcelableHolder.ThreadMediaParcelableHolder>(THREAD_DESCRIPTOR_PARAM)
      }
      VIEW_MIXED_MEDIA_ACTION -> {
        Logger.d(TAG, "handleNewIntent() action=${action}")

        return intent.extras
          ?.getBundle(MEDIA_VIEWER_PARAMS)
          ?.getParcelable<ViewableMediaParcelableHolder.MixedMediaParcelableHolder>(MIXED_MEDIA_PARAM)
      }
      VIEW_REPLY_ATTACH_MEDIA_ACTION -> {
        Logger.d(TAG, "handleNewIntent() action=${action}")

        return intent.extras
          ?.getBundle(MEDIA_VIEWER_PARAMS)
          ?.getParcelable<ViewableMediaParcelableHolder.ReplyAttachMediaParcelableHolder>(REPLY_ATTACH_MEDIA_PARAM)
      }
      Intent.ACTION_SEND -> {
        Logger.d(TAG, "handleNewIntent() action=${action}")

        val uri = intent.extras?.getString(Intent.EXTRA_TEXT)
        if (uri == null) {
          Logger.e(TAG, "handleNewIntent() action=${action} uri==null")
          return null
        }

        if (URLUtil.isFileUrl(uri)) {
          Logger.d(TAG, "handleNewIntent() action=${action} uri=$uri")
          return ViewableMediaParcelableHolder.MixedMediaParcelableHolder(
            mixedMedia = listOf(MediaLocation.Local(uri, isUri = false))
          )
        }

        if (URLUtil.isContentUrl(uri)) {
          Logger.d(TAG, "handleNewIntent() action=${action} uri=$uri")
          return ViewableMediaParcelableHolder.MixedMediaParcelableHolder(
            mixedMedia = listOf(MediaLocation.Local(uri, isUri = true))
          )
        }

        if (URLUtil.isHttpUrl(uri) || URLUtil.isHttpsUrl(uri)) {
          Logger.d(TAG, "handleNewIntent() action=${action} uri=$uri")

          val finalUrl = if (URLUtil.isHttpUrl(uri)) {
            uri.replaceFirst("http://", "https://")
          } else {
            uri
          }

          return ViewableMediaParcelableHolder.MixedMediaParcelableHolder(
            mixedMedia = listOf(MediaLocation.Remote(finalUrl))
          )
        }

        Logger.e(TAG, "handleNewIntent() action=${action} Unsupported uri=$uri")
        return null
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
    private const val VIEW_COMPOSITE_CATALOG_MEDIA_ACTION = "${BuildConfig.APPLICATION_ID}_internal.view.composite_catalog.media.action"
    private const val VIEW_THREAD_MEDIA_ACTION = "${BuildConfig.APPLICATION_ID}_internal.view.thread.media.action"
    private const val VIEW_MIXED_MEDIA_ACTION = "${BuildConfig.APPLICATION_ID}_internal.view.mixed.media.action"
    private const val VIEW_REPLY_ATTACH_MEDIA_ACTION = "${BuildConfig.APPLICATION_ID}_internal.view.reply_attach.media.action"

    private const val MEDIA_VIEWER_PARAMS = "MEDIA_VIEWER_PARAMS"
    private const val CATALOG_DESCRIPTOR_PARAM = "CATALOG_DESCRIPTOR"
    private const val COMPOSITE_CATALOG_DESCRIPTOR_PARAM = "COMPOSITE_CATALOG_DESCRIPTOR"
    private const val THREAD_DESCRIPTOR_PARAM = "THREAD_DESCRIPTOR"
    private const val MIXED_MEDIA_PARAM = "MIXED_MEDIA"
    private const val REPLY_ATTACH_MEDIA_PARAM = "REPLY_ATTACH_MEDIA"

    @JvmStatic
    fun replyAttachMedia(
      context: Context,
      replyUuidList: List<UUID>
    ) {
      Logger.d(TAG, "replyAttachMedia() replyUuidList.size=${replyUuidList.size}")

      val intent = Intent(context, MediaViewerActivity::class.java)
      intent.action = VIEW_REPLY_ATTACH_MEDIA_ACTION
      intent.putExtra(
        MEDIA_VIEWER_PARAMS,
        bundleOf(
          Pair(
            REPLY_ATTACH_MEDIA_PARAM,
            ViewableMediaParcelableHolder.ReplyAttachMediaParcelableHolder(replyUuidList)
          )
        )
      )

      context.startActivitySafe(intent)
    }

    @JvmStatic
    fun mixedMedia(
      context: Context,
      mixedMedia: List<MediaLocation>
    ) {
      Logger.d(TAG, "mixedMedia() mixedMedia.size=${mixedMedia.size}")

      val intent = Intent(context, MediaViewerActivity::class.java)
      intent.action = VIEW_MIXED_MEDIA_ACTION
      intent.putExtra(
        MEDIA_VIEWER_PARAMS,
        bundleOf(
          Pair(
            MIXED_MEDIA_PARAM,
            ViewableMediaParcelableHolder.MixedMediaParcelableHolder(mixedMedia)
          )
        )
      )

      context.startActivitySafe(intent)
    }

    fun catalogMedia(
      context: Context,
      catalogDescriptor: ChanDescriptor.ICatalogDescriptor,
      initialImageUrl: String?,
      transitionThumbnailUrl: String,
      lastTouchCoordinates: Point,
      mediaViewerOptions: MediaViewerOptions
    ) {
      Logger.d(TAG, "catalogMedia() catalogDescriptor=$catalogDescriptor")

      val key = when (catalogDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> CATALOG_DESCRIPTOR_PARAM
        is ChanDescriptor.CompositeCatalogDescriptor -> COMPOSITE_CATALOG_DESCRIPTOR_PARAM
      }

      val action = when (catalogDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> VIEW_CATALOG_MEDIA_ACTION
        is ChanDescriptor.CompositeCatalogDescriptor -> VIEW_COMPOSITE_CATALOG_MEDIA_ACTION
      }

      val catalogMediaParcelableHolder = when (catalogDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          ViewableMediaParcelableHolder.CatalogMediaParcelableHolder.fromCatalogDescriptor(
            catalogDescriptor = catalogDescriptor,
            initialImageUrl = initialImageUrl,
            transitionInfo = ViewableMediaParcelableHolder.TransitionInfo(
              transitionThumbnailUrl = transitionThumbnailUrl,
              lastTouchPosX = lastTouchCoordinates.x,
              lastTouchPosY = lastTouchCoordinates.y,
            ),
            mediaViewerOptions = mediaViewerOptions
          )
        }
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          ViewableMediaParcelableHolder.CompositeCatalogMediaParcelableHolder.fromCompositeCatalogDescriptor(
            compositeCatalogDescriptor = catalogDescriptor,
            initialImageUrl = initialImageUrl,
            transitionInfo = ViewableMediaParcelableHolder.TransitionInfo(
              transitionThumbnailUrl = transitionThumbnailUrl,
              lastTouchPosX = lastTouchCoordinates.x,
              lastTouchPosY = lastTouchCoordinates.y,
            ),
            mediaViewerOptions = mediaViewerOptions
          )
        }
      }

      val intent = Intent(context, MediaViewerActivity::class.java)
      intent.action = action
      intent.putExtra(
        MEDIA_VIEWER_PARAMS,
        bundleOf(Pair(key, catalogMediaParcelableHolder))
      )

      context.startActivitySafe(intent)
    }

    fun threadMedia(
      context: Context,
      threadDescriptor: ChanDescriptor.ThreadDescriptor,
      postDescriptorList: List<PostDescriptor>,
      initialImageUrl: String?,
      transitionThumbnailUrl: String,
      lastTouchCoordinates: Point,
      mediaViewerOptions: MediaViewerOptions
    ) {
      Logger.d(TAG, "threadMedia() postDescriptorList.size=${postDescriptorList.size}")

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
              postDescriptorList = postDescriptorList,
              transitionInfo = ViewableMediaParcelableHolder.TransitionInfo(
                transitionThumbnailUrl = transitionThumbnailUrl,
                lastTouchPosX = lastTouchCoordinates.x,
                lastTouchPosY = lastTouchCoordinates.y,
              ),
              mediaViewerOptions = mediaViewerOptions
            )
          )
        )
      )

      context.startActivitySafe(intent)
    }

  }

}