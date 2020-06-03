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
package com.github.adamantcheese.chan.features.drawer

import android.content.Context
import android.content.res.ColorStateList
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.epoxy.EpoxyRecyclerView
import com.airbnb.epoxy.EpoxyTouchHelper
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.manager.GlobalWindowInsetsManager
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager
import com.github.adamantcheese.chan.core.manager.WatchManager
import com.github.adamantcheese.chan.core.manager.WatchManager.PinMessages.*
import com.github.adamantcheese.chan.core.model.orm.Pin
import com.github.adamantcheese.chan.core.model.orm.PinType
import com.github.adamantcheese.chan.features.drawer.epoxy.EpoxyHistoryEntryView
import com.github.adamantcheese.chan.features.drawer.epoxy.EpoxyHistoryEntryViewModel_
import com.github.adamantcheese.chan.features.drawer.epoxy.epoxyHistoryEntryView
import com.github.adamantcheese.chan.features.settings.MainSettingsControllerV2
import com.github.adamantcheese.chan.ui.adapter.DrawerAdapter
import com.github.adamantcheese.chan.ui.adapter.DrawerAdapter.HeaderAction
import com.github.adamantcheese.chan.ui.controller.HistoryController
import com.github.adamantcheese.chan.ui.controller.ThreadController
import com.github.adamantcheese.chan.ui.controller.ThreadSlideController
import com.github.adamantcheese.chan.ui.controller.navigation.*
import com.github.adamantcheese.chan.ui.epoxy.epoxyErrorView
import com.github.adamantcheese.chan.ui.epoxy.epoxyLoadingView
import com.github.adamantcheese.chan.ui.epoxy.epoxyTextView
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.ui.view.HidingBottomNavigationView
import com.github.adamantcheese.chan.ui.widget.SimpleEpoxySwipeCallbacks
import com.github.adamantcheese.chan.utils.*
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import javax.inject.Inject

class DrawerController(
  context: Context
) : Controller(context), DrawerView, DrawerAdapter.Callback, View.OnClickListener {

  @Inject
  lateinit var watchManager: WatchManager
  @Inject
  lateinit var settingsNotificationManager: SettingsNotificationManager
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var themeHelper: ThemeHelper

  private lateinit var rootLayout: FrameLayout
  private lateinit var container: FrameLayout
  private lateinit var drawerLayout: DrawerLayout
  private lateinit var drawer: LinearLayout
  private lateinit var epoxyRecyclerView: EpoxyRecyclerView
  private lateinit var drawerAdapter: DrawerAdapter
  private lateinit var bottomNavView: HidingBottomNavigationView

  private val drawerPresenter = DrawerPresenter()

  private val topThreadController: ThreadController?
    get() {
      val nav = mainToolbarNavigationController

      if (nav.top is ThreadController) {
        return nav.top as ThreadController?
      }

      if (nav.top is ThreadSlideController) {
        val slideNav = nav.top as ThreadSlideController?

        if (slideNav?.leftController is ThreadController) {
          return slideNav.leftController as ThreadController
        }
      }

      return null
    }

  private val mainToolbarNavigationController: ToolbarNavigationController
    get() {
      var navigationController: ToolbarNavigationController? = null
      val topController = top!!

      if (topController is StyledToolbarNavigationController) {
        navigationController = topController
      } else if (topController is SplitNavigationController) {
        if (topController.getLeftController() is StyledToolbarNavigationController) {
          navigationController = topController.getLeftController() as StyledToolbarNavigationController
        }
      } else if (topController is ThreadSlideController) {
        navigationController = topController.leftController as StyledToolbarNavigationController
      }

      checkNotNull(navigationController) {
        "The child controller of a DrawerController must either be StyledToolbarNavigationController " +
          "or an DoubleNavigationController that has a ToolbarNavigationController."
      }

      return navigationController
    }

  override fun onCreate() {
    super.onCreate()

    Chan.inject(this)
    EventBus.getDefault().register(this)

    view = AndroidUtils.inflate(context, R.layout.controller_navigation_drawer)
    rootLayout = view.findViewById(R.id.root_layout)
    container = view.findViewById(R.id.container)
    drawerLayout = view.findViewById(R.id.drawer_layout)
    drawerLayout.setDrawerShadow(R.drawable.panel_shadow, GravityCompat.START)
    drawer = view.findViewById(R.id.drawer)
    epoxyRecyclerView = view.findViewById(R.id.drawer_recycler_view)

    bottomNavView = view.findViewById(R.id.bottom_navigation_view)
    bottomNavView.selectedItemId = R.id.action_browse

    bottomNavView.itemIconTintList = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
      intArrayOf(themeHelper.theme.textPrimary, themeHelper.theme.textSecondary)
    )
    bottomNavView.itemTextColor = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
      intArrayOf(themeHelper.theme.textPrimary, themeHelper.theme.textSecondary)
    )

    bottomNavView.setOnNavigationItemSelectedListener { menuItem ->
      onNavigationItemSelectedListener(menuItem)
      return@setOnNavigationItemSelectedListener true
    }

//  TODO(KurobaEx): pins, move to some other place
//  recyclerView = view.findViewById(R.id.drawer_recycler_view);
//  recyclerView.setHasFixedSize(true);
//  recyclerView.setLayoutManager(new LinearLayoutManager(context));
//  recyclerView.getRecycledViewPool().setMaxRecycledViews(TYPE_PIN, 0);
//
//  drawerAdapter = new DrawerAdapter(this, context);
//  recyclerView.setAdapter(drawerAdapter);

//  ItemTouchHelper itemTouchHelper = new ItemTouchHelper(drawerAdapter.getItemTouchHelperCallback());
//  itemTouchHelper.attachToRecyclerView(recyclerView);

    updateBadge()

    ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
      globalWindowInsetsManager.updateInsets(insets)

      view.updateMargins(
        globalWindowInsetsManager.left(),
        globalWindowInsetsManager.right(),
        globalWindowInsetsManager.left(),
        globalWindowInsetsManager.right(),
        globalWindowInsetsManager.top(),
        globalWindowInsetsManager.bottom()
      )

      return@setOnApplyWindowInsetsListener insets.replaceSystemWindowInsets(0, 0, 0, 0)
    }

    ViewCompat.requestApplyInsets(rootLayout)

    EpoxyTouchHelper
      .initSwiping(epoxyRecyclerView)
      .right()
      .withTarget(EpoxyHistoryEntryViewModel_::class.java)
      .andCallbacks(object : SimpleEpoxySwipeCallbacks<EpoxyHistoryEntryViewModel_>() {
        override fun onSwipeCompleted(
          model: EpoxyHistoryEntryViewModel_,
          itemView: View?,
          position: Int,
          direction: Int
        ) {
          super.onSwipeCompleted(model, itemView, position, direction)

          drawerPresenter.onNavElementSwipedAway(model.descriptor())
        }
      })

    compositeDisposable += drawerPresenter.listenForStateChanges()
      .subscribe(
        { state -> onDrawerStateChanged(state) },
        { error -> Logger.e(TAG, "Unknown error subscribed to listenForStateChanges()", error) }
      )

    // TODO(KurobaEx): pins, move to some other place
//    compositeDisposable += settingsNotificationManager.listenForNotificationUpdates()
//      .subscribe(
//        { drawerAdapter.onNotificationsChanged() },
//        { error -> Logger.e(TAG, "Unknown error while subscribed to listenForNotificationUpdates()", error) }
//      )

    // Must be called after drawerPresenter.listenForStateChanges() so it receives the "Loading"
    // state as well as other states
    drawerPresenter.onCreate(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    drawerPresenter.onDestroy()
    compositeDisposable.clear()

    // TODO(KurobaEx): pins, move to some other place
//        recyclerView.setAdapter(null);

    EventBus.getDefault().unregister(this)
  }

  fun setChildController(childController: Controller) {
    addChildController(childController)
    childController.attachToParentView(container)
    childController.onShow()
  }

  fun attachBottomNavViewToToolbar() {
    val topController = top
      ?: return

    if (topController is ToolbarNavigationController) {
      val toolbar = topController.toolbar

      if (toolbar != null) {
        bottomNavView.setToolbar(toolbar)
      }
    }
  }

  fun loadThread(descriptor: ChanDescriptor.ThreadDescriptor) {
    topThreadController?.showThread(descriptor)
  }

  private fun onNavigationItemSelectedListener(menuItem: MenuItem) {
    when (menuItem.itemId) {
      R.id.action_browse -> closeAllNonMainControllers()
      R.id.action_bookmarks -> openController(HistoryController(context));
      R.id.action_settings -> openController(MainSettingsControllerV2(context))
    }
  }

  private fun closeAllNonMainControllers() {
    val currentNavController = top
      ?: return

    while (true) {
      val topController = currentNavController.top
        ?: return

      if (topController is HasNavigation) {
        return
      }

      if (currentNavController is NavigationController) {
        currentNavController.popController(false)
      } else if (currentNavController is DoubleNavigationController) {
        currentNavController.popController(false)
      }
    }
  }

  private fun openController(controller: Controller) {
    val topController = top
      ?: return

    if (topController is NavigationController) {
      closeAllNonMainControllers()

      topController.pushController(controller, false)
    } else if (topController is DoubleNavigationController) {
      closeAllNonMainControllers()

      topController.pushController(controller, false)
    }

    drawerLayout.closeDrawer(GravityCompat.START)
  }

  override fun onClick(v: View) {
    // no-op
  }

  fun onMenuClicked() {
    val topController = mainToolbarNavigationController.top
      ?: return

    if (topController.navigation.hasDrawer) {
      drawerLayout.openDrawer(drawer)
    }
  }

  override fun onBack(): Boolean {
    return if (drawerLayout.isDrawerOpen(drawer)) {
      drawerLayout.closeDrawer(drawer)
      true
    } else {
      super.onBack()
    }
  }

  fun resetBottomNavViewCheckState() {
    BackgroundUtils.ensureMainThread()

    // Hack! To reset the bottomNavView's checked item to "browse" when pressing back one either
    // of the bottomNavView's child controllers (Bookmarks or Settings)
    bottomNavView.menu.findItem(R.id.action_browse)?.isChecked = true
  }

  private fun onDrawerStateChanged(state: HistoryControllerState) {
    epoxyRecyclerView.withModels {
      addOneshotModelBuildListener {
        val llm = (epoxyRecyclerView.layoutManager as LinearLayoutManager)

        if (llm.findFirstCompletelyVisibleItemPosition() <= 1) {
          // Scroll to the top of the nav history list if the previous fully visible item's position
          // was either 0 or 1
          llm.scrollToPosition(0)
        }
      }

      when (state) {
        HistoryControllerState.Loading -> {
          epoxyLoadingView {
            id("history_loading_view")
          }
        }
        HistoryControllerState.Empty -> {
          epoxyTextView {
            id("history_is_empty_text_view")
            message(context.getString(R.string.navigation_history_is_empty))
          }
        }
        is HistoryControllerState.Error -> {
          epoxyErrorView {
            id("history_error_view")
            errorMessage(state.errorText)
          }
        }
        is HistoryControllerState.Data -> {
          state.navHistoryEntryList.forEach { navHistoryEntry ->
            val requestData = EpoxyHistoryEntryView.ImageLoaderRequestData(navHistoryEntry.thumbnailUrl)

            epoxyHistoryEntryView {
              id("navigation_history_entry_${navHistoryEntry.hashCode()}")
              descriptor(navHistoryEntry.descriptor)
              imageLoaderRequestData(requestData)
              title(navHistoryEntry.title)
              clickListener {
                onHistoryEntryViewClicked(navHistoryEntry)
              }
            }
          }
        }
      }
    }
  }

  private fun onHistoryEntryViewClicked(navHistoryEntry: NavigationHistoryEntry) {
    topThreadController?.let { threadController ->
      val isCurrentlyVisible = drawerPresenter.isCurrentlyVisible(navHistoryEntry.descriptor)
      if (!isCurrentlyVisible) {
        when (val descriptor = navHistoryEntry.descriptor) {
          is ChanDescriptor.ThreadDescriptor -> {
            threadController.showThread(descriptor)
          }
          is ChanDescriptor.CatalogDescriptor -> {
            threadController.showBoard(descriptor)
          }
        }
      }
    }

    if (drawerLayout.isDrawerOpen(drawer)) {
      drawerLayout.closeDrawer(drawer)
    }
  }

  override fun onPinClicked(pin: Pin) {
    // TODO(KurobaEx): pins, move to some other place
//        drawerLayout.post(() -> drawerLayout.post(() -> drawerLayout.closeDrawer(drawer)));
//
//        ThreadController threadController = getTopThreadController();
//        if (threadController != null) {
//            Loadable.LoadableDownloadingState state = Loadable.LoadableDownloadingState.NotDownloading;
//
//            if (PinType.hasDownloadFlag(pin.pinType)) {
//                // Try to load saved copy of a thread if pinned thread has an error flag but only if
//                // we are downloading this thread. Otherwise it will break archived threads that are not
//                // being downloaded
//                SavedThread savedThread = watchManager.findSavedThreadByLoadableId(pin.loadable.id);
//                if (savedThread != null) {
//                    // Do not check for isArchived here since we don't want to show archived threads
//                    // as local threads
//                    if (pin.isError) {
//                        state = Loadable.LoadableDownloadingState.AlreadyDownloaded;
//                    } else {
//                        if (savedThread.isFullyDownloaded) {
//                            state = Loadable.LoadableDownloadingState.AlreadyDownloaded;
//                        } else {
//                            boolean hasNoNetwork = !isConnected(ConnectivityManager.TYPE_MOBILE) && !isConnected(
//                                    ConnectivityManager.TYPE_WIFI);
//
//                            if (hasNoNetwork) {
//                                // No internet connection, but we have a local copy of this thread,
//                                // so show it instead of an empty screen.
//                                state = DownloadingAndViewable;
//                            } else {
//                                state = DownloadingAndNotViewable;
//                            }
//                        }
//                    }
//                }
//            }
//
//            pin.loadable.setLoadableState(state);
//            threadController.openPin(pin);
//        }
  }

  override fun onWatchCountClicked(pin: Pin) {
    // TODO(KurobaEx): pins, move to some other place
//        watchManager.toggleWatch(pin);
  }

  override fun onHeaderClicked(headerAction: HeaderAction) {
    // TODO(KurobaEx): pins, move to some other place

//        if (headerAction == DrawerAdapter.HeaderAction.CLEAR || headerAction == DrawerAdapter.HeaderAction.CLEAR_ALL) {
//            final boolean all =
//                    headerAction == DrawerAdapter.HeaderAction.CLEAR_ALL || !ChanSettings.watchEnabled.get();
//            final boolean hasDownloadFlag = watchManager.hasAtLeastOnePinWithDownloadFlag();
//
//            if (all && hasDownloadFlag) {
//                // Some pins may have threads that have saved copies on the disk. We want to warn the
//                // user that this action will delete them as well
//                new AlertDialog.Builder(context).setTitle(R.string.warning)
//                        .setMessage(R.string.drawer_controller_at_least_one_pin_has_download_flag)
//                        .setNegativeButton(R.string.drawer_controller_do_not_delete,
//                                (dialog, which) -> dialog.dismiss()
//                        )
//                        .setPositiveButton(R.string.drawer_controller_delete_all_pins,
//                                ((dialog, which) -> onHeaderClickedInternal(true, true))
//                        )
//                        .create()
//                        .show();
//                return;
//            }
//
//            onHeaderClickedInternal(all, hasDownloadFlag);
//        }
  }

  // TODO(KurobaEx): pins, move to some other place
  private fun onHeaderClickedInternal(all: Boolean, hasDownloadFlag: Boolean) {
  //        final List<Pin> pins = watchManager.clearPins(all);
  //        if (!pins.isEmpty()) {
  //            if (!hasDownloadFlag) {
  //                // We can't undo this operation when there is at least one pin that downloads a thread
  //                // because we will be deleting files from the disk. We don't want to warn the user
  //                // every time he deletes one pin.
  //                String text = getQuantityString(R.plurals.bookmark, pins.size(), pins.size());
  //                Snackbar snackbar = Snackbar.make(
  //                        drawerLayout,
  //                        getString(R.string.drawer_pins_cleared, text),
  //                        4000
  //                );
  //
  //                snackbar.setGestureInsetBottomIgnored(true);
  //                fixSnackbarText(context, snackbar);
  //                fixSnackbarInsets(snackbar, globalWindowInsetsManager);
  //                snackbar.setAction(R.string.undo, v -> watchManager.addAll(pins));
  //                snackbar.show();
  //            }
  //        } else {
  //            int text = watchManager.getAllPins().isEmpty()
  //                    ? R.string.drawer_pins_non_cleared
  //                    : R.string.drawer_pins_non_cleared_try_all;
  //            Snackbar snackbar = Snackbar.make(drawerLayout, text, Snackbar.LENGTH_LONG);
  //            snackbar.setGestureInsetBottomIgnored(true);
  //            fixSnackbarText(context, snackbar);
  //            fixSnackbarInsets(snackbar, globalWindowInsetsManager);
  //            snackbar.show();
  //        }
      }

  override fun onPinRemoved(pin: Pin) {
    // TODO(KurobaEx): pins, move to some other place

//        final Pin undoPin = pin.clone();
//        watchManager.deletePin(pin);
//
//        Snackbar snackbar;
//
//        if (!PinType.hasDownloadFlag(pin.pinType)) {
//            snackbar = Snackbar.make(drawerLayout,
//                    getString(R.string.drawer_pin_removed, pin.loadable.title),
//                    Snackbar.LENGTH_LONG
//            );
//
//            snackbar.setAction(R.string.undo, v -> watchManager.createPin(undoPin));
//        } else {
//            snackbar = Snackbar.make(drawerLayout,
//                    getString(R.string.drawer_pin_with_saved_thread_removed, pin.loadable.title),
//                    Snackbar.LENGTH_LONG
//            );
//        }
//        snackbar.setGestureInsetBottomIgnored(true);
//        fixSnackbarText(context, snackbar);
//        fixSnackbarInsets(snackbar, globalWindowInsetsManager);
//        snackbar.show();
  }

  fun setPinHighlighted(pin: Pin?) {
    // TODO(KurobaEx): pins, move to some other place
//        drawerAdapter.setPinHighlighted(pin);
//        drawerAdapter.updateHighlighted(recyclerView);
  }

  @Subscribe
  fun onEvent(message: PinAddedMessage?) {
    // TODO(KurobaEx): pins, move to some other place

//        drawerAdapter.onPinAdded(message.pin);
//        if (ChanSettings.drawerAutoOpenCount.get() < 5 || ChanSettings.alwaysOpenDrawer.get()) {
//            drawerLayout.openDrawer(drawer);
//            //max out at 5
//            int curCount = ChanSettings.drawerAutoOpenCount.get();
//            ChanSettings.drawerAutoOpenCount.set(Math.min(curCount + 1, 5));
//            if (ChanSettings.drawerAutoOpenCount.get() < 5 && !ChanSettings.alwaysOpenDrawer.get()) {
//                int countLeft = 5 - ChanSettings.drawerAutoOpenCount.get();
//                showToast(context,
//                        "Drawer will auto-show " + countLeft + " more time" + (countLeft == 1 ? "" : "s")
//                                + " as a reminder."
//                );
//            }
//        }
//        updateBadge();
  }

  @Subscribe
  fun onEvent(message: PinRemovedMessage?) {
    // TODO(KurobaEx): pins, move to some other place
//        drawerAdapter.onPinRemoved(message.index);
//        updateBadge();
  }

  @Subscribe
  fun onEvent(message: PinChangedMessage?) {
    // TODO(KurobaEx): pins, move to some other place

//        drawerAdapter.onPinChanged(recyclerView, message.pin);
//        updateBadge();
  }

  @Subscribe
  fun onEvent(message: PinsChangedMessage?) {
    // TODO(KurobaEx): pins, move to some other place

//        drawerAdapter.notifyDataSetChanged();
//        updateBadge();
  }

  fun setDrawerEnabled(enabled: Boolean) {
    val lockMode = if (enabled) {
      DrawerLayout.LOCK_MODE_UNLOCKED
    } else {
      DrawerLayout.LOCK_MODE_LOCKED_CLOSED
    }

    drawerLayout.setDrawerLockMode(lockMode, GravityCompat.START)

    if (!enabled) {
      drawerLayout.closeDrawer(drawer)
    }
  }

  private fun updateBadge() {
    var total = 0
    var color = false

    for (pin in watchManager.watchingPins) {
      if (!PinType.hasWatchNewPostsFlag(pin.pinType)) {
        continue
      }

      total += pin.newPostCount
      color = color or (pin.newQuoteCount > 0)
    }

    if (top != null) {
      mainToolbarNavigationController.toolbar?.arrowMenuDrawable?.setBadge(total, color)
    }
  }

  companion object {
    private const val TAG = "DrawerController"
  }
}