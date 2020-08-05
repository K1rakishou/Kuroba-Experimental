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
package com.github.adamantcheese.chan

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.CreateNdefMessageCallback
import android.nfc.NfcEvent
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import com.airbnb.epoxy.EpoxyController
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.manager.*
import com.github.adamantcheese.chan.core.navigation.RequiresNoBottomNavBar
import com.github.adamantcheese.chan.core.repository.BoardRepository
import com.github.adamantcheese.chan.core.repository.SiteRepository
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.site.SiteResolver
import com.github.adamantcheese.chan.core.site.SiteService
import com.github.adamantcheese.chan.features.drawer.DrawerController
import com.github.adamantcheese.chan.ui.controller.AlbumViewController
import com.github.adamantcheese.chan.ui.controller.BrowseController
import com.github.adamantcheese.chan.ui.controller.ThreadSlideController
import com.github.adamantcheese.chan.ui.controller.ViewThreadController
import com.github.adamantcheese.chan.ui.controller.navigation.DoubleNavigationController
import com.github.adamantcheese.chan.ui.controller.navigation.NavigationController
import com.github.adamantcheese.chan.ui.controller.navigation.SplitNavigationController
import com.github.adamantcheese.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.adamantcheese.chan.ui.helper.ImagePickDelegate
import com.github.adamantcheese.chan.ui.helper.RuntimePermissionsHelper
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.utils.*
import com.github.adamantcheese.common.updatePaddings
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.DescriptorParcelable
import com.github.adamantcheese.model.data.navigation.NavHistoryElement
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.FSAFActivityCallbacks
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactive.asFlow
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class StartActivity : AppCompatActivity(),
  CreateNdefMessageCallback,
  FSAFActivityCallbacks,
  CoroutineScope,
  StartActivityCallbacks {

  @Inject
  lateinit var databaseManager: DatabaseManager
  @Inject
  lateinit var siteResolver: SiteResolver
  @Inject
  lateinit var siteService: SiteService
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var themeHelper: ThemeHelper
  @Inject
  lateinit var siteRepository: SiteRepository
  @Inject
  lateinit var boardRepository: BoardRepository
  @Inject
  lateinit var historyNavigationManager: HistoryNavigationManager
  @Inject
  lateinit var controllerNavigationManager: ControllerNavigationManager
  @Inject
  lateinit var replyViewStateManager: ReplyViewStateManager
  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val stack = Stack<Controller>()
  private val job = SupervisorJob()

  private var intentMismatchWorkaroundActive = false
  private var exitFlag = false
  private var browseController: BrowseController? = null

  lateinit var contentView: ViewGroup
    private set
  lateinit var imagePickDelegate: ImagePickDelegate
    private set
  lateinit var runtimePermissionsHelper: RuntimePermissionsHelper
    private set
  lateinit var updateManager: UpdateManager
    private set

  private lateinit var mainNavigationController: NavigationController
  private lateinit var drawerController: DrawerController

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("StartActivity")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    launch {
      val start = System.currentTimeMillis()
      onCreateInternal(this, savedInstanceState)
      val diff = System.currentTimeMillis() - start
      Logger.d(TAG, "StartActivity initialization took " + diff + "ms")
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    job.cancel()
    updateManager.onDestroy()
    imagePickDelegate.onDestroy()
    fileChooser.removeCallbacks()

    while (!stack.isEmpty()) {
      val controller = stack.pop()
      controller.onHide()
      controller.onDestroy()
    }
  }

  private suspend fun onCreateInternal(coroutineScope: CoroutineScope, savedInstanceState: Bundle?) {
    Chan.inject(this)

    if (AndroidUtils.getFlavorType() == AndroidUtils.FlavorType.Dev) {
      EpoxyController.setGlobalDebugLoggingEnabled(true)
    }

    themeHelper.setupContext(this)
    fileChooser.setCallbacks(this)
    imagePickDelegate = ImagePickDelegate(this)
    runtimePermissionsHelper = RuntimePermissionsHelper(this)
    updateManager = UpdateManager(this)

    contentView = findViewById(android.R.id.content)

    FullScreenUtils.setupDefaultFlags(window)
    FullScreenUtils.setupFullscreen(this)

    // Setup base controllers, and decide if to use the split layout for tablets
    drawerController = DrawerController(this).apply {
      onCreate()
      onShow()
    }

    ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
      val isKeyboardOpen = FullScreenUtils.isKeyboardAppeared(view, insets.systemWindowInsetBottom)

      globalWindowInsetsManager.updateKeyboardHeight(
        FullScreenUtils.calculateDesiredRealBottomInset(view, insets.systemWindowInsetBottom)
      )

      globalWindowInsetsManager.updateIsKeyboardOpened(isKeyboardOpen)

      globalWindowInsetsManager.updateInsets(
        insets.replaceSystemWindowInsets(
          insets.systemWindowInsetLeft,
          insets.systemWindowInsetTop,
          insets.systemWindowInsetRight,
          FullScreenUtils.calculateDesiredBottomInset(view, insets.systemWindowInsetBottom)
        )
      )

      drawerController.view.updatePaddings(
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

    mainNavigationController = StyledToolbarNavigationController(this)
    setupLayout()

    setContentView(drawerController.view)
    pushController(drawerController)

    drawerController.attachBottomNavViewToToolbar()

    // Prevent overdraw
    // Do this after setContentView, or the decor creating will reset the background to a
    // default non-null drawable
    window.setBackgroundDrawable(null)

    val adapter = NfcAdapter.getDefaultAdapter(this)
    adapter?.setNdefPushMessageCallback(this, this)

    updateManager.autoUpdateCheck()

    if (ChanSettings.fullUserRotationEnable.get()) {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    }

    historyNavigationManager.awaitUntilInitialized()
    setupFromStateOrFreshLaunch(savedInstanceState)

    if (ChanSettings.getCurrentLayoutMode() != ChanSettings.LayoutMode.SPLIT) {
      coroutineScope.launch {
        listenForReplyViewStatesChanges()
      }

      coroutineScope.launch {
        listenForControllerNavigationChanges()
      }
    }

    onNewIntentInternal(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)

    onNewIntentInternal(intent)
  }

  private fun onNewIntentInternal(intent: Intent) {
    val extras = intent.extras
      ?: return
    val action = intent.action
      ?: return

    if (!isKnownAction(action)) {
      return
    }

    launch {
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

  private suspend fun listenForReplyViewStatesChanges() {
    replyViewStateManager.listenForReplyViewsStateUpdates()
      .asFlow()
      .collect {
        if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
          return@collect
        }

        updateBottomNavBar()
      }
  }

  private suspend fun listenForControllerNavigationChanges() {
    controllerNavigationManager.listenForControllerNavigationChanges()
      .asFlow()
      .collect { change ->
        updateBottomNavBarIfNeeded(change)
      }
  }

  private fun updateBottomNavBarIfNeeded(change: ControllerNavigationManager.ControllerNavigationChange?) {
    if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
      return
    }

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
    val hasRequiresNoBottomNavBarControllers = isControllerAdded { controller -> controller is RequiresNoBottomNavBar }
    if (hasRequiresNoBottomNavBarControllers) {
      drawerController.hideBottomNavBar(lockTranslation = true, lockCollapse = true)
    } else if (!replyViewStateManager.anyReplyViewVisible()) {
      drawerController.resetBottomNavViewState(unlockTranslation = true, unlockCollapse = true)
    }
  }

  private fun isControllerPresent(
    controller: Controller,
    predicate: (Controller) -> Boolean
  ): Boolean {
    if (predicate(controller)) {
      return true
    }

    for (childController in controller.childControllers) {
      if (isControllerPresent(childController, predicate)) {
        return true
      }
    }

    return false
  }

  private fun setupFromStateOrFreshLaunch(savedInstanceState: Bundle?) {
    val handled = savedInstanceState?.let { restoreFromSavedState(it) }
      ?: restoreFromUrl()

    // Not from a state or from an url, launch the setup controller if no boards are setup up yet,
    // otherwise load the default saved board.
    if (!handled) {
      restoreFresh()
    }
  }

  private fun restoreFresh() {
    if (!siteService.areSitesSetup()) {
      browseController?.showSitesNotSetup()
      return
    }

    historyNavigationManager.runAfterInitialized { error ->
      if (error != null) {
        throw error
      }

      val topNavElement = historyNavigationManager.getNavElementAtTop()
      if (topNavElement == null) {
        browseController?.loadWithDefaultBoard()
        return@runAfterInitialized
      }

      when (topNavElement) {
        is NavHistoryElement.Catalog -> {
          browseController?.showBoard(topNavElement.descriptor.boardDescriptor)
        }
        is NavHistoryElement.Thread -> {
          val catalogNavElement = historyNavigationManager.getFirstCatalogNavElement()
          if (catalogNavElement != null) {
            require(catalogNavElement is NavHistoryElement.Catalog) {
              "catalogNavElement is not catalog!"
            }

            browseController?.setBoard(catalogNavElement.descriptor.boardDescriptor)
          } else {
            browseController?.loadWithDefaultBoard()
          }

          loadThread(topNavElement.descriptor)
        }
      }
    }
  }

  fun loadThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    drawerController.loadThread(threadDescriptor, true)
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

  private fun restoreFromUrl(): Boolean {
    val data = intent.data
      ?: return false

    val chanDescriptorResult = siteResolver.resolveChanDescriptorForUrl(data.toString())
    if (chanDescriptorResult != null) {
      loadedFromURL = true

      val chanDescriptor = chanDescriptorResult.chanDescriptor
      browseController?.setBoard(chanDescriptor.boardDescriptor())

      if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
        browseController?.showThread(chanDescriptor, false)
      }

      return true
    }

    Toast.makeText(
      this,
      getString(R.string.open_link_not_matched, AndroidUtils.getApplicationLabel()),
      Toast.LENGTH_LONG
    ).show()

    return false
  }

  private fun restoreFromSavedState(savedInstanceState: Bundle): Boolean {
    var handled = false

    // Restore the activity state from the previously saved state.
    val chanState = savedInstanceState.getParcelable<ChanState>(STATE_KEY)
    if (chanState == null) {
      Logger.w(TAG, "savedInstanceState was not null, but no ChanState was found!")
      return handled
    }

    val boardThreadPair = resolveChanState(chanState)
    if (boardThreadPair.first != null) {
      handled = true
      browseController?.setBoard(boardThreadPair.first!!)

      if (boardThreadPair.second != null) {
        browseController?.showThread(boardThreadPair.second!!, false)
      }
    }

    return handled
  }

  private fun resolveChanState(state: ChanState): Pair<BoardDescriptor?, ChanDescriptor.ThreadDescriptor?> {
    val boardDescriptor =
      (resolveChanDescriptor(state.board) as ChanDescriptor.CatalogDescriptor).boardDescriptor
    val threadDescriptor =
      resolveChanDescriptor(state.thread) as ChanDescriptor.ThreadDescriptor

    return Pair(boardDescriptor, threadDescriptor)
  }

  private fun resolveChanDescriptor(descriptorParcelable: DescriptorParcelable): ChanDescriptor? {
    val chanDescriptor = if (descriptorParcelable.isThreadDescriptor()) {
      ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(descriptorParcelable)
    } else {
      ChanDescriptor.CatalogDescriptor.fromDescriptorParcelable(descriptorParcelable)
    }

    siteRepository.bySiteDescriptor(chanDescriptor.siteDescriptor())
      ?: return null

    boardRepository.getFromBoardDescriptor(chanDescriptor.boardDescriptor())
      ?: return null

    return chanDescriptor
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun setupLayout() {
    val layoutMode = ChanSettings.getCurrentLayoutMode()

    when (layoutMode) {
      ChanSettings.LayoutMode.SPLIT -> {
        val split = SplitNavigationController(this)
        split.setEmptyView(AndroidUtils.inflate(this, R.layout.layout_split_empty))
        drawerController.pushChildController(split)

        split.setDrawerCallbacks(drawerController)
        split.setLeftController(mainNavigationController)
      }
      ChanSettings.LayoutMode.PHONE,
      ChanSettings.LayoutMode.SLIDE -> {
        drawerController.pushChildController(mainNavigationController)
      }
      ChanSettings.LayoutMode.AUTO -> throw IllegalStateException("Shouldn't happen")
    }

    browseController = BrowseController(this)

    if (layoutMode == ChanSettings.LayoutMode.SLIDE) {
      val slideController = ThreadSlideController(this)
      slideController.setEmptyView(AndroidUtils.inflate(this, R.layout.layout_split_empty))
      mainNavigationController.pushController(slideController, false)

      slideController.setDrawerCallbacks(drawerController)
      slideController.setLeftController(browseController)
    } else {
      mainNavigationController.pushController(browseController, false)
    }

    browseController!!.setDrawerCallbacks(drawerController)
  }

  private fun isKnownAction(action: String): Boolean {
    return when (action) {
      NotificationConstants.LAST_PAGE_NOTIFICATION_ACTION -> true
      NotificationConstants.REPLY_NOTIFICATION_ACTION -> true
      else -> false
    }
  }

  private fun lastPageNotificationClicked(extras: Bundle) {
    val threadDescriptors = extras.getParcelableArrayList<DescriptorParcelable>(
      NotificationConstants.LastPageNotifications.LP_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY
    )?.map { it -> ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(it) }

    if (threadDescriptors.isNullOrEmpty()) {
      return
    }

    Logger.d(TAG, "onNewIntent() last page notification clicked, threads count = ${threadDescriptors.size}")

    if (threadDescriptors.size == 1) {
      drawerController.loadThread(threadDescriptors.first(), true)
    } else {
      drawerController.openBookmarksController(threadDescriptors)
    }
  }

  private fun replyNotificationSwipedAway(extras: Bundle) {
    val threadDescriptors = extras.getParcelableArrayList<DescriptorParcelable>(
      NotificationConstants.ReplyNotifications.R_NOTIFICATION_SWIPE_THREAD_DESCRIPTORS_KEY
    )?.map { it -> ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(it) }

    if (threadDescriptors.isNullOrEmpty()) {
      return
    }

    Logger.d(TAG, "onNewIntent() reply notification swiped away, " +
      "marking as seen ${threadDescriptors.size} bookmarks")

    bookmarksManager.updateBookmarks(
      threadDescriptors,
      BookmarksManager.NotifyListenersOption.NotifyEager
    ) { threadBookmark -> threadBookmark.markAsSeenAllReplies() }
  }

  private fun replyNotificationClicked(extras: Bundle) {
    val threadDescriptors = extras.getParcelableArrayList<DescriptorParcelable>(
      NotificationConstants.ReplyNotifications.R_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY
    )?.map { it -> ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(it) }

    if (threadDescriptors.isNullOrEmpty()) {
      return
    }

    Logger.d(TAG, "onNewIntent() reply notification clicked, " +
      "marking as seen ${threadDescriptors.size} bookmarks")

    if (threadDescriptors.size == 1) {
      drawerController.loadThread(threadDescriptors.first(), true)
    } else {
      drawerController.openBookmarksController(threadDescriptors)
    }

    bookmarksManager.updateBookmarks(
      threadDescriptors,
      BookmarksManager.NotifyListenersOption.NotifyEager
    ) { threadBookmark -> threadBookmark.markAsSeenAllReplies() }
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

  override fun createNdefMessage(event: NfcEvent): NdefMessage? {
    var threadController: Controller? = null

    if (drawerController.childControllers[0] is DoubleNavigationController) {
      val splitNavigationController = drawerController.childControllers[0] as SplitNavigationController

      if (splitNavigationController.rightController is NavigationController) {
        val rightNavigationController = splitNavigationController.rightController as NavigationController

        for (controller in rightNavigationController.childControllers) {
          if (controller is CreateNdefMessageCallback) {
            threadController = controller
            break
          }
        }
      }
    }

    if (threadController == null) {
      threadController = mainNavigationController.top
    }

    return if (threadController is CreateNdefMessageCallback) {
      (threadController as CreateNdefMessageCallback).createNdefMessage(event)
    } else {
      null
    }
  }

  fun pushController(controller: Controller) {
    stack.push(controller)
  }

  fun isControllerAdded(predicate: Function1<Controller, Boolean>): Boolean {
    for (controller in stack) {
      if (isControllerPresent(controller, predicate)) {
        return true
      }
    }

    return false
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
  }

  override fun onBackPressed() {
    if (stack.peek().onBack()) {
      return
    }

    if (!exitFlag) {
      AndroidUtils.showToast(this, R.string.action_confirm_exit)
      exitFlag = true
      BackgroundUtils.runOnMainThread({ exitFlag = false }, 650)
    } else {
      exitFlag = false
      super@StartActivity.onBackPressed()
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int, permissions: Array<String>, grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    runtimePermissionsHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (fileChooser.onActivityResult(requestCode, resultCode, data)) {
      return
    }

    imagePickDelegate.onActivityResult(requestCode, resultCode, data)
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

  companion object {
    private const val TAG = "StartActivity"
    private const val STATE_KEY = "chan_state"
    var loadedFromURL = false
  }
}