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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Pair
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import com.airbnb.epoxy.EpoxyController
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.manager.ControllerNavigationManager
import com.github.adamantcheese.chan.core.manager.HistoryNavigationManager
import com.github.adamantcheese.chan.core.manager.UpdateManager
import com.github.adamantcheese.chan.core.manager.WatchManager
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.navigation.RequiresNoBottomNavBar
import com.github.adamantcheese.chan.core.repository.SiteRepository
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.site.SiteResolver
import com.github.adamantcheese.chan.core.site.SiteService
import com.github.adamantcheese.chan.features.drawer.DrawerController
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
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.setupFullscreen
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
  CoroutineScope {

  @Inject
  lateinit var databaseManager: DatabaseManager
  @Inject
  lateinit var watchManager: WatchManager
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
  lateinit var historyNavigationManager: HistoryNavigationManager
  @Inject
  lateinit var controllerNavigationManager: ControllerNavigationManager

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

    if (intentMismatchWorkaround()) {
      return
    }

    launch {
      val start = System.currentTimeMillis()
      onCreateInternal(savedInstanceState)
      val diff = System.currentTimeMillis() - start
      Logger.d(TAG, "StartActivity initialization took " + diff + "ms")
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    if (intentMismatchWorkaround()) {
      return
    }

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

  suspend fun onCreateInternal(savedInstanceState: Bundle?) {
    Chan.inject(this)

    if (BuildConfig.DEV_BUILD) {
      EpoxyController.setGlobalDebugLoggingEnabled(true)
    }

    themeHelper.setupContext(this)
    fileChooser.setCallbacks(this)
    imagePickDelegate = ImagePickDelegate(this)
    runtimePermissionsHelper = RuntimePermissionsHelper(this)
    updateManager = UpdateManager(this)

    contentView = findViewById(android.R.id.content)
    setupFullscreen()

    // Setup base controllers, and decide if to use the split layout for tablets
    drawerController = DrawerController(this).apply {
      onCreate()
      onShow()
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
      coroutineScope {
        launch {
          listenForControllerNavigationChanges()
        }
      }
    }
  }

  private suspend fun listenForControllerNavigationChanges() {
    controllerNavigationManager.listenForControllerNavigationChanges()
      .asFlow()
      .collect { change ->
        when (change) {
          is ControllerNavigationManager.ControllerNavigationChange.Presented,
          is ControllerNavigationManager.ControllerNavigationChange.Unpresented,
          is ControllerNavigationManager.ControllerNavigationChange.Pushed,
          is ControllerNavigationManager.ControllerNavigationChange.Popped -> {
            val hasNoBottomNavBarControllers = stack.any { navController ->
              return@any isControllerPresent(navController) { controller ->
                return@isControllerPresent controller is RequiresNoBottomNavBar
              }
            }

            if (hasNoBottomNavBarControllers) {
              drawerController.hideBottomNavBar(lockTranslation = true, lockCollapse = true)
            } else {
              drawerController.showBottomNavBar(unlockTranslation = true, unlockCollapse = true)
            }
          }
          else -> {
            // no-op
          }
        }
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

    historyNavigationManager.runAfterInitialized {
      val topNavElement = historyNavigationManager.getNavElementAtTop()
      if (topNavElement != null) {
        when (topNavElement) {
          is NavHistoryElement.Catalog -> {
            browseController?.showBoard(topNavElement.descriptor)
          }
          is NavHistoryElement.Thread -> {
            browseController?.loadWithDefaultBoard()
            drawerController.loadThread(topNavElement.descriptor)
          }
        }
      }
    }
  }

  private fun restoreFromUrl(): Boolean {
    var handled = false
    val data = intent.data

    // Start from an url launch.
    if (data != null) {
      val loadableResult = siteResolver.resolveLoadableForUrl(data.toString())
      if (loadableResult != null) {
        handled = true
        loadedFromURL = true

        val loadable = loadableResult.loadable
        browseController?.setBoard(loadable.board)

        if (loadable.isThreadMode) {
          browseController?.showThread(loadable, false)
        }
      } else {
        AlertDialog.Builder(this)
          .setMessage(getString(R.string.open_link_not_matched,
            AndroidUtils.getApplicationLabel()
          ))
          .setPositiveButton(R.string.ok) { _, _ -> AndroidUtils.openLink(data.toString()) }
          .show()
      }
    }

    return handled
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
      browseController?.setBoard(boardThreadPair.first!!.board)

      if (boardThreadPair.second != null) {
        browseController?.showThread(boardThreadPair.second, false)
      }
    }

    return handled
  }

  private fun resolveChanState(state: ChanState): Pair<Loadable?, Loadable?> {
    val boardLoadable = resolveLoadable(state.board, false)
    val threadLoadable = resolveLoadable(state.thread, true)
    return Pair(boardLoadable, threadLoadable)
  }

  private fun resolveLoadable(_stateLoadable: Loadable, forThread: Boolean): Loadable? {
    // invalid (no state saved).
    var stateLoadable = _stateLoadable

    val mode = if (forThread) {
      Loadable.Mode.THREAD
    } else {
      Loadable.Mode.CATALOG
    }

    if (stateLoadable.mode != mode) {
      return null
    }

    val site = siteRepository.forId(stateLoadable.siteId)
      ?: return null

    val board = site.board(stateLoadable.boardCode)
      ?: return null

    stateLoadable.site = site
    stateLoadable.board = board

    if (forThread) {
      // When restarting the parcelable isn't actually deserialized, but the same
      // object instance is reused. This means that the loadables we gave to the
      // state are the same instance, and also have the id set etc. We don't need to
      // query these from the loadablemanager.
      val loadableManager = databaseManager.databaseLoadableManager
      if (stateLoadable.id == 0) {
        stateLoadable = loadableManager[stateLoadable]
      }
    }

    return stateLoadable
  }

  private fun setupLayout() {
    val layoutMode = ChanSettings.getCurrentLayoutMode()

    when (layoutMode) {
      ChanSettings.LayoutMode.SPLIT -> {
        val split = SplitNavigationController(this)
        split.setEmptyView(AndroidUtils.inflate(this, R.layout.layout_split_empty))
        drawerController.setChildController(split)

        split.setDrawerCallbacks(drawerController)
        split.setLeftController(mainNavigationController)
      }
      ChanSettings.LayoutMode.PHONE,
      ChanSettings.LayoutMode.SLIDE -> {
        drawerController.setChildController(mainNavigationController)
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

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)

    // Handle WatchNotification clicks
    if (intent.extras != null) {
      val pinId = intent.extras?.getInt("pin_id", -2) ?: -2
      if (pinId != -2 && mainNavigationController.top is BrowseController) {
        passIntentToBrowseController(pinId)
      } else if (pinId != -2 && mainNavigationController.top is ThreadSlideController) {
        passIntentToThreadSlideController(pinId)
      }
    }
  }

  private fun passIntentToThreadSlideController(pinId: Int) {
    if (pinId == -1) {
      drawerController.onMenuClicked()
      return
    }

    val pin = watchManager.findPinById(pinId)
      ?: return

    val controllers: List<Controller> = mainNavigationController.childControllers

    for (controller in controllers) {
      if (controller is ViewThreadController) {
        controller.loadThread(pin.loadable)
        break
      } else if (controller is ThreadSlideController) {
        if (controller.getRightController() is ViewThreadController) {
          (controller.getRightController() as ViewThreadController).loadThread(pin.loadable)
          controller.switchToController(false)
          break
        } else {
          val viewThreadController = ViewThreadController(this, pin.loadable)
          controller.setRightController(viewThreadController)
          controller.switchToController(false)
          break
        }
      }
    }
  }

  private fun passIntentToBrowseController(pinId: Int) {
    if (pinId == -1) {
      drawerController.onMenuClicked()
      return
    }

    val pin = watchManager.findPinById(pinId)
    if (pin != null) {
      browseController?.showThread(pin.loadable, false)
    }
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

    val board = browseController?.loadable
    if (board == null) {
      Logger.w(TAG, "Can not save instance state, the board loadable is null")
      return
    }

    var thread: Loadable? = null

    if (drawerController.childControllers[0] is SplitNavigationController) {
      val dblNav = drawerController.childControllers[0] as SplitNavigationController

      if (dblNav.getRightController() is NavigationController) {
        val rightNavigationController = dblNav.getRightController() as NavigationController

        for (controller in rightNavigationController.childControllers) {
          if (controller is ViewThreadController) {
            thread = controller.loadable
            break
          }
        }
      }
    } else {
      val controllers: List<Controller> = mainNavigationController.childControllers

      for (controller in controllers) {
        if (controller is ViewThreadController) {
          thread = controller.loadable
          break
        } else if (controller is ThreadSlideController) {
          if (controller.getRightController() is ViewThreadController) {
            thread = (controller.getRightController() as ViewThreadController).loadable
            break
          }
        }
      }
    }

    if (thread == null) {
      // Make the parcel happy
      thread = Loadable.emptyLoadable()
    }

    outState.putParcelable(STATE_KEY, ChanState(board.clone(), thread!!.clone()))
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
      if (predicate.invoke(controller)) {
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