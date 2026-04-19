package com.github.k1rakishou.chan.ui.activity

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.lifecycleScope
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.ControllerHostActivity
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.activity.ActivityModule
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.helper.migration.settings.KurobaSettingsMigrationHelper
import com.github.k1rakishou.chan.core.manager.ApplicationCrashNotifier
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.HapticFeedbackManager
import com.github.k1rakishou.chan.core.manager.update.KurobaAppUpdateManager
import com.github.k1rakishou.chan.core.manager.update.MpvLibsUpdateManager
import com.github.k1rakishou.chan.features.drawer.MainController
import com.github.k1rakishou.chan.ui.controller.BrowseController
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.controller.ViewThreadController
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.SplitNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.CrashStorage
import com.github.k1rakishou.chan.utils.FullScreenUtils.setupEdgeToEdge
import com.github.k1rakishou.chan.utils.FullScreenUtils.setupStatusAndNavBarColors
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.FSAFActivityCallbacks
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.LayoutMode
import dagger.Lazy
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.measureTime

class StartActivity :
  ControllerHostActivity(),
  FSAFActivityCallbacks,
  StartActivityStartupHandlerHelper.StartActivityCallbacks,
  ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var kurobaSettings: KurobaSettings
  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var imagePickHelper: ImagePickHelper
  @Inject
  lateinit var appRestarter: AppRestarter
  @Inject
  lateinit var startActivityStartupHandlerHelper: StartActivityStartupHandlerHelper
  @Inject
  lateinit var chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>
  @Inject
  lateinit var kurobaAppUpdateManager: Lazy<KurobaAppUpdateManager>
  @Inject
  lateinit var mpvLibsUpdateManager: Lazy<MpvLibsUpdateManager>
  @Inject
  lateinit var applicationCrashNotifier: ApplicationCrashNotifier
  @Inject
  lateinit var globalUiStateHolder: GlobalUiStateHolder
  @Inject
  lateinit var hapticFeedbackManager: HapticFeedbackManager
  @Inject
  lateinit var kurobaSettingsMigrationHelper: KurobaSettingsMigrationHelper

  private var intentMismatchWorkaroundActive = false
  private var browseController: BrowseController? = null

  override lateinit var activityComponent: ActivityComponent
  private lateinit var viewModelComponent: ViewModelComponent

  private lateinit var mainRootLayoutMargins: View
  private lateinit var mainNavigationController: NavigationController
  private lateinit var mainController: MainController

  @Suppress("ForbiddenComment")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val isFreshStart = savedInstanceState == null

    if (intentMismatchWorkaround()) {
      Logger.d(
        TAG, "onCreate() intentMismatchWorkaround()==true, " +
        "savedInstanceState == null: $isFreshStart")
      return
    }

    if (isFreshStart) {
      val crashData = CrashStorage.loadCrash(this)
      if (crashData != null) {
        Logger.debug(TAG) { "There is a crash data stored on the disk. Launching the CrashReportActivity." }
        CrashReportActivity.launch(this)
        finish()
        return
      }
    }

    Logger.d(TAG, "onCreate() start isFreshStart: $isFreshStart, initializing everything")

    activityComponent = Chan.getComponent()
      .activityComponentBuilder()
      .activity(this)
      .activityModule(ActivityModule())
      .build()
      .also { component -> component.inject(this) }

    viewModelComponent = Chan.getComponent()
      .viewModelComponentBuilder()
      .build()

    globalWindowInsetsManager.updateDisplaySize(this)

    themeEngine.addListener(this)
    themeEngine.refreshViews()

    val createUiTime = measureTime { createUi() }
    Logger.d(TAG, "createUi took $createUiTime")

    imagePickHelper.onActivityCreated(this)
    appRestarter.attachActivity(this)

    startActivityStartupHandlerHelper.onCreate(
      context = this,
      browseController = browseController!!,
      mainController = mainController,
      startActivityCallbacks = this
    )

    lifecycleScope.launch {
      kurobaAppUpdateManager.get().autoUpdateCheck()
    }
    lifecycleScope.launch {
      mpvLibsUpdateManager.get().check(forced = false)
    }
    lifecycleScope.launch {
      startActivityStartupHandlerHelper.setupFromStateOrFreshLaunch(intent, savedInstanceState)
    }
    lifecycleScope.launch {
      applicationCrashNotifier.applicationCrashedEventFlow
        .onEach { finish() }
        .collect()
    }

    mainController.loadMainControllerDrawerData()
    Logger.d(TAG, "onCreate() end isFreshStart: $isFreshStart")

    // TODO: remove me in 1 year
    if (kurobaSettingsMigrationHelper.perform(forced = false)) {
      appRestarter.restart()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    Logger.d(TAG, "onDestroy()")

    if (::appRestarter.isInitialized) {
      appRestarter.detachActivity(this)
    }

    if (::kurobaAppUpdateManager.isInitialized) {
      kurobaAppUpdateManager.get().onDestroy()
    }

    if (::imagePickHelper.isInitialized) {
      imagePickHelper.onActivityDestroyed(this)
    }

    if (::fileChooser.isInitialized) {
      fileChooser.removeCallbacks()
    }

    if (::startActivityStartupHandlerHelper.isInitialized) {
      startActivityStartupHandlerHelper.onDestroy()
    }

    if (::themeEngine.isInitialized) {
      themeEngine.removeRootView(this)
      themeEngine.removeListener(this)
    }

    if (::globalWindowInsetsManager.isInitialized) {
      globalWindowInsetsManager.stopListeningForWindowInsetsChanges(window)
    }

    if (::hapticFeedbackManager.isInitialized) {
      hapticFeedbackManager.onDetachedFromView()
    }
  }

  override fun onThemeChanged() {
    window.setupStatusAndNavBarColors(themeEngine.chanTheme)
  }

  private fun createUi() {
    if (AppModuleAndroidUtils.isDevBuild) {
      EpoxyController.setGlobalDebugLoggingEnabled(true)
    }

    setupContext(this, themeEngine.chanTheme)
    initView(findViewById(android.R.id.content))

    fileChooser.setCallbacks(this)

    window.setupEdgeToEdge()
    window.setupStatusAndNavBarColors(themeEngine.chanTheme)

    // Setup base controllers, and decide if to use the split layout for tablets
    mainController = MainController(this).apply {
      onCreate()
      onShow()
    }

    hapticFeedbackManager.onAttachedToView(mainController.view)

    mainRootLayoutMargins = mainController.view.findViewById(R.id.drawer_layout)
    globalWindowInsetsManager.listenForWindowInsetsChanges(window, mainRootLayoutMargins)

    mainNavigationController = StyledToolbarNavigationController(this)
    dialogFactory.containerController = mainNavigationController

    setupLayout()

    setContentView(mainController.view)
    themeEngine.setRootView(this, mainController.view)
    pushControllerIntoStack(mainController)

    // Prevent overdraw
    // Do this after setContentView, or the decor creating will reset the background to a
    // default non-null drawable
    window.setBackgroundDrawable(null)

    browseController?.showLoading(animateTransition = false)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)

    lifecycleScope.launch {
      val result = startActivityStartupHandlerHelper.onNewIntentInternal(intent)
      Logger.d(TAG, "onNewIntent() -> $result")
    }
  }

  override fun loadThreadAndMarkPost(postDescriptor: PostDescriptor, animated: Boolean) {
    lifecycleScope.launch {
      browseController?.getViewThreadController()?.let { viewThreadController ->
        if (viewThreadController.chanDescriptor != postDescriptor.descriptor) {
          viewThreadController.showLoading(animateTransition = false)
        }
      }

      chanThreadViewableInfoManager.get().update(postDescriptor.threadDescriptor(), true) { chanThreadViewableInfo ->
        chanThreadViewableInfo.markedPostNo = postDescriptor.postNo
      }

      browseController?.showThread(postDescriptor.threadDescriptor(), animated)
    }
  }

  override fun loadThread(threadDescriptor: ChanDescriptor.ThreadDescriptor, animated: Boolean) {
    lifecycleScope.launch {
      mainController.getViewThreadController()?.let { viewThreadController ->
        if (viewThreadController.chanDescriptor != threadDescriptor) {
          viewThreadController.showLoading(animateTransition = false)
        }
      }

      mainController.loadThread(
        descriptor = threadDescriptor,
        animated = animated
      )
    }
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun setupLayout() {
    val layoutMode = kurobaSettings.application.getCurrentLayoutModeBlocking()

    when (layoutMode) {
      LayoutMode.Split -> {
        val split = SplitNavigationController(
          context = this,
          emptyView = inflate(this, R.layout.layout_split_empty)
        )

        mainController.pushChildController(split)
        split.updateLeftController(mainNavigationController, false)
      }
      LayoutMode.Phone,
      LayoutMode.Slide -> {
        mainController.pushChildController(mainNavigationController)
      }
      LayoutMode.Auto -> throw IllegalStateException("Shouldn't happen")
    }

    browseController = BrowseController(this, mainController)

    if (layoutMode == LayoutMode.Phone || layoutMode == LayoutMode.Slide) {
      val slideController = ThreadSlideController(
        context = this,
        mainControllerCallbacks = mainController,
        emptyView = inflate(this, R.layout.layout_split_empty)
      )

      mainNavigationController.pushController(slideController, false)
      slideController.updateLeftController(browseController, false)
    } else {
      mainNavigationController.pushController(browseController!!, false)
    }

    browseController!!.onGainedFocus(ThreadControllerType.Catalog)
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN) {
      mainController.onMenuClicked()
      return true
    }

    try {
      return super.dispatchKeyEvent(event)
    } catch (error: Throwable) {
      // java.lang.IllegalStateException: focus search returned a view that wasn't able to take focus
      return false
    }
  }

  override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
    globalUiStateHolder.updateMainUiState {
      if (
        event == null ||
        event.pointerCount != 1 ||
        event.actionMasked == MotionEvent.ACTION_UP ||
        event.actionMasked == MotionEvent.ACTION_CANCEL
      ) {
        updateTouchPosition(Offset.Unspecified, event?.actionMasked)
      } else {
        updateTouchPosition(Offset(event.rawX, event.rawY), event.actionMasked)
      }
    }

    globalWindowInsetsManager.updateLastTouchCoordinates(event)
    return super.dispatchTouchEvent(event)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)

    val boardDescriptor = browseController?.chanDescriptor
    if (boardDescriptor == null) {
      Logger.w(TAG, "Can not save instance state, the board loadable is null")
      return
    }

    var threadDescriptor: ChanDescriptor? = null

    if (mainController.childControllers[0] is SplitNavigationController) {
      val splitNavigationController = mainController.childControllers[0] as SplitNavigationController

      if (splitNavigationController.rightController() is NavigationController) {
        val rightNavigationController = splitNavigationController.rightController() as NavigationController

        for (controller in rightNavigationController.childControllers) {
          if (controller is ViewThreadController) {
            threadDescriptor = controller.chanDescriptor
            break
          }
        }
      }
    } else {
      val controllers: List<Controller> = mainNavigationController.childControllers

      for (controller in controllers) {
        if (controller is ViewThreadController) {
          threadDescriptor = controller.chanDescriptor
          break
        } else if (controller is ThreadSlideController) {
          if (controller.rightController() is ViewThreadController) {
            threadDescriptor = (controller.rightController() as ViewThreadController).chanDescriptor
            break
          }
        }
      }
    }

    if (threadDescriptor == null) {
      return
    }

    val chanState = ChanState(
      DescriptorParcelable.fromDescriptor(boardDescriptor),
      DescriptorParcelable.fromDescriptor(threadDescriptor)
    )

    outState.putParcelable(STATE_KEY, chanState)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    if (AndroidUtils.isAndroidQ && !kurobaSettings.application.ignoreDarkNightMode.readBlocking()) {
      applyLightDarkThemeIfNeeded(newConfig)
    }

    globalWindowInsetsManager.updateDisplaySize(this)
  }

  private fun applyLightDarkThemeIfNeeded(newConfig: Configuration) {
    val nightModeFlags = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
    if (nightModeFlags == Configuration.UI_MODE_NIGHT_UNDEFINED) {
      return
    }

    if (!::themeEngine.isInitialized) {
      return
    }

    when (nightModeFlags) {
      Configuration.UI_MODE_NIGHT_YES -> themeEngine.switchTheme(switchToDarkTheme = true)
      Configuration.UI_MODE_NIGHT_NO -> themeEngine.switchTheme(switchToDarkTheme = false)
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

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (fileChooser.onActivityResult(requestCode, resultCode, data)) {
      return
    }

    imagePickHelper.onActivityResult(requestCode, resultCode, data)
  }

  private fun intentMismatchWorkaround(): Boolean {
    // Workaround for an intent mismatch that causes a new activity instance to be started
    // every time the app is launched from the launcher.
    // See https://issuetracker.google.com/issues/36907463
    // Still unfixed as of 5/15/2019
    if (intentMismatchWorkaroundActive) {
      return true
    }

    if (!isTaskRoot) {
      val intent = intent
      if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && Intent.ACTION_MAIN == intent.action) {
        Logger.w(TAG, "Workaround for intent mismatch.")
        intentMismatchWorkaroundActive = true
        finish()
        return true
      }
    }

    return false
  }

  override fun fsafStartActivityForResult(intent: Intent, requestCode: Int) {
    startActivityForResult(intent, requestCode)
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_START)
  public override fun onStart() {
    super.onStart()
    Logger.d(TAG, "start")
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
  public override fun onStop() {
    super.onStop()
    Logger.d(TAG, "stop")
  }

  companion object {
    private const val TAG = "StartActivity"
    const val STATE_KEY = "chan_state"
  }
}