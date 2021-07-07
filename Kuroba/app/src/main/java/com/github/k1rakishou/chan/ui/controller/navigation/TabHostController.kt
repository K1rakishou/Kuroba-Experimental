package com.github.k1rakishou.chan.ui.controller.navigation

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.features.bookmarks.BookmarksController
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.features.filter_watches.FilterWatchesController
import com.github.k1rakishou.chan.features.my_posts.SavedPostsController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTabLayout
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.ui.widget.DisableableLayout
import com.github.k1rakishou.chan.ui.widget.KurobaViewPager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.persist_state.PersistableChanState
import com.google.android.material.tabs.TabLayout

class TabHostController(
  context: Context,
  private val bookmarksToHighlight: List<ChanDescriptor.ThreadDescriptor>,
  private val mainControllerCallbacks: MainControllerCallbacks,
  private val startActivityCallback: StartActivityStartupHandlerHelper.StartActivityCallbacks
) : Controller(context), ToolbarNavigationController.ToolbarSearchCallback, DisableableLayout {
  private lateinit var tabLayout: ColorizableTabLayout
  private lateinit var viewPager: KurobaViewPager

  private var currentPageType: PageType? = null

  private val simpleOnTabSelectedListener = SimpleOnTabSelectedListener { tab -> onTabSelected(tab) }
  private val simpleOnPageChangeListener = SimpleOnPageChangeListener { tabType -> onScrolledToTab(tabType) }

  private val viewPagerAdapter = ControllerAdapter()

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    view = AppModuleAndroidUtils.inflate(context, R.layout.controller_tab_host)

    tabLayout = view.findViewById(R.id.tab_layout)
    viewPager = view.findViewById(R.id.view_pager)
    viewPager.adapter = viewPagerAdapter

    val pageType = getLastOpenedTabPageIndex(bookmarksToHighlight)

    tabLayout.addTab(
      tabLayout.newTab()
        .setText(context.getString(R.string.saved_posts_tab_title))
        .setTag(PageType.SavedPosts),
      pageType == PageType.SavedPosts
    )
    tabLayout.addTab(
      tabLayout.newTab()
        .setText(context.getString(R.string.bookmarks_tab_title))
        .setTag(PageType.Bookmarks),
      pageType == PageType.Bookmarks
    )
    tabLayout.addTab(
      tabLayout.newTab()
        .setText(context.getString(R.string.filter_watches_tab_title))
        .setTag(PageType.FilterWatches),
      pageType == PageType.FilterWatches
    )

    tabLayout.addOnTabSelectedListener(simpleOnTabSelectedListener)
    tabLayout.setDisableableLayoutHandler(this)
    viewPager.addOnPageChangeListener(simpleOnPageChangeListener)
    viewPager.setDisableableLayoutHandler(this)

    onTabWithTypeSelected(pageType)
    viewPagerAdapter.notifyDataSetChanged()
  }

  override fun onDestroy() {
    super.onDestroy()

    tabLayout.removeOnTabSelectedListener(simpleOnTabSelectedListener)
    tabLayout.removeDisableableLayoutHandler()
    viewPager.removeOnPageChangeListener(simpleOnPageChangeListener)
    viewPager.removeDisableableLayoutHandler()

    currentPageType = null
  }

  override fun isLayoutEnabled(): Boolean {
    val topController = top
      ?: return false

    if (navigation.search) {
      return false
    }

    return (topController as TabPageController).canSwitchTabs()
  }

  override fun onSearchVisibilityChanged(visible: Boolean) {
    (top as? ToolbarNavigationController.ToolbarSearchCallback)?.onSearchVisibilityChanged(visible)
  }

  override fun onSearchEntered(entered: String) {
    (top as? ToolbarNavigationController.ToolbarSearchCallback)?.onSearchEntered(entered)
  }

  private fun onScrolledToTab(pageType: PageType) {
    tabLayout.getTabAt(pageType.pageIndex)?.select()
  }

  private fun onTabSelected(tab: TabLayout.Tab) {
    onTabWithTypeSelected(tab.tag as PageType)
  }

  private fun onTabWithTypeSelected(pageType: PageType) {
    if (currentPageType == pageType) {
      return
    }

    currentPageType = pageType

    viewPager.setCurrentItem(pageType.pageIndex, true)
    storeLastOpenedTagPageIndex(pageType)
  }

  inner class ControllerAdapter : PagerAdapter() {
    private var currentPosition = -1

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
      return createController(PageType.fromInt(position))
    }

    override fun setPrimaryItem(container: ViewGroup, position: Int, controller: Any) {
      if (currentPosition == position) {
        return
      }

      currentPosition = position
      controller as TabPageController

      attachController(container, controller)
    }

    override fun destroyItem(container: ViewGroup, position: Int, controller: Any) {
      controller as TabPageController

      removeChildController(controller)
      childControllers.remove(controller)
    }

    override fun isViewFromObject(view: View, controller: Any): Boolean {
      val tabPageController = (controller as TabPageController)

      if (!tabPageController.isViewInitialized()) {
        return false
      }

      return controller.view == view
    }

    override fun getCount(): Int = PageType.values().size

    private fun createController(pageType: PageType): TabPageController {
      return when (pageType) {
        PageType.SavedPosts -> {
          SavedPostsController(
            context = context,
            mainControllerCallbacks = mainControllerCallbacks,
            startActivityCallback = startActivityCallback
          )
        }
        PageType.Bookmarks -> {
          BookmarksController(
            context = context,
            bookmarksToHighlight = bookmarksToHighlight,
            mainControllerCallbacks = mainControllerCallbacks,
            startActivityCallback = startActivityCallback
          )
        }
        PageType.FilterWatches -> {
          FilterWatchesController(
            context = context,
            startActivityCallback = startActivityCallback
          )
        }
      }
    }

    private fun attachController(container: ViewGroup, childController: TabPageController) {
      addChildControllerOrMoveToTop(container, childController)

      val newNavItem = NavigationItem()
      childController.rebuildNavigationItem(newNavItem)
      childController.navigation = newNavItem
      childController.onTabFocused()

      this@TabHostController.navigation = newNavItem
      requireNavController().requireToolbar().setNavigationItem(false, true, newNavItem, null)
    }

  }

  private fun getLastOpenedTabPageIndex(
    bookmarksToHighlight: List<ChanDescriptor.ThreadDescriptor>
  ): PageType {
    if (bookmarksToHighlight.isNotEmpty()) {
      return PageType.Bookmarks
    }

    val tabPageIndex = PersistableChanState.bookmarksLastOpenedTabPageIndex.get()
    return PageType.fromInt(tabPageIndex)
  }

  private fun storeLastOpenedTagPageIndex(pageType: PageType) {
    PersistableChanState.bookmarksLastOpenedTabPageIndex.set(pageType.pageIndex)
  }

  /**
   * This this is persisted in the PersistableChanState. We need to be very careful with it because
   * it may break everything.
   * */
  enum class PageType(val pageIndex: Int) {
    SavedPosts(0),
    Bookmarks(1),
    FilterWatches(2);

    companion object {
      fun fromInt(value: Int): PageType {
        return when (value) {
          0 -> SavedPosts
          1 -> Bookmarks
          2 -> FilterWatches
          else -> Bookmarks
        }
      }
    }
  }

  class SimpleOnPageChangeListener(
    private val onSwitchedToTab: (PageType) -> Unit
  ) : ViewPager.OnPageChangeListener {

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
      // no-op
    }

    override fun onPageSelected(position: Int) {
      onSwitchedToTab(PageType.fromInt(position))
    }

    override fun onPageScrollStateChanged(state: Int) {
      // no-op
    }
  }

  class SimpleOnTabSelectedListener(
    private val onTabSelected: (TabLayout.Tab) -> Unit
  ) : TabLayout.OnTabSelectedListener {
    override fun onTabSelected(tab: TabLayout.Tab) {
      onTabSelected.invoke(tab)
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {
      // no-op
    }

    override fun onTabReselected(tab: TabLayout.Tab) {
      // no-op
    }
  }

  companion object {
    private const val TAG = "TabNavigationController"
  }
}