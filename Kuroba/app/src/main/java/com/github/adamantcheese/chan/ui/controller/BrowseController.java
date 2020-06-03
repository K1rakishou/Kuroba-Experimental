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
package com.github.adamantcheese.chan.ui.controller;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.HistoryNavigationManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.presenter.BrowsePresenter;
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.ui.controller.navigation.SplitNavigationController;
import com.github.adamantcheese.chan.ui.controller.navigation.StyledToolbarNavigationController;
import com.github.adamantcheese.chan.ui.controller.navigation.ToolbarNavigationController;
import com.github.adamantcheese.chan.ui.helper.BoardHelper;
import com.github.adamantcheese.chan.ui.helper.HintPopup;
import com.github.adamantcheese.chan.ui.layout.BrowseBoardsFloatingMenu;
import com.github.adamantcheese.chan.ui.layout.ThreadLayout;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuSubItem;
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor;

import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLinkInBrowser;
import static com.github.adamantcheese.chan.utils.AndroidUtils.shareLink;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class BrowseController
        extends ThreadController
        implements ThreadLayout.ThreadLayoutCallback, BrowsePresenter.Callback, BrowseBoardsFloatingMenu.ClickCallback,
        ThreadSlideController.SlideChangeListener {
    private static final int ACTION_CHANGE_VIEW_MODE = 901;
    private static final int ACTION_SORT = 902;
    private static final int ACTION_REPLY = 903;
    private static final int ACTION_OPEN_BROWSER = 904;
    private static final int ACTION_SHARE = 905;
    private static final int ACTION_SCROLL_TO_TOP = 906;
    private static final int ACTION_SCROLL_TO_BOTTOM = 907;

    private static final int SORT_MODE_BUMP = 1000;
    private static final int SORT_MODE_REPLY = 1001;
    private static final int SORT_MODE_IMAGE = 1002;
    private static final int SORT_MODE_NEWEST = 1003;
    private static final int SORT_MODE_OLDEST = 1004;
    private static final int SORT_MODE_MODIFIED = 1005;
    private static final int SORT_MODE_ACTIVITY = 1006;

    private DatabaseLoadableManager databaseLoadableManager;

    @Inject
    BrowsePresenter presenter;
    @Inject
    ThemeHelper themeHelper;
    @Inject
    BoardRepository boardRepository;
    @Inject
    DatabaseManager databaseManager;
    @Inject
    HistoryNavigationManager historyNavigationManager;

    private PostsFilter.Order order;
    @Nullable
    private HintPopup hint = null;
    public String searchQuery = null;

    public BrowseController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        databaseLoadableManager = databaseManager.getDatabaseLoadableManager();

        // Initialization
        order = PostsFilter.Order.find(ChanSettings.boardOrder.get());
        threadLayout.setPostViewMode(ChanSettings.boardViewMode.get());
        threadLayout.presenter.setOrder(order);

        // Navigation
        initNavigation();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (hint != null) {
            hint.dismiss();
            hint = null;
        }

        presenter.destroy();
    }

    @Override
    public void showSitesNotSetup() {
        super.showSitesNotSetup();

        if (hint != null) {
            hint.dismiss();
            hint = null;
        }

        View hintView = getToolbar().findViewById(R.id.title_container);
        hint = HintPopup.show(context, hintView, R.string.thread_empty_setup_hint);
        hint.alignCenter();
        hint.wiggle();
    }

    @Override
    public void setBoard(Board board) {
        presenter.setBoard(board);
    }

    public void loadWithDefaultBoard() {
        presenter.loadWithDefaultBoard(false);
    }

    private void initNavigation() {
        // Navigation item
        navigation.hasDrawer = true;

        navigation.setMiddleMenu(anchor -> {
            BrowseBoardsFloatingMenu boardsFloatingMenu = new BrowseBoardsFloatingMenu(context);
            boardsFloatingMenu.show(view, anchor, BrowseController.this, presenter.currentBoard());
        });

        // Toolbar menu
        navigation.hasBack = false;

        // this controller is used for catalog views; displaying things on two rows for them middle
        // menu is how we want it done these need to be setup before the view is rendered,
        // otherwise the subtitle view is removed
        navigation.title = "App Setup";
        navigation.subtitle = "Tap for site/board setup";

        buildMenu();

        // Presenter
        presenter.create(this);
    }

    private void buildMenu() {
        NavigationItem.MenuBuilder menuBuilder = navigation.buildMenu()
                .withItem(R.drawable.ic_search_white_24dp, this::searchClicked)
                .withItem(R.drawable.ic_refresh_white_24dp, this::reloadClicked);

        NavigationItem.MenuOverflowBuilder overflowBuilder =
                menuBuilder.withOverflow(navigationController);

        if (!ChanSettings.enableReplyFab.get()) {
            overflowBuilder.withSubItem(ACTION_REPLY, R.string.action_reply, this::replyClicked);
        }

        int modeStringId = ChanSettings.boardViewMode.get() == ChanSettings.PostViewMode.LIST
                ? R.string.action_switch_catalog
                : R.string.action_switch_board;

        PostsFilter.Order currentOrder = PostsFilter.Order.find(ChanSettings.boardOrder.get());
        if (currentOrder == null) {
            currentOrder = PostsFilter.Order.BUMP;
        }

        overflowBuilder
                .withSubItem(ACTION_CHANGE_VIEW_MODE, modeStringId, this::viewModeClicked)
                .withNestedOverflow(ACTION_SORT, R.string.action_sort, true)
                .addNestedItem(
                        SORT_MODE_BUMP,
                        R.string.order_bump,
                        true,
                        currentOrder == PostsFilter.Order.BUMP,
                        PostsFilter.Order.BUMP,
                        this::onSortItemClicked
                )
                .addNestedItem(
                        SORT_MODE_REPLY,
                        R.string.order_reply,
                        true,
                        currentOrder == PostsFilter.Order.REPLY,
                        PostsFilter.Order.REPLY,
                        this::onSortItemClicked
                )
                .addNestedItem(
                        SORT_MODE_IMAGE,
                        R.string.order_image,
                        true,
                        currentOrder == PostsFilter.Order.IMAGE,
                        PostsFilter.Order.IMAGE,
                        this::onSortItemClicked
                )
                .addNestedItem(
                        SORT_MODE_NEWEST,
                        R.string.order_newest,
                        true,
                        currentOrder == PostsFilter.Order.IMAGE,
                        PostsFilter.Order.NEWEST,
                        this::onSortItemClicked
                )
                .addNestedItem(
                        SORT_MODE_OLDEST,
                        R.string.order_oldest,
                        true,
                        currentOrder == PostsFilter.Order.OLDEST,
                        PostsFilter.Order.OLDEST,
                        this::onSortItemClicked
                )
                .addNestedItem(
                        SORT_MODE_MODIFIED,
                        R.string.order_modified,
                        true,
                        currentOrder == PostsFilter.Order.MODIFIED,
                        PostsFilter.Order.MODIFIED,
                        this::onSortItemClicked
                )
                .addNestedItem(
                        SORT_MODE_ACTIVITY,
                        R.string.order_activity,
                        true,
                        currentOrder == PostsFilter.Order.ACTIVITY,
                        PostsFilter.Order.ACTIVITY,
                        this::onSortItemClicked
                )
                .build()
                .withSubItem(ACTION_OPEN_BROWSER, R.string.action_open_browser, this::openBrowserClicked)
                .withSubItem(ACTION_SHARE, R.string.action_share, this::shareClicked)
                .withSubItem(ACTION_SCROLL_TO_TOP, R.string.action_scroll_to_top, this::upClicked)
                .withSubItem(ACTION_SCROLL_TO_BOTTOM, R.string.action_scroll_to_bottom, this::downClicked)
                .build()
                .build();
    }

    private void onSortItemClicked(ToolbarMenuSubItem subItem) {
        PostsFilter.Order order = (PostsFilter.Order) subItem.value;
        ChanSettings.boardOrder.set(order.name);
        BrowseController.this.order = order;

        ToolbarMenuSubItem sortSubItem = navigation.findSubItem(ACTION_SORT);
        resetSelectedSortOrderItem(sortSubItem);
        subItem.isCurrentlySelected = true;

        final ThreadPresenter presenter = threadLayout.presenter;
        presenter.setOrder(order);
    }

    private void resetSelectedSortOrderItem(ToolbarMenuSubItem sortSubItem) {
        sortSubItem.isCurrentlySelected = false;

        for (ToolbarMenuSubItem nestedItem : sortSubItem.moreItems) {
            resetSelectedSortOrderItem(nestedItem);
        }
    }

    private void searchClicked(ToolbarMenuItem item) {
        ThreadPresenter presenter = threadLayout.presenter;
        if (presenter.isBound()) {
            View refreshView = item.getView();
            refreshView.setScaleX(1f);
            refreshView.setScaleY(1f);
            refreshView.animate()
                    .scaleX(10f)
                    .scaleY(10f)
                    .setDuration(500)
                    .setInterpolator(new AccelerateInterpolator(2f))
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            refreshView.setScaleX(1f);
                            refreshView.setScaleY(1f);
                        }
                    });

            ((ToolbarNavigationController) navigationController).showSearch();
        }
    }

    private void reloadClicked(ToolbarMenuItem item) {
        ThreadPresenter presenter = threadLayout.presenter;
        if (presenter.isBound()) {
            presenter.requestData();

            // Give the rotation menu item view a spin.
            View refreshView = item.getView();
            //Disable the ripple effect until the animation ends, but turn it back on so tap/hold ripple works
            refreshView.setBackgroundResource(0);
            Animation animation = new RotateAnimation(0,
                    360,
                    RotateAnimation.RELATIVE_TO_SELF,
                    0.5f,
                    RotateAnimation.RELATIVE_TO_SELF,
                    0.5f
            );
            animation.setDuration(500L);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    refreshView.setBackgroundResource(R.drawable.item_background);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            refreshView.startAnimation(animation);
        }
    }

    private void replyClicked(ToolbarMenuSubItem item) {
        threadLayout.openReply(true);
    }

    private void viewModeClicked(ToolbarMenuSubItem item) {
        handleViewMode(item);
    }

    private void openBrowserClicked(ToolbarMenuSubItem item) {
        handleShareAndOpenInBrowser(false);
    }

    private void shareClicked(ToolbarMenuSubItem item) {
        handleShareAndOpenInBrowser(true);
    }

    private void upClicked(ToolbarMenuSubItem item) {
        threadLayout.presenter.scrollTo(0, false);
    }

    private void downClicked(ToolbarMenuSubItem item) {
        threadLayout.presenter.scrollTo(-1, false);
    }

    @Override
    public void onSiteClicked(Site site) {
        presenter.onBoardsFloatingMenuSiteClicked(site);
    }

    @Override
    public void openSetup() {
        SitesSetupController setupController = new SitesSetupController(context);
        if (doubleNavigationController != null) {
            doubleNavigationController.pushController(setupController);
        } else {
            navigationController.pushController(setupController);
        }
    }

    private void handleShareAndOpenInBrowser(boolean share) {
        ThreadPresenter presenter = threadLayout.presenter;
        if (presenter.isBound()) {
            if (presenter.getChanThread() == null) {
                showToast(context, R.string.cannot_open_in_browser_already_deleted);
                return;
            }

            String link = presenter.getLoadable().desktopUrl();

            if (share) {
                shareLink(link);
            } else {
                openLinkInBrowser(context, link, themeHelper.getTheme());
            }
        }
    }

    private void handleViewMode(ToolbarMenuSubItem item) {
        ChanSettings.PostViewMode postViewMode = ChanSettings.boardViewMode.get();
        if (postViewMode == ChanSettings.PostViewMode.LIST) {
            postViewMode = ChanSettings.PostViewMode.CARD;
        } else {
            postViewMode = ChanSettings.PostViewMode.LIST;
        }

        ChanSettings.boardViewMode.set(postViewMode);

        int viewModeText = postViewMode == ChanSettings.PostViewMode.LIST
                ? R.string.action_switch_catalog
                : R.string.action_switch_board;
        item.text = getString(viewModeText);

        threadLayout.setPostViewMode(postViewMode);
    }

    @Override
    public void loadBoard(Loadable loadable) {
        loadable.title = BoardHelper.getName(loadable.board);
        navigation.title = "/" + loadable.boardCode + "/";
        navigation.subtitle = loadable.board.name;

        ThreadPresenter presenter = threadLayout.presenter;
        presenter.bindLoadable(loadable);
        presenter.requestData();

        requireNavController().requireToolbar().updateTitle(navigation);
    }

    @Override
    public void loadSiteSetup(Site site) {
        SiteSetupController siteSetupController = new SiteSetupController(context);
        siteSetupController.setSite(site);

        if (doubleNavigationController != null) {
            doubleNavigationController.pushController(siteSetupController);
        } else {
            navigationController.pushController(siteSetupController);
        }
    }

    @Override
    public void openPin(Pin pin) {
        showThread(pin.loadable);
    }

    @Override
    public void showThread(Loadable threadLoadable) {
        showThread(threadLoadable, true);
    }

    @Override
    public void showThread(@NotNull ChanDescriptor.ThreadDescriptor descriptor) {
        Loadable threadLoadable = databaseLoadableManager.getByThreadDescriptor(descriptor);
        if (threadLoadable == null) {
            showToast(context, R.string.browse_controller_cannot_open_thread);
            return;
        }

        if (!threadLoadable.isThreadMode()) {
            String errorMessage = context.getString(
                    R.string.browse_controller_cannot_open_thread_not_a_thread,
                    threadLoadable.mode
            );

            showToast(context, errorMessage);
            return;
        }

        showThread(threadLoadable, true);
    }

    @Override
    public void showBoard(@NotNull ChanDescriptor.CatalogDescriptor descriptor) {
        Board board = boardRepository.getFromBoardDescriptor(descriptor.getBoardDescriptor());
        if (board == null) {
            showToast(context, R.string.browse_controller_cannot_open_board);
            return;
        }

        showBoard(board);
    }

    @Override
    public void showBoard(Loadable catalogLoadable) {
        //we don't actually need to do anything here because you can't tap board links in the browse controller
        //set the board just in case?
        setBoard(catalogLoadable.board);
    }

    @Override
    public void showBoardAndSearch(Loadable catalogLoadable, String searchQuery) {
        //we don't actually need to do anything here because you can't tap board links in the browse controller
        //set the board just in case?
        setBoard(catalogLoadable.board);
    }

    // Creates or updates the target ThreadViewController
    // This controller can be in various places depending on the layout
    // We dynamically search for it
    public void showThread(Loadable threadLoadable, boolean animated) {
        // The target ThreadViewController is in a split nav
        // (BrowseController -> ToolbarNavigationController -> SplitNavigationController)
        SplitNavigationController splitNav = null;

        // The target ThreadViewController is in a slide nav
        // (BrowseController -> SlideController -> ToolbarNavigationController)
        ThreadSlideController slideNav = null;

        if (doubleNavigationController instanceof SplitNavigationController) {
            splitNav = (SplitNavigationController) doubleNavigationController;
        }

        if (doubleNavigationController instanceof ThreadSlideController) {
            slideNav = (ThreadSlideController) doubleNavigationController;
        }

        if (splitNav != null) {
            // Create a threadview inside a toolbarnav in the right part of the split layout
            if (splitNav.getRightController() instanceof StyledToolbarNavigationController) {
                StyledToolbarNavigationController navigationController =
                        (StyledToolbarNavigationController) splitNav.getRightController();

                if (navigationController.getTop() instanceof ViewThreadController) {
                    ((ViewThreadController) navigationController.getTop()).loadThread(threadLoadable);
                }
            } else {
                StyledToolbarNavigationController navigationController
                        = new StyledToolbarNavigationController(context);
                splitNav.setRightController(navigationController);

                ViewThreadController viewThreadController
                        = new ViewThreadController(context, threadLoadable);
                navigationController.pushController(viewThreadController, false);
            }
            splitNav.switchToController(false);
        } else if (slideNav != null) {
            // Create a threadview in the right part of the slide nav *without* a toolbar
            if (slideNav.getRightController() instanceof ViewThreadController) {
                ((ViewThreadController) slideNav.getRightController()).loadThread(threadLoadable);
            } else {
                ViewThreadController viewThreadController = new ViewThreadController(
                        context,
                        threadLoadable
                );

                slideNav.setRightController(viewThreadController);
            }
            slideNav.switchToController(false);
        } else {
            // the target ThreadNav must be pushed to the parent nav controller
            // (BrowseController -> ToolbarNavigationController)
            ViewThreadController viewThreadController = new ViewThreadController(
                    context,
                    threadLoadable
            );

            if (navigationController != null) {
                navigationController.pushController(viewThreadController, animated);
            }
        }

        historyNavigationManager.moveNavElementToTop(threadLoadable.getChanDescriptor());
    }

    public void showBoard(Board board) {
        // The target ThreadViewController is in a split nav
        // (BrowseController -> ToolbarNavigationController -> SplitNavigationController)
        SplitNavigationController splitNav = null;

        // The target ThreadViewController is in a slide nav
        // (BrowseController -> SlideController -> ToolbarNavigationController)
        ThreadSlideController slideNav = null;

        if (doubleNavigationController instanceof SplitNavigationController) {
            splitNav = (SplitNavigationController) doubleNavigationController;
        }

        if (doubleNavigationController instanceof ThreadSlideController) {
            slideNav = (ThreadSlideController) doubleNavigationController;
        }

        // Do nothing when split navigation is enabled because both controllers are always visible
        // so we don't need to switch between left and right controllers
        if (splitNav == null) {
            if (slideNav != null) {
                slideNav.switchToController(true);
            } else {
                if (navigationController != null) {
                    // We wouldn't want to pop BrowseController when opening a board
                    if ((!(navigationController.getTop() instanceof BrowseController))) {
                        navigationController.popController(true);
                    }
                }
            }
        }

        setBoard(board);
    }

    @Override
    public void onSlideChanged() {
        super.onSlideChanged();
        if (getToolbar() != null && searchQuery != null) {
            getToolbar().openSearch(searchQuery);
            getToolbar().searchInput(searchQuery);
            searchQuery = null;
        }
    }

    @Override
    public Post getPostForPostImage(PostImage postImage) {
        return threadLayout.presenter.getPostFromPostImage(postImage);
    }
}
