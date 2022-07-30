/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.activity

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.lifecycleScope
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.ControllerHostActivity
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.di.module.activity.ActivityModule
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.manager.BottomNavBarVisibilityStateManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.ControllerNavigationManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.UpdateManager
import com.github.k1rakishou.chan.core.navigation.RequiresNoBottomNavBar
import com.github.k1rakishou.chan.features.drawer.MainController
import com.github.k1rakishou.chan.ui.controller.AlbumViewController
import com.github.k1rakishou.chan.ui.controller.BrowseController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.controller.ViewThreadController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.SplitNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.ui.view.KurobaBottomNavigationView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.FullScreenUtils.setupEdgeToEdge
import com.github.k1rakishou.chan.utils.FullScreenUtils.setupStatusAndNavBarColors
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.FSAFActivityCallbacks
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import dagger.Lazy
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@DoNotStrip
class StartActivity : ControllerHostActivity(),
  FSAFActivityCallbacks,
  StartActivityCallbacks,
  StartActivityStartupHandlerHelper.StartActivityCallbacks,
  ThemeEngine.ThemeChangesListener {

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
  lateinit var controllerNavigationManager: Lazy<ControllerNavigationManager>
  @Inject
  lateinit var bottomNavBarVisibilityStateManager: Lazy<BottomNavBarVisibilityStateManager>
  @Inject
  lateinit var chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>
  @Inject
  lateinit var updateManager: Lazy<UpdateManager>

  private val compositeDisposable = CompositeDisposable()
  private var intentMismatchWorkaroundActive = false
  private var browseController: BrowseController? = null

  private lateinit var activityComponent: ActivityComponent
  private lateinit var mainRootLayoutMargins: View
  private lateinit var mainNavigationController: NavigationController
  private lateinit var mainController: MainController

  fun getActivityComponent(): ActivityComponent {
    return activityComponent
  }

  @OptIn(ExperimentalTime::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val isFreshStart = savedInstanceState == null

    if (intentMismatchWorkaround()) {
      Logger.d(TAG, "onCreate() intentMismatchWorkaround()==true, " +
        "savedInstanceState == null: $isFreshStart")
      return
    }

    Logger.d(TAG, "onCreate() start isFreshStart: $isFreshStart, initializing everything")

    activityComponent = Chan.getComponent()
      .activityComponentBuilder()
      .activity(this)
      .activityModule(ActivityModule())
      .build()
      .also { component -> component.inject(this) }

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
      val initializeDepsTime = measureTime { initializeDependencies(this, savedInstanceState) }
      Logger.d(TAG, "initializeDependencies took $initializeDepsTime")
    }

    mainController.loadMainControllerDrawerData()
    Logger.d(TAG, "onCreate() end isFreshStart: $isFreshStart")
  }

  override fun onDestroy() {
    super.onDestroy()
    Logger.d(TAG, "onDestroy()")

    AppModuleAndroidUtils.cancelLastToast()
    compositeDisposable.clear()
    appRestarter.detachActivity(this)

    if (::updateManager.isInitialized) {
      updateManager.get().onDestroy()
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

      if (isDevBuild()) {
        themeEngine.checkNoListenersLeft()
      }
    }
  }

  override fun onThemeChanged() {
    window.setupStatusAndNavBarColors(themeEngine.chanTheme)
  }

  @OptIn(ExperimentalTime::class)
  private fun createUi() {
    if (isDevBuild()) {
      EpoxyController.setGlobalDebugLoggingEnabled(true)
    }

    setupContext(this, themeEngine.chanTheme)
    fileChooser.setCallbacks(this)

    contentView = findViewById(android.R.id.content)

    window.setupEdgeToEdge()
    window.setupStatusAndNavBarColors(themeEngine.chanTheme)

    // Setup base controllers, and decide if to use the split layout for tablets
    mainController = MainController(this).apply {
      onCreate()
      onShow()
    }

    mainRootLayoutMargins = mainController.view.findViewById(R.id.drawer_layout)
    globalWindowInsetsManager.listenForWindowInsetsChanges(window, mainRootLayoutMargins)

    mainNavigationController = StyledToolbarNavigationController(this)
    dialogFactory.containerController = mainNavigationController

    setupLayout()

    setContentView(mainController.view)
    themeEngine.setRootView(this, mainController.view)
    pushController(mainController)

    mainController.attachBottomNavViewToToolbar()

    // Prevent overdraw
    // Do this after setContentView, or the decor creating will reset the background to a
    // default non-null drawable
    window.setBackgroundDrawable(null)

    if (ChanSettings.fullUserRotationEnable.get()) {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    }

    browseController?.showLoading(animateTransition = false)
  }

  private suspend fun initializeDependencies(
    coroutineScope: CoroutineScope,
    savedInstanceState: Bundle?
  ) {
    updateManager.get().autoUpdateCheck()

    coroutineScope.launch {
      startActivityStartupHandlerHelper.setupFromStateOrFreshLaunch(intent, savedInstanceState)
    }

    if (KurobaBottomNavigationView.isBottomNavViewEnabled()) {
      compositeDisposable.add(
        bottomNavBarVisibilityStateManager.get().listenForViewsStateUpdates()
          .subscribe { updateBottomNavBar() }
      )

      compositeDisposable.add(
        controllerNavigationManager.get().listenForControllerNavigationChanges()
          .subscribe { change -> updateBottomNavBarIfNeeded(change) }
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)

    lifecycleScope.launch {
      val result = startActivityStartupHandlerHelper.onNewIntentInternal(intent)
      Logger.d(TAG, "onNewIntent() -> $result")
    }
  }

  private fun updateBottomNavBarIfNeeded(change: ControllerNavigationManager.ControllerNavigationChange?) {
    when (change) {
      is ControllerNavigationManager.ControllerNavigationChange.Presented,
      is ControllerNavigationManager.ControllerNavigationChange.Unpresented,
      is ControllerNavigationManager.ControllerNavigationChange.Pushed,
      is ControllerNavigationManager.ControllerNavigationChange.Popped -> {
        updateBottomNavBar()
      }
      is ControllerNavigationManager.ControllerNavigationChange.SwipedFrom -> {
        if (change.controller is AlbumViewController) {
          updateBottomNavBar()
        }
      }
      else -> {
        // no-op
      }
    }
  }

  private fun updateBottomNavBar() {
    if (!KurobaBottomNavigationView.isBottomNavViewEnabled()) {
      return
    }

    val hasRequiresNoBottomNavBarControllers = isControllerAdded { controller -> controller is RequiresNoBottomNavBar }
    if (hasRequiresNoBottomNavBarControllers) {
      mainController.hideBottomNavBar(lockTranslation = true, lockCollapse = true)
      return
    }

    if (bottomNavBarVisibilityStateManager.get().anyOfViewsIsVisible()) {
      mainController.hideBottomNavBar(lockTranslation = true, lockCollapse = true)
      return
    }

    mainController.resetBottomNavViewState(unlockTranslation = true, unlockCollapse = true)
  }

  override fun loadThreadAndMarkPost(postDescriptor: PostDescriptor, animated: Boolean) {
    lifecycleScope.launch {
      browseController?.getViewThreadController()?.let { viewThreadController ->
        if (viewThreadController.chanDescriptor != postDescriptor.descriptor) {
          viewThreadController.showLoading(animateTransition = false)
        }
      }

      mainController.closeAllNonMainControllers()

      chanThreadViewableInfoManager.get().update(postDescriptor.threadDescriptor(), true) { chanThreadViewableInfo ->
        chanThreadViewableInfo.markedPostNo = postDescriptor.postNo
      }

      browseController?.showThread(postDescriptor.threadDescriptor(), animated)
    }
  }

  override fun loadThreadWithoutFocusing(threadDescriptor: ChanDescriptor.ThreadDescriptor, animated: Boolean) {
    lifecycleScope.launch {
      mainController.getViewThreadController()?.let { viewThreadController ->
        if (viewThreadController.chanDescriptor != threadDescriptor) {
          viewThreadController.showLoading(animateTransition = false)
        }
      }

      mainController.loadThreadWithoutFocusing(
        threadDescriptor = threadDescriptor,
        closeAllNonMainControllers = true,
        animated = animated
      )
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
        closeAllNonMainControllers = true,
        animated = animated
      )
    }
  }

  override fun openControllerWrappedIntoBottomNavAwareController(controller: Controller) {
    mainController.openControllerWrappedIntoBottomNavAwareController(controller)
  }

  override fun setSettingsMenuItemSelected() {
    mainController.setSettingsMenuItemSelected()
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun setupLayout() {
    val layoutMode = ChanSettings.getCurrentLayoutMode()

    when (layoutMode) {
      ChanSettings.LayoutMode.SPLIT -> {
        val split = SplitNavigationController(
          this,
          inflate(this, R.layout.layout_split_empty),
          mainController
        )

        mainController.pushChildController(split)
        split.setLeftController(mainNavigationController, false)
      }
      ChanSettings.LayoutMode.PHONE,
      ChanSettings.LayoutMode.SLIDE -> {
        mainController.pushChildController(mainNavigationController)
      }
      ChanSettings.LayoutMode.AUTO -> throw IllegalStateException("Shouldn't happen")
    }

    browseController = BrowseController(this, mainController)

    if (layoutMode == ChanSettings.LayoutMode.PHONE || layoutMode == ChanSettings.LayoutMode.SLIDE) {
      val slideController = ThreadSlideController(
        this,
        inflate(this, R.layout.layout_split_empty),
        mainController
      )

      mainNavigationController.pushController(slideController, false)
      slideController.setLeftController(browseController, false)
    } else {
      mainNavigationController.pushController(browseController!!, false)
    }

    browseController!!.onGainedFocus(ThreadSlideController.ThreadControllerType.Catalog)
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

  override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
    globalWindowInsetsManager.updateLastTouchCoordinates(ev)
    return super.dispatchTouchEvent(ev)
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
      val dblNav = mainController.childControllers[0] as SplitNavigationController

      if (dblNav.getRightController() is NavigationController) {
        val rightNavigationController = dblNav.getRightController() as NavigationController

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
          if (controller.getRightController() is ViewThreadController) {
            threadDescriptor = (controller.getRightController() as ViewThreadController).chanDescriptor
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

    if (AndroidUtils.isAndroid10() && !ChanSettings.ignoreDarkNightMode.get()) {
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