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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.HistoryNavigationManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.manager.WatchManager.PinMessages;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.drawer.DrawerCallbacks;
import com.github.adamantcheese.chan.features.drawer.DrawerController;
import com.github.adamantcheese.chan.ui.controller.navigation.NavigationController;
import com.github.adamantcheese.chan.ui.controller.navigation.StyledToolbarNavigationController;
import com.github.adamantcheese.chan.ui.controller.navigation.ToolbarNavigationController;
import com.github.adamantcheese.chan.ui.helper.HintPopup;
import com.github.adamantcheese.chan.ui.layout.ThreadLayout;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuSubItem;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.DescriptorUtils;
import com.github.adamantcheese.chan.utils.DialogUtils;
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor;

import org.greenrobot.eventbus.Subscribe;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

import javax.inject.Inject;

import kotlin.Unit;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.ui.toolbar.ToolbarMenu.OVERFLOW_ID;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getFlavorType;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLinkInBrowser;
import static com.github.adamantcheese.chan.utils.AndroidUtils.shareLink;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class ViewThreadController
        extends ThreadController
        implements ThreadLayout.ThreadLayoutCallback,
        ToolbarMenuItem.ToobarThreedotMenuCallback,
        ThreadSlideController.ReplyAutoCloseListener {
    private static final String TAG = "ViewThreadController";

    private static final int ACTION_PIN = 8001;
    private static final int ACTION_ALBUM = 8002;

    private static final int ACTION_REPLY = 9000;
    private static final int ACTION_SEARCH = 9001;
    private static final int ACTION_RELOAD = 9002;
    private static final int ACTION_VIEW_REMOVED_POSTS = 9003;
    private static final int ACTION_RETRIEVE_DELETED_POSTS = 9004;
    private static final int ACTION_OPEN_BROWSER = 9005;
    private static final int ACTION_SHARE = 9006;
    private static final int ACTION_GO_TO_POST = 9007;
    private static final int ACTION_SCROLL_TO_TOP = 9008;
    private static final int ACTION_SCROLL_TO_BOTTOM = 9009;
    private static final int ACTION_FORCE_RELOAD = 9010;


    private DatabaseLoadableManager databaseLoadableManager;

    @Inject
    WatchManager watchManager;
    @Inject
    ThemeHelper themeHelper;
    @Inject
    DatabaseManager databaseManager;
    @Inject
    BoardRepository boardRepository;
    @Inject
    HistoryNavigationManager historyNavigationManager;

    private boolean pinItemPinned = false;
    private Loadable loadable;

    //pairs of the current thread loadable and the thread we're going to's hashcode
    private Deque<Pair<Loadable, Integer>> threadFollowerpool = new ArrayDeque<>();

    @Nullable
    private HintPopup hintPopup = null;

    public ViewThreadController(Context context, Loadable loadable) {
        super(context);
        this.loadable = loadable;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        databaseLoadableManager = databaseManager.getDatabaseLoadableManager();

        threadLayout.setPostViewMode(ChanSettings.PostViewMode.LIST);
        view.setBackgroundColor(getAttrColor(context, R.attr.backcolor));

        navigation.hasDrawer = true;

        buildMenu();
        loadThread(loadable);
    }

    @Override
    public void onShow() {
        super.onShow();

        ThreadPresenter presenter = threadLayout.presenter;
        if (presenter != null) {
            setPinIconState(false);
        }

        if (drawerCallbacks != null) {
            drawerCallbacks.resetBottomNavViewCheckState();

            if (ChanSettings.getCurrentLayoutMode() != ChanSettings.LayoutMode.SPLIT) {
                drawerCallbacks.showBottomNavBar(false, false);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        dismissHintPopup();
        updateDrawerHighlighting(null);
        updateLeftPaneHighlighting(null);
    }

    @Override
    protected void setDrawerCallbacks(@Nullable DrawerCallbacks drawerCallbacks) {
        super.setDrawerCallbacks(drawerCallbacks);
    }

    protected void buildMenu() {
        NavigationItem.MenuBuilder menuBuilder = navigation.buildMenu();

        if (!ChanSettings.textOnly.get()) {
            menuBuilder.withItem(
                    ACTION_ALBUM,
                    R.drawable.ic_image_white_24dp,
                    this::albumClicked
            );
        }

        menuBuilder.withItem(
                ACTION_PIN,
                R.drawable.ic_bookmark_border_white_24dp,
                this::pinClicked
        );

        NavigationItem.MenuOverflowBuilder menuOverflowBuilder = menuBuilder.withOverflow(
                navigationController,
                this
        );

        if (!ChanSettings.enableReplyFab.get()) {
            menuOverflowBuilder.withSubItem(
                    ACTION_REPLY,
                    R.string.action_reply,
                    this::replyClicked
            );
        }

        ChanDescriptor descriptor = DescriptorUtils.getDescriptor(loadable);
        boolean isThread = descriptor instanceof ChanDescriptor.ThreadDescriptor;
        boolean is4chan = descriptor.siteDescriptor().is4chan();

        menuOverflowBuilder
                .withSubItem(
                        ACTION_SEARCH,
                        R.string.action_search,
                        this::searchClicked
                )
                .withSubItem(
                        ACTION_RELOAD,
                        R.string.action_reload,
                        this::reloadClicked
                )
                .withSubItem(
                        ACTION_FORCE_RELOAD,
                        R.string.action_force_reload,
                        getFlavorType() == AndroidUtils.FlavorType.Dev,
                        this::forceReloadClicked
                )
                .withSubItem(
                        ACTION_VIEW_REMOVED_POSTS,
                        R.string.action_view_removed_posts,
                        this::showRemovedPostsDialog
                )
                .withSubItem(
                        ACTION_RETRIEVE_DELETED_POSTS,
                        R.string.action_retrieve_deleted_posts,
                        is4chan && isThread,
                        this::retrieveDeletedPosts
                )
                .withSubItem(
                        ACTION_OPEN_BROWSER,
                        R.string.action_open_browser,
                        this::openBrowserClicked
                )
                .withSubItem(
                        ACTION_SHARE,
                        R.string.action_share,
                        this::shareClicked
                )
                .withSubItem(
                        ACTION_GO_TO_POST,
                        R.string.action_go_to_post,
                        getFlavorType() == AndroidUtils.FlavorType.Dev,
                        this::onGoToPostClicked
                )
                .withSubItem(
                        ACTION_SCROLL_TO_TOP,
                        R.string.action_scroll_to_top,
                        this::upClicked
                )
                .withSubItem(
                        ACTION_SCROLL_TO_BOTTOM,
                        R.string.action_scroll_to_bottom,
                        this::downClicked
                );

        menuOverflowBuilder.build().build();
    }

    private void albumClicked(ToolbarMenuItem item) {
        threadLayout.presenter.showAlbum();
    }

    private void pinClicked(ToolbarMenuItem item) {
        if (threadLayout.presenter.pin()) {
            setPinIconState(true);

            updateDrawerHighlighting(loadable);
        }
    }

    private void searchClicked(ToolbarMenuSubItem item) {
        ((ToolbarNavigationController) navigationController).showSearch();
    }

    private void replyClicked(ToolbarMenuSubItem item) {
        threadLayout.openReply(true);
    }

    @Override
    public void onReplyViewShouldClose() {
        threadLayout.openReply(false);
    }

    private void reloadClicked(ToolbarMenuSubItem item) {
        threadLayout.presenter.requestData();
    }

    private void forceReloadClicked(ToolbarMenuSubItem item) {
        threadLayout.presenter.forceRequestData();
    }

    public void retrieveDeletedPosts(ToolbarMenuSubItem item) {
        threadLayout.presenter.retrieveDeletedPosts();
    }

    public void showRemovedPostsDialog(ToolbarMenuSubItem item) {
        threadLayout.presenter.showRemovedPostsDialog();
    }

    private void openBrowserClicked(ToolbarMenuSubItem item) {
        if (threadLayout.presenter.getChanThread() == null) {
            showToast(context, R.string.cannot_open_in_browser_already_deleted);
            return;
        }

        Loadable loadable = threadLayout.presenter.getLoadable();
        openLinkInBrowser(context, loadable.desktopUrl(), themeHelper.getTheme());
    }

    private void shareClicked(ToolbarMenuSubItem item) {
        if (threadLayout.presenter.getChanThread() == null) {
            showToast(context, R.string.cannot_shared_thread_already_deleted);
            return;
        }

        Loadable loadable = threadLayout.presenter.getLoadable();
        shareLink(loadable.desktopUrl());
    }

    private void onGoToPostClicked(ToolbarMenuSubItem item) {
        DialogUtils.createSimpleDialogWithInput(
                context,
                R.string.view_thread_controller_enter_post_id,
                (input) -> {
                    try {
                        int postNo = Integer.parseInt(input);
                        threadLayout.presenter.scrollToPostByPostNo(postNo);
                    } catch (NumberFormatException e) {
                        //ignored
                    }

                    return Unit.INSTANCE;
                },
                InputType.TYPE_CLASS_NUMBER
        ).show();
    }

    private void upClicked(ToolbarMenuSubItem item) {
        threadLayout.scrollTo(0, false);
    }

    private void downClicked(ToolbarMenuSubItem item) {
        threadLayout.scrollTo(-1, false);
    }

    @Override
    public void openPin(Pin pin) {
        loadThread(pin.loadable);
    }

    @Subscribe
    public void onEvent(PinMessages.PinAddedMessage message) {
        setPinIconState(true);
    }

    @Subscribe
    public void onEvent(PinMessages.PinRemovedMessage message) {
        setPinIconState(true);
    }

    @Subscribe
    public void onEvent(PinMessages.PinChangedMessage message) {
        setPinIconState(false);
    }

    @Subscribe
    public void onEvent(PinMessages.PinsChangedMessage message) {
        setPinIconState(true);
    }

    @Override
    public void showThread(final Loadable threadLoadable) {
        new AlertDialog.Builder(context).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    threadFollowerpool.addFirst(new Pair<>(loadable, threadLoadable.hashCode()));
                    loadThread(threadLoadable);
                })
                .setTitle(R.string.open_thread_confirmation)
                .setMessage("/" + threadLoadable.boardCode + "/" + threadLoadable.no)
                .show();
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

        loadThread(threadLoadable);
    }

    @Override
    public void showBoard(@NotNull ChanDescriptor.CatalogDescriptor descriptor) {
        Board board = boardRepository.getFromBoardDescriptor(descriptor.getBoardDescriptor());
        if (board == null) {
            showToast(context, R.string.browse_controller_cannot_open_board);
            return;
        }

        Loadable catalog = databaseLoadableManager.get(Loadable.forCatalog(board));
        showBoard(catalog);
    }

    @Override
    public void showBoard(@NonNull final Loadable catalogLoadable) {
        showBoardInternal(catalogLoadable, null);
    }

    @Override
    public void showBoardAndSearch(@NonNull final Loadable catalogLoadable, String search) {
        showBoardInternal(catalogLoadable, search);
    }

    private void showBoardInternal(Loadable catalogLoadable, String searchQuery) {
        historyNavigationManager.moveNavElementToTop(
                new ChanDescriptor.CatalogDescriptor(catalogLoadable.getBoardDescriptor())
        );

        if (doubleNavigationController != null
                && doubleNavigationController.getLeftController() instanceof BrowseController) {
            // slide layout
            doubleNavigationController.switchToController(true);
            BrowseController browseController =
                    ((BrowseController) doubleNavigationController.getLeftController());

            browseController.setBoard(catalogLoadable.board);
            if (searchQuery != null) {
                browseController.searchQuery = searchQuery;
            }

            return;
        }

        if (doubleNavigationController != null
                && doubleNavigationController.getLeftController() instanceof StyledToolbarNavigationController) {
            // split layout
            BrowseController browseController =
                    ((BrowseController) doubleNavigationController.getLeftController().childControllers.get(0));

            browseController.setBoard(catalogLoadable.board);
            if (searchQuery != null) {
                Toolbar toolbar = browseController.getToolbar();
                if (toolbar != null) {
                    toolbar.openSearch();
                    toolbar.searchInput(searchQuery);
                }
            }

            return;
        }

        // phone layout
        BrowseController browseController = null;
        for (Controller c : navigationController.childControllers) {
            if (c instanceof BrowseController) {
                browseController = (BrowseController) c;
                break;
            }
        }

        if (browseController != null) {
            browseController.setBoard(catalogLoadable.board);
        }

        navigationController.popController(false);

        // search after we're at the browse controller
        if (searchQuery != null && browseController != null) {
            Toolbar toolbar = browseController.getToolbar();
            if (toolbar != null) {
                toolbar.openSearch();
                toolbar.searchInput(searchQuery);
            }
        }
    }

    public void loadThread(Loadable loadable) {
        loadThread(loadable, true);
    }

    public void loadThread(Loadable loadable, boolean addToLocalBackHistory) {
        historyNavigationManager.moveNavElementToTop(loadable.getChanDescriptor());

        ThreadPresenter presenter = threadLayout.presenter;
        if (!loadable.equals(presenter.getLoadable())) {
            loadThreadInternal(loadable, addToLocalBackHistory);
        }
    }

    private void loadThreadInternal(Loadable loadable, boolean addToLocalBackHistory) {
        ThreadPresenter presenter = threadLayout.presenter;

        presenter.bindLoadable(loadable, addToLocalBackHistory);
        this.loadable = loadable;

        updateMenuItems();
        navigation.title = loadable.title;
        requireNavController().requireToolbar().updateTitle(navigation);

        setPinIconState(false);

        updateDrawerHighlighting(loadable);
        updateLeftPaneHighlighting(loadable);
        presenter.requestInitialData();

        showHints();
    }

    private void updateMenuItems() {
        updateRetrievePostsFromArchivesMenuItem();
    }

    private void updateRetrievePostsFromArchivesMenuItem() {
        ChanDescriptor descriptor = DescriptorUtils.getDescriptor(loadable);
        boolean is4chan = descriptor.siteDescriptor().is4chan();

        ToolbarMenuSubItem retrieveDeletedPostsItem = navigation.findSubItem(ACTION_RETRIEVE_DELETED_POSTS);
        if (retrieveDeletedPostsItem == null) {
            return;
        }

        if (is4chan) {
            retrieveDeletedPostsItem.visible = true;
        } else {
            retrieveDeletedPostsItem.visible = false;
        }
    }

    private void showHints() {
        int counter = ChanSettings.threadOpenCounter.increase();
        if (counter == 2) {
            view.postDelayed(() -> {
                View view = navigation.findItem(OVERFLOW_ID).getView();
                if (view != null) {
                    dismissHintPopup();
                    hintPopup = HintPopup.show(context, view, getString(R.string.thread_up_down_hint), -dp(1), 0);
                }
            }, 600);
        } else if (counter == 3) {
            view.postDelayed(() -> {
                View view = navigation.findItem(ACTION_PIN).getView();
                if (view != null) {
                    dismissHintPopup();
                    hintPopup = HintPopup.show(context, view, getString(R.string.thread_pin_hint), -dp(1), 0);
                }
            }, 600);
        }
    }

    private void dismissHintPopup() {
        if (hintPopup != null) {
            hintPopup.dismiss();
            hintPopup = null;
        }
    }

    @Override
    public void onShowPosts() {
        super.onShowPosts();
        navigation.title = this.loadable.title;

        setPinIconState(false);

        requireNavController().requireToolbar().updateTitle(navigation);
        requireNavController().requireToolbar().updateViewForItem(navigation);
    }

    private void updateDrawerHighlighting(Loadable loadable) {
        Pin pin = loadable == null ? null : watchManager.findPinByLoadableId(loadable.id);

        if (navigationController.parentController instanceof DrawerController) {
            ((DrawerController) navigationController.parentController).setPinHighlighted(pin);
        } else if (doubleNavigationController != null) {
            Controller doubleNav = (Controller) doubleNavigationController;
            if (doubleNav.parentController instanceof DrawerController) {
                ((DrawerController) doubleNav.parentController).setPinHighlighted(pin);
            }
        }
    }

    private void updateLeftPaneHighlighting(Loadable loadable) {
        if (doubleNavigationController != null) {
            ThreadController threadController = null;
            Controller leftController = doubleNavigationController.getLeftController();
            if (leftController instanceof ThreadController) {
                threadController = (ThreadController) leftController;
            } else if (leftController instanceof NavigationController) {
                NavigationController leftNavigationController = (NavigationController) leftController;
                for (Controller controller : leftNavigationController.childControllers) {
                    if (controller instanceof ThreadController) {
                        threadController = (ThreadController) controller;
                        break;
                    }
                }
            }
            if (threadController != null) {
                threadController.selectPost(loadable != null ? loadable.no : -1);
            }
        }
    }

    private void setPinIconState(boolean animated) {
        ThreadPresenter presenter = threadLayout.presenter;
        if (presenter != null) {
            setPinIconStateDrawable(presenter.isPinned(), animated);
        }
    }

    private void setPinIconStateDrawable(boolean pinned, boolean animated) {
        if (pinned == pinItemPinned) {
            return;
        }
        ToolbarMenuItem menuItem = navigation.findItem(ACTION_PIN);
        if (menuItem == null) {
            return;
        }

        pinItemPinned = pinned;

        Drawable outline = context.getDrawable(R.drawable.ic_bookmark_border_white_24dp);
        Drawable white = context.getDrawable(R.drawable.ic_bookmark_white_24dp);

        Drawable drawable = pinned ? white : outline;
        menuItem.setImage(drawable, animated);
    }

    @Override
    public boolean threadBackPressed() {
        //clear the pool if the current thread isn't a part of this crosspost chain
        //ie a new thread is loaded and a new chain is started; this will never throw null pointer exceptions
        //noinspection ConstantConditions
        if (!threadFollowerpool.isEmpty() && threadFollowerpool.peekFirst().second != loadable.hashCode()) {
            threadFollowerpool.clear();
        }
        //if the thread is new, it'll be empty here, so we'll get back-to-catalog functionality
        if (threadFollowerpool.isEmpty()) {
            return false;
        }
        loadThread(threadFollowerpool.removeFirst().first, false);
        return true;
    }

    @Override
    public Post getPostForPostImage(PostImage postImage) {
        return threadLayout.presenter.getPostFromPostImage(postImage);
    }

    @Override
    public void onMenuShown() {
    }

    @Override
    public void onMenuHidden() {
    }

    @Override
    public void onSlideChanged(boolean leftOpen) {
        super.onSlideChanged(leftOpen);

        if (loadable != null) {
            historyNavigationManager.moveNavElementToTop(loadable.getChanDescriptor());
        }
    }
}
