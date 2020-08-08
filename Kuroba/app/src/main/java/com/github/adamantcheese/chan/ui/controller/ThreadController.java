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
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.manager.FilterType;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.features.drawer.DrawerCallbacks;
import com.github.adamantcheese.chan.ui.controller.navigation.ToolbarNavigationController;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.layout.ThreadLayout;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor;
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public abstract class ThreadController
        extends Controller
        implements ThreadLayout.ThreadLayoutCallback,
        ImageViewerController.ImageViewerCallback,
        SwipeRefreshLayout.OnRefreshListener,
        ToolbarNavigationController.ToolbarSearchCallback,
        NfcAdapter.CreateNdefMessageCallback,
        ThreadSlideController.SlideChangeListener {
    private static final String TAG = "ThreadController";

    @Inject
    SiteRepository siteRepository;

    protected ThreadLayout threadLayout;
    @Nullable
    protected DrawerCallbacks drawerCallbacks;
    private SwipeRefreshLayout swipeRefreshLayout;

    public ThreadController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        EventBus.getDefault().register(this);

        navigation.handlesToolbarInset = true;

        threadLayout = (ThreadLayout) inflate(context, R.layout.layout_thread, null);
        threadLayout.create(this);

        swipeRefreshLayout = new SwipeRefreshLayout(context) {
            @Override
            public boolean canChildScrollUp() {
                return threadLayout.canChildScrollUp();
            }
        };

        swipeRefreshLayout.addView(threadLayout);
        swipeRefreshLayout.setOnRefreshListener(this);
        view = swipeRefreshLayout;

        Toolbar toolbar = getToolbar();

        if (toolbar != null) {
            toolbar.addToolbarHeightUpdatesCallback((heightChanged) -> {
                int toolbarHeight = getToolbar().getToolbarHeight();

                swipeRefreshLayout.setProgressViewOffset(
                        false,
                        toolbarHeight - dp(40),
                        toolbarHeight + dp(64 - 40)
                );
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        drawerCallbacks = null;
        threadLayout.destroy();

        EventBus.getDefault().unregister(this);
    }

    protected void setDrawerCallbacks(@Nullable DrawerCallbacks drawerCallbacks) {
        threadLayout.setDrawerCallbacks(drawerCallbacks);
        this.drawerCallbacks = drawerCallbacks;
    }

    public boolean passMotionEventIntoDrawer(MotionEvent event) {
        if (drawerCallbacks == null) {
            return false;
        }

        return drawerCallbacks.passMotionEventIntoDrawer(event);
    }

    public void showSitesNotSetup() {
        threadLayout.presenter.showNoContent();
    }

    /*
     * Used to save instance state
     */
    @Nullable
    public ChanDescriptor getChanDescriptor() {
        return threadLayout.presenter.getChanDescriptor();
    }

    public void selectPost(long post) {
        threadLayout.presenter.selectPost(post);
    }

    @Override
    public boolean onBack() {
        return threadLayout.onBack();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return threadLayout.sendKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Subscribe
    public void onEvent(Chan.ForegroundChangedMessage message) {
        threadLayout.presenter.onForegroundChanged(message.inForeground);
    }

    @Subscribe
    public void onEvent(RefreshUIMessage message) {
        threadLayout.presenter.requestData();
    }

    @Override
    public void onRefresh() {
        threadLayout.refreshFromSwipe();
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        if (threadLayout.presenter.getChanThread() == null) {
            showToast(context, R.string.cannot_send_thread_via_nfc_already_deleted);
            return null;
        }

        String url = null;
        NdefMessage message = null;

        ChanDescriptor chanDescriptor = getChanDescriptor();
        SiteDescriptor siteDescriptor = chanDescriptor.siteDescriptor();
        Site site = siteRepository.bySiteDescriptor(siteDescriptor);

        if (site != null) {
            url = site.resolvable().desktopUrl(chanDescriptor, null);
        }

        if (url != null) {
            try {
                Logger.d(TAG, "Pushing url " + url + " to android beam");
                NdefRecord record = NdefRecord.createUri(url);
                message = new NdefMessage(new NdefRecord[]{record});
            } catch (IllegalArgumentException e) {
                Logger.e(TAG, "NdefMessage create error", e);
            }
        }

        return message;
    }

    @Override
    public void openReportController(final Post post) {
        Site site = siteRepository.bySiteDescriptor(post.boardDescriptor.getSiteDescriptor());

        if (site != null) {
            navigationController.pushController(new ReportController(context, post, site));
        }
    }

    public void selectPostImage(PostImage postImage) {
        threadLayout.presenter.selectPostImage(postImage);
    }

    @Override
    public void showImages(
            @NonNull List<PostImage> images,
            int index,
            @NonNull ChanDescriptor chanDescriptor,
            final ThumbnailView thumbnail
    ) {
        boolean isAlreadyPresenting =
                isAlreadyPresenting((controller) -> controller instanceof ImageViewerNavigationController);

        // Just ignore the showImages request when the image is not loaded
        if (thumbnail.getBitmap() != null && !isAlreadyPresenting) {
            ImageViewerNavigationController imagerViewer = new ImageViewerNavigationController(context);
            presentController(imagerViewer, false);
            imagerViewer.showImages(images, index, chanDescriptor, this);
        }
    }

    @Override
    public ThumbnailView getPreviewImageTransitionView(PostImage postImage) {
        return threadLayout.getThumbnail(postImage);
    }

    @Override
    public void scrollToImage(PostImage postImage) {
        threadLayout.presenter.scrollToImage(postImage, true);
    }

    @Override
    public void showAlbum(List<PostImage> images, int index) {
        if (threadLayout.presenter.getChanThread() != null) {
            AlbumViewController albumViewController = new AlbumViewController(context);
            albumViewController.setImages(getChanDescriptor(), images, index, navigation.title);

            if (doubleNavigationController != null) {
                doubleNavigationController.pushController(albumViewController);
            } else {
                navigationController.pushController(albumViewController);
            }
        }
    }

    @Override
    public void onShowPosts() {
    }

    @Override
    public void hideSwipeRefreshLayout() {
        if (swipeRefreshLayout == null) {
            return;
        }

        swipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public Toolbar getToolbar() {
        if (navigationController instanceof ToolbarNavigationController) {
            return navigationController.getToolbar();
        } else {
            return null;
        }
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        threadLayout.presenter.onSearchVisibilityChanged(visible);
    }

    @Override
    public void onSearchEntered(String entered) {
        threadLayout.presenter.onSearchEntered(entered);
    }

    @Override
    public void openFilterForType(FilterType type, String filterText) {
        FiltersController filtersController = new FiltersController(context);
        if (doubleNavigationController != null) {
            doubleNavigationController.openControllerWrappedIntoBottomNavAwareController(filtersController);
        } else {
            requireStartActivity().openControllerWrappedIntoBottomNavAwareController(filtersController);
        }

        requireStartActivity().setSettingsMenuItemSelected();

        Filter filter = new Filter();
        filter.type = type.flag;
        filter.pattern = '/' + (filterText == null ? "" : filterText) + '/';

        filtersController.showFilterDialog(filter);
    }

    @Override
    public void onSlideChanged(boolean leftOpen) {
        threadLayout.gainedFocus();
    }

    @Override
    public boolean threadBackPressed() {
        return false;
    }

}
