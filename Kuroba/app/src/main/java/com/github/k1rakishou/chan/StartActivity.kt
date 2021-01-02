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
package com.github.k1rakishou.chan

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.lifecycleScope
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.StartActivityComponent
import com.github.k1rakishou.chan.core.di.module.activity.StartActivityModule
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.BottomNavBarVisibilityStateManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.ControllerNavigationManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.UpdateManager
import com.github.k1rakishou.chan.core.navigation.RequiresNoBottomNavBar
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.features.drawer.DrawerController
import com.github.k1rakishou.chan.ui.controller.AlbumViewController
import com.github.k1rakishou.chan.ui.controller.BrowseController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.controller.ViewThreadController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.SplitNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.ui.helper.picker.PickedFile
import com.github.k1rakishou.chan.ui.helper.picker.ShareFilePicker
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.FullScreenUtils
import com.github.k1rakishou.chan.utils.FullScreenUtils.setupFullscreen
import com.github.k1rakishou.chan.utils.FullScreenUtils.setupStatusAndNavBarColors
import com.github.k1rakishou.chan.utils.NotificationConstants
import com.github.k1rakishou.chan.utils.plusAssign
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AndroidUtils.isAndroid11
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.updateMargins
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.FSAFActivityCallbacks
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@DoNotStrip
class StartActivity : AppCompatActivity(),
  FSAFActivityCallbacks,
  StartActivityCallbacks,
  ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var siteResolver: SiteResolver
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var historyNavigationManager: HistoryNavigationManager
  @Inject
  lateinit var controllerNavigationManager: ControllerNavigationManager
  @Inject
  lateinit var bottomNavBarVisibilityStateManager: BottomNavBarVisibilityStateManager
  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var chanFilterManager: ChanFilterManager
  @Inject
  lateinit var chanThreadViewableInfoManager: ChanThreadViewableInfoManager
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var imagePickHelper: ImagePickHelper
  @Inject
  lateinit var runtimePermissionsHelper: RuntimePermissionsHelper
  @Inject
  lateinit var updateManager: UpdateManager

  private val stack = Stack<Controller>()
  private val job = SupervisorJob()
  private val compositeDisposable = CompositeDisposable()

  private var intentMismatchWorkaroundActive = false
  private var exitFlag = false
  private var browseController: BrowseController? = null

  lateinit var contentView: ViewGroup

  private lateinit var startActivityComponent: StartActivityComponent
  private lateinit var mainRootLayoutMargins: TouchBlockingFrameLayout
  private lateinit var mainNavigationController: NavigationController
  private lateinit var drawerController: DrawerController

  fun getComponent(): StartActivityComponent {
    return startActivityComponent
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

    startActivityComponent = Chan.getComponent()
      .activityComponentBuilder()
      .startActivity(this)
      .startActivityModule(StartActivityModule())
      .build()
      .also { component -> component.inject(this) }

    val createUiTime = measureTime { createUi() }
    Logger.d(TAG, "createUi took $createUiTime")

    themeEngine.addListener(this)
    themeEngine.refreshViews()

    imagePickHelper.onActivityCreated(this)

    lifecycleScope.launch {
      val initializeDepsTime = measureTime { initializeDependencies(this, savedInstanceState) }
      Logger.d(TAG, "initializeDependencies took $initializeDepsTime")
    }

    Logger.d(TAG, "onCreate() end isFreshStart: $isFreshStart")
  }

  override fun onDestroy() {
    super.onDestroy()
    Logger.d(TAG, "onDestroy()")

    AppModuleAndroidUtils.cancelLastToast()

    compositeDisposable.clear()
    job.cancel()

    if (::themeEngine.isInitialized) {
      themeEngine.removeRootView()
      themeEngine.removeListener(this)
    }

    if (::updateManager.isInitialized) {
      updateManager.onDestroy()
    }

    if (::imagePickHelper.isInitialized) {
      imagePickHelper.onActivityDestroyed()
    }

    if (::fileChooser.isInitialized) {
      fileChooser.removeCallbacks()
    }

    while (!stack.isEmpty()) {
      val controller = stack.pop()
      controller.onHide()
      controller.onDestroy()
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

    window.setupFullscreen()
    window.setupStatusAndNavBarColors(themeEngine.chanTheme)

    // Setup base controllers, and decide if to use the split layout for tablets
    drawerController = DrawerController(this).apply {
      onCreate()
      onShow()
    }

    mainRootLayoutMargins = drawerController.view.findViewById(R.id.main_root_layout_margins)
    listenForWindowInsetsChanges()

    mainNavigationController = StyledToolbarNavigationController(this)
    setupLayout()

    setContentView(drawerController.view)
    themeEngine.setRootView(drawerController.view)
    pushController(drawerController)

    drawerController.attachBottomNavViewToToolbar()

    // Prevent overdraw
    // Do this after setContentView, or the decor creating will reset the background to a
    // default non-null drawable
    window.setBackgroundDrawable(null)

    if (ChanSettings.fullUserRotationEnable.get()) {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    }

    browseController?.showLoading()
  }

  private suspend fun initializeDependencies(
    coroutineScope: CoroutineScope,
    savedInstanceState: Bundle?
  ) {
    updateManager.autoUpdateCheck()

    coroutineScope.launch {
      setupFromStateOrFreshLaunch(savedInstanceState)
    }

    if (ChanSettings.getCurrentLayoutMode() != ChanSettings.LayoutMode.SPLIT) {
      compositeDisposable += bottomNavBarVisibilityStateManager.listenForViewsStateUpdates()
        .subscribe { updateBottomNavBar() }

      compositeDisposable += controllerNavigationManager.listenForControllerNavigationChanges()
        .subscribe { change -> updateBottomNavBarIfNeeded(change) }
    }

    onNewIntentInternal(intent)
  }

  private fun listenForWindowInsetsChanges() {
    ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
      val isKeyboardOpen = FullScreenUtils.isKeyboardShown(view, insets.systemWindowInsetBottom)

      globalWindowInsetsManager.updateInsets(
        insets.replaceSystemWindowInsets(
          insets.systemWindowInsetLeft,
          insets.systemWindowInsetTop,
          insets.systemWindowInsetRight,
          FullScreenUtils.calculateDesiredBottomInset(view, insets.systemWindowInsetBottom)
        )
      )

      globalWindowInsetsManager.updateKeyboardHeight(
        FullScreenUtils.calculateDesiredRealBottomInset(view, insets.systemWindowInsetBottom)
      )

      globalWindowInsetsManager.updateIsKeyboardOpened(isKeyboardOpen)
      globalWindowInsetsManager.fireCallbacks()
      globalWindowInsetsManager.fireInsetsUpdateCallbacks()

      mainRootLayoutMargins.updateMargins(
        left = globalWindowInsetsManager.left(),
        right = globalWindowInsetsManager.right()
      )

      return@setOnApplyWindowInsetsListener ViewCompat.onApplyWindowInsets(
        view,
        insets.replaceSystemWindowInsets(
          0,
          0,
          0,
          FullScreenUtils.calculateDesiredRealBottomInset(view, insets.systemWindowInsetBottom)
        )
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)

    onNewIntentInternal(intent)
  }

  private fun onNewIntentInternal(intent: Intent?) {
    if (intent == null) {
      return
    }

    val extras = intent.extras
      ?: return
    val action = intent.action
      ?: return

    if (!isKnownAction(action)) {
      return
    }

    lifecycleScope.launch {
      Logger.d(TAG, "onNewIntentInternal called, action=${action}")

      if (action == Intent.ACTION_SEND) {
        if (onShareIntentReceived(intent)) {
          setResult(Activity.RESULT_OK)
        } else {
          setResult(Activity.RESULT_CANCELED)
        }

        if (isAndroid11() && !isTaskRoot) {
          Logger.d(TAG, "Current task is not root, finishing...")
          finish()
        }

        return@launch
      }

      bookmarksManager.awaitUntilInitialized()

      when {
        intent.hasExtra(NotificationConstants.ReplyNotifications.R_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY) -> {
          replyNotificationClicked(extras)
        }
        intent.hasExtra(NotificationConstants.ReplyNotifications.R_NOTIFICATION_SWIPE_THREAD_DESCRIPTORS_KEY) -> {
          replyNotificationSwipedAway(extras)
        }
        intent.hasExtra(NotificationConstants.LastPageNotifications.LP_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY) -> {
          lastPageNotificationClicked(extras)
        }
      }
    }
  }

  private suspend fun onShareIntentReceived(intent: Intent): Boolean {
    showToast(this@StartActivity, getString(R.string.share_success_start))

    val shareResult = imagePickHelper.pickFilesFromIntent(
      ShareFilePicker.ShareFilePickerInput(intent)
    )

    when (shareResult) {
      is ModularResult.Value -> {
        when (val pickedFile = shareResult.value) {
          is PickedFile.Result -> {
            val sharedFilesCount = pickedFile.replyFiles.size

            if (sharedFilesCount > 0) {
              showToast(
                this@StartActivity,
                getString(R.string.share_success_message, sharedFilesCount)
              )
            } else {
              showToast(this@StartActivity, R.string.share_error_message)
            }

            return true
          }
          is PickedFile.Failure -> {
            Logger.e(
              TAG,
              "imagePickHelper.pickFilesFromIntent() -> PickedFile.Failure",
              pickedFile.reason
            )

            showToast(this@StartActivity, R.string.share_error_message)
            return false
          }
        }
      }
      is ModularResult.Error -> {
        Logger.e(TAG, "imagePickHelper.pickFilesFromIntent() -> MR.Error", shareResult.error)
        showToast(this@StartActivity, R.string.share_error_message)
        return false
      }
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
    if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
      return
    }

    val hasRequiresNoBottomNavBarControllers = isControllerAdded { controller -> controller is RequiresNoBottomNavBar }
    if (hasRequiresNoBottomNavBarControllers) {
      drawerController.hideBottomNavBar(lockTranslation = true, lockCollapse = true)
      return
    }

    if (bottomNavBarVisibilityStateManager.anyOfViewsIsVisible()) {
      drawerController.hideBottomNavBar(lockTranslation = true, lockCollapse = true)
      return
    }

    drawerController.resetBottomNavViewState(unlockTranslation = true, unlockCollapse = true)
  }

  private fun isControllerPresent(
    controller: Controller,
    predicate: (Controller) -> Boolean
  ): Boolean {
    if (predicate(controller)) {
      return true
    }

    return controller.childControllers.any { isControllerPresent(it, predicate) }
  }

  private suspend fun setupFromStateOrFreshLaunch(savedInstanceState: Bundle?) {
    historyNavigationManager.awaitUntilInitialized()
    siteManager.awaitUntilInitialized()
    boardManager.awaitUntilInitialized()
    bookmarksManager.awaitUntilInitialized()
    chanFilterManager.awaitUntilInitialized()

    val handled = if (savedInstanceState != null) {
      restoreFromSavedState(savedInstanceState)
    } else {
      restoreFromUrl()
    }

    // Not from a state or from an url, launch the setup controller if no boards are setup up yet,
    // otherwise load the default saved board.
    if (!handled) {
      restoreFresh()
    }
  }

  private suspend fun restoreFresh() {
    Logger.d(TAG, "restoreFresh()")

    if (!siteManager.areSitesSetup()) {
      Logger.d(TAG, "restoreFresh() Sites are not setup, showSitesNotSetup()")
      browseController?.showSitesNotSetup()
      return
    }

    val boardToOpen = getBoardToOpen()
    Logger.d(TAG, "restoreFresh() getBoardToOpen returned ${boardToOpen}")

    if (boardToOpen != null) {
      browseController?.showBoard(boardToOpen, false)
    } else {
      browseController?.loadWithDefaultBoard()
    }

    val threadToOpen = getThreadToOpen()
    Logger.d(TAG, "restoreFresh() getThreadToOpen returned ${threadToOpen}")

    if (threadToOpen != null) {
      loadThread(threadToOpen, animated = false)
    }
  }

  private fun getThreadToOpen(): ChanDescriptor.ThreadDescriptor? {
    val loadLastOpenedThreadUponAppStart = ChanSettings.loadLastOpenedThreadUponAppStart.get()
    Logger.d(
      TAG,
      "getThreadToOpen, loadLastOpenedThreadUponAppStart=$loadLastOpenedThreadUponAppStart"
    )

    if (loadLastOpenedThreadUponAppStart) {
      return historyNavigationManager.getNavElementAtTop()?.descriptor()?.threadDescriptorOrNull()
    }

    return null
  }

  private fun getBoardToOpen(): BoardDescriptor? {
    val loadLastOpenedBoardUponAppStart = ChanSettings.loadLastOpenedBoardUponAppStart.get()
    Logger.d(
      TAG,
      "getBoardToOpen, loadLastOpenedBoardUponAppStart=$loadLastOpenedBoardUponAppStart"
    )

    if (loadLastOpenedBoardUponAppStart) {
      return historyNavigationManager.getFirstCatalogNavElement()?.descriptor()?.boardDescriptor()
    }

    return siteManager.firstSiteDescriptor()?.let { firstSiteDescriptor ->
      return@let boardManager.firstBoardDescriptor(firstSiteDescriptor)
    }
  }

  fun loadThread(postDescriptor: PostDescriptor) {
    lifecycleScope.launch {
      drawerController.closeAllNonMainControllers()

      if (!postDescriptor.isOP()) {
        chanThreadViewableInfoManager.update(postDescriptor.threadDescriptor(), true) { chanThreadViewableInfo ->
          chanThreadViewableInfo.markedPostNo = postDescriptor.postNo
        }
      }

      browseController?.showThread(postDescriptor.threadDescriptor(), false)
    }
  }

  suspend fun loadThread(threadDescriptor: ChanDescriptor.ThreadDescriptor, animated: Boolean) {
    drawerController.loadThread(
      threadDescriptor,
      closeAllNonMainControllers = true,
      animated = animated
    )
  }

  override fun openControllerWrappedIntoBottomNavAwareController(controller: Controller) {
    drawerController.openControllerWrappedIntoBottomNavAwareController(controller)
  }

  override fun setSettingsMenuItemSelected() {
    drawerController.setSettingsMenuItemSelected()
  }

  override fun setBookmarksMenuItemSelected() {
    drawerController.setBookmarksMenuItemSelected()
  }

  private suspend fun restoreFromUrl(): Boolean {
    val data = intent.data
      ?: return false

    Logger.d(TAG, "restoreFromUrl(), url = $data")

    val chanDescriptorResult = siteResolver.resolveChanDescriptorForUrl(data.toString())
    if (chanDescriptorResult == null) {
      Toast.makeText(
        this,
        getString(R.string.open_link_not_matched, AndroidUtils.getApplicationLabel()),
        Toast.LENGTH_LONG
      ).show()

      Logger.d(TAG, "restoreFromUrl() failure")
      return false
    }

    Logger.d(
      TAG, "chanDescriptorResult.descriptor = ${chanDescriptorResult.chanDescriptor}, " +
        "markedPostNo = ${chanDescriptorResult.markedPostNo}"
    )

    val chanDescriptor = chanDescriptorResult.chanDescriptor
    browseController?.setBoard(chanDescriptor.boardDescriptor())

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      if (chanDescriptorResult.markedPostNo > 0L) {
        chanThreadViewableInfoManager.update(chanDescriptor, true) { chanThreadViewableInfo ->
          chanThreadViewableInfo.markedPostNo = chanDescriptorResult.markedPostNo
        }
      }

      browseController?.showThread(chanDescriptor, false)
    }

    Logger.d(TAG, "restoreFromUrl() success")
    return true
  }

  private suspend fun restoreFromSavedState(savedInstanceState: Bundle): Boolean {
    Logger.d(TAG, "restoreFromSavedState()")

    // Restore the activity state from the previously saved state.
    val chanState = savedInstanceState.getParcelable<ChanState>(STATE_KEY)
    if (chanState == null) {
      Logger.w(TAG, "savedInstanceState was not null, but no ChanState was found!")
      return false
    }

    val boardThreadPair = resolveChanState(chanState)
    if (boardThreadPair.first == null) {
      return false
    }

    browseController?.setBoard(boardThreadPair.first!!)

    if (boardThreadPair.second != null) {
      browseController?.showThread(boardThreadPair.second!!, false)
    }

    return true
  }

  private fun resolveChanState(state: ChanState): Pair<BoardDescriptor?, ChanDescriptor.ThreadDescriptor?> {
    val boardDescriptor =
      (resolveChanDescriptor(state.board) as? ChanDescriptor.CatalogDescriptor)?.boardDescriptor
    val threadDescriptor =
      resolveChanDescriptor(state.thread) as? ChanDescriptor.ThreadDescriptor

    return Pair(boardDescriptor, threadDescriptor)
  }

  private fun resolveChanDescriptor(descriptorParcelable: DescriptorParcelable): ChanDescriptor? {
    val chanDescriptor = if (descriptorParcelable.isThreadDescriptor()) {
      ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(descriptorParcelable)
    } else {
      ChanDescriptor.CatalogDescriptor.fromDescriptorParcelable(descriptorParcelable)
    }

    siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
      ?: return null

    boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
      ?: return null

    return chanDescriptor
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun setupLayout() {
    val layoutMode = ChanSettings.getCurrentLayoutMode()

    when (layoutMode) {
      ChanSettings.LayoutMode.SPLIT -> {
        val split = SplitNavigationController(
          this,
          inflate(this, R.layout.layout_split_empty),
          drawerController
        )

        drawerController.pushChildController(split)
        split.setLeftController(mainNavigationController, false)
      }
      ChanSettings.LayoutMode.PHONE,
      ChanSettings.LayoutMode.SLIDE -> {
        drawerController.pushChildController(mainNavigationController)
      }
      ChanSettings.LayoutMode.AUTO -> throw IllegalStateException("Shouldn't happen")
    }

    browseController = BrowseController(this, drawerController)

    if (layoutMode == ChanSettings.LayoutMode.PHONE || layoutMode == ChanSettings.LayoutMode.SLIDE) {
      val slideController = ThreadSlideController(
        this,
        inflate(this, R.layout.layout_split_empty),
        drawerController
      )

      mainNavigationController.pushController(slideController, false)
      slideController.setLeftController(browseController, false)
    } else {
      mainNavigationController.pushController(browseController, false)
    }
  }

  private fun isKnownAction(action: String): Boolean {
    return when (action) {
      NotificationConstants.LAST_PAGE_NOTIFICATION_ACTION -> true
      NotificationConstants.REPLY_NOTIFICATION_ACTION -> true
      Intent.ACTION_SEND -> true
      else -> false
    }
  }

  private suspend fun lastPageNotificationClicked(extras: Bundle) {
    val threadDescriptors = extras.getParcelableArrayList<DescriptorParcelable>(
      NotificationConstants.LastPageNotifications.LP_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY
    )?.map { it -> ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(it) }

    if (threadDescriptors.isNullOrEmpty()) {
      return
    }

    Logger.d(
      TAG,
      "onNewIntent() last page notification clicked, threads count = ${threadDescriptors.size}"
    )

    if (threadDescriptors.size == 1) {
      drawerController.loadThread(
        threadDescriptors.first(),
        closeAllNonMainControllers = true,
        animated = false
      )
    } else {
      drawerController.openBookmarksController(threadDescriptors)
    }
  }

  private suspend fun replyNotificationSwipedAway(extras: Bundle) {
    val threadDescriptors = extras.getParcelableArrayList<DescriptorParcelable>(
      NotificationConstants.ReplyNotifications.R_NOTIFICATION_SWIPE_THREAD_DESCRIPTORS_KEY
    )?.map { it -> ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(it) }

    if (threadDescriptors.isNullOrEmpty()) {
      return
    }

    Logger.d(
      TAG, "onNewIntent() reply notification swiped away, " +
        "marking as seen ${threadDescriptors.size} bookmarks"
    )

    val updatedBookmarkDescriptors = bookmarksManager.updateBookmarks(threadDescriptors) { threadBookmark ->
      threadBookmark.markAsSeenAllReplies()
    }
    bookmarksManager.persistBookmarksManually(updatedBookmarkDescriptors)
  }

  private suspend fun replyNotificationClicked(extras: Bundle) {
    val threadDescriptors = extras.getParcelableArrayList<DescriptorParcelable>(
      NotificationConstants.ReplyNotifications.R_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY
    )?.map { it -> ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(it) }

    if (threadDescriptors.isNullOrEmpty()) {
      return
    }

    Logger.d(
      TAG, "onNewIntent() reply notification clicked, " +
        "marking as seen ${threadDescriptors.size} bookmarks"
    )

    if (threadDescriptors.size == 1) {
      drawerController.loadThread(
        threadDescriptors.first(),
        closeAllNonMainControllers = true,
        animated = false
      )
    } else {
      drawerController.openBookmarksController(threadDescriptors)
    }

    val updatedBookmarkDescriptors = bookmarksManager.updateBookmarks(threadDescriptors) { threadBookmark ->
      threadBookmark.markAsSeenAllReplies()
    }
    bookmarksManager.persistBookmarksManually(updatedBookmarkDescriptors)
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN) {
      drawerController.onMenuClicked()
      return true
    }

    return stack.peek().dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)

    val boardDescriptor = browseController?.chanDescriptor
    if (boardDescriptor == null) {
      Logger.w(TAG, "Can not save instance state, the board loadable is null")
      return
    }

    var threadDescriptor: ChanDescriptor? = null

    if (drawerController.childControllers[0] is SplitNavigationController) {
      val dblNav = drawerController.childControllers[0] as SplitNavigationController

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

  fun pushController(controller: Controller) {
    stack.push(controller)
  }

  fun isControllerAdded(predicate: Function1<Controller, Boolean>): Boolean {
    return stack.any { isControllerPresent(it, predicate) }
  }

  fun getControllerOrNull(predicate: (Controller) -> Boolean): Controller? {
    for (controller in stack) {
      val innerController = findController(controller, predicate)
      if (innerController != null) {
        return innerController
      }
    }

    return null
  }

  private fun findController(
    controller: Controller,
    predicate: (Controller) -> Boolean
  ): Controller? {
    if (predicate(controller)) {
      return controller
    }

    for (childController in controller.childControllers) {
      val innerController = findController(childController, predicate)
      if (innerController != null) {
        return innerController
      }
    }

    return null
  }

  fun popController(controller: Controller?) {
    // we permit removal of things not on the top of the stack, but everything gets shifted down
    // so the top of the stack remains the same
    stack.remove(controller)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    for (controller in stack) {
      controller.onConfigurationChanged(newConfig)
    }

    if (AndroidUtils.isAndroid10() && !ChanSettings.ignoreDarkNightMode.get()) {
      applyLightDarkThemeIfNeeded(newConfig)
    }
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

  override fun onBackPressed() {
    if (stack.peek().onBack()) {
      return
    }

    if (!exitFlag) {
      showToast(this, R.string.action_confirm_exit)
      exitFlag = true
      BackgroundUtils.runOnMainThread({ exitFlag = false }, 650)
    } else {
      exitFlag = false
      super@StartActivity.onBackPressed()
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

  fun restartApp() {
    val intent = Intent(this, StartActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    finish()
    Runtime.getRuntime().exit(0)
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

  private fun setupContext(context: Activity, chanTheme: ChanTheme) {
    val taskDescription = if (AndroidUtils.isAndroidP()) {
      ActivityManager.TaskDescription(
        null,
        R.drawable.ic_stat_notify,
        chanTheme.primaryColor
      )
    } else {
      val taskDescriptionBitmap = BitmapFactory.decodeResource(
        context.resources,
        R.drawable.ic_stat_notify
      )

      ActivityManager.TaskDescription(
        null,
        taskDescriptionBitmap,
        chanTheme.primaryColor
      )
    }

    context.setTaskDescription(taskDescription)
  }

  companion object {
    private const val TAG = "StartActivity"
    private const val STATE_KEY = "chan_state"
  }
}