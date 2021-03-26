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
package com.github.k1rakishou.chan.ui.controller;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.core.helper.ThumbnailLongtapOptionsHelper;
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener;
import com.github.k1rakishou.chan.core.navigation.RequiresNoBottomNavBar;
import com.github.k1rakishou.chan.features.settings.screens.AppearanceSettingsScreen;
import com.github.k1rakishou.chan.ui.cell.AlbumViewCell;
import com.github.k1rakishou.chan.ui.controller.navigation.DoubleNavigationController;
import com.github.k1rakishou.chan.ui.controller.navigation.SplitNavigationController;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableGridRecyclerView;
import com.github.k1rakishou.chan.ui.toolbar.Toolbar;
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem;
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem;
import com.github.k1rakishou.chan.ui.view.FastScroller;
import com.github.k1rakishou.chan.ui.view.FastScrollerHelper;
import com.github.k1rakishou.chan.ui.view.post_thumbnail.PostImageThumbnailView;
import com.github.k1rakishou.chan.ui.view.post_thumbnail.ThumbnailView;
import com.github.k1rakishou.common.AndroidUtils;
import com.github.k1rakishou.common.KotlinExtensionsKt;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;
import com.github.k1rakishou.model.data.filter.ChanFilterMutable;
import com.github.k1rakishou.model.data.post.ChanPostImage;
import com.github.k1rakishou.persist_state.PersistableChanState;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import javax.inject.Inject;

import kotlin.Unit;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getQuantityString;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate;

public class AlbumViewController
        extends Controller
        implements ImageViewerController.ImageViewerCallback,
        ImageViewerController.GoPostCallback,
        RequiresNoBottomNavBar,
        WindowInsetsListener,
        Toolbar.ToolbarHeightUpdatesCallback {
    private static final int DEFAULT_SPAN_WIDTH = dp(120);

    private static final int ACTION_DOWNLOAD = 0;
    private static final int ACTION_TOGGLE_LAYOUT_MODE = 1;
    private static final int ACTION_TOGGLE_IMAGE_DETAILS = 2;

    private ColorizableGridRecyclerView recyclerView;
    private List<ChanPostImage> postImages;
    private int targetIndex = -1;
    private ChanDescriptor chanDescriptor;

    @Nullable
    private FastScroller fastScroller;

    private AlbumAdapter albumAdapter;
    private final ThreadControllerCallbacks threadControllerCallbacks;

    @Inject
    GlobalWindowInsetsManager globalWindowInsetsManager;
    @Inject
    ThumbnailLongtapOptionsHelper thumbnailLongtapOptionsHelper;

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    public AlbumViewController(Context context, ThreadControllerCallbacks threadControllerCallbacks) {
        super(context);

        this.threadControllerCallbacks = threadControllerCallbacks;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // View setup
        view = inflate(context, R.layout.controller_album_view);
        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);

        albumAdapter = new AlbumAdapter();
        recyclerView.setAdapter(albumAdapter);

        updateRecyclerView(false);

        // Navigation
        Drawable downloadDrawable = ContextCompat.getDrawable(context, R.drawable.ic_file_download_white_24dp);
        downloadDrawable.setTint(Color.WHITE);

        Drawable gridDrawable;

        if (PersistableChanState.getAlbumLayoutGridMode().get()) {
            gridDrawable = ContextCompat.getDrawable(context, R.drawable.ic_baseline_view_quilt_24);
        } else {
            gridDrawable = ContextCompat.getDrawable(context, R.drawable.ic_baseline_view_comfy_24);
        }

        gridDrawable.setTint(Color.WHITE);

        navigation
                .buildMenu(context)
                .withItem(ACTION_TOGGLE_LAYOUT_MODE, gridDrawable, this::toggleLayoutModeClicked)
                .withItem(ACTION_DOWNLOAD, downloadDrawable, this::downloadAlbumClicked)
                .withOverflow(navigationController)
                .withCheckableSubItem(
                        ACTION_TOGGLE_IMAGE_DETAILS,
                        R.string.action_album_show_image_details,
                        true,
                        PersistableChanState.showAlbumViewsImageDetails.get(),
                        this::onToggleAlbumViewsImageInfoToggled)
                .build()
                .build();

        fastScroller = FastScrollerHelper.create(
                FastScroller.FastScrollerControllerType.Album,
                recyclerView,
                null,
                0
        );

        requireNavController().requireToolbar().addToolbarHeightUpdatesCallback(this);
        globalWindowInsetsManager.addInsetsUpdatesListener(this);

        onInsetsChanged();
    }

    private void updateRecyclerView(boolean reloading) {
        SpanInfo spanInfo = getSpanCountAndSpanWidth();

        if (PersistableChanState.getAlbumLayoutGridMode().get()) {
            GridLayoutManager gridLayoutManager = new GridLayoutManager(context, spanInfo.spanCount);
            recyclerView.setLayoutManager(gridLayoutManager);
        } else {
            StaggeredGridLayoutManager staggeredGridLayoutManager = new StaggeredGridLayoutManager(
                    spanInfo.spanCount,
                    StaggeredGridLayoutManager.VERTICAL
            );

            recyclerView.setLayoutManager(staggeredGridLayoutManager);
        }

        recyclerView.setSpanWidth(spanInfo.spanWidth);
        recyclerView.setItemAnimator(null);
        recyclerView.scrollToPosition(targetIndex);

        if (reloading) {
            albumAdapter.refresh();
        }
    }

    private SpanInfo getSpanCountAndSpanWidth() {
        int albumSpanCount = ChanSettings.albumSpanCount.get();
        int albumSpanWith = DEFAULT_SPAN_WIDTH;

        int displayWidth = AndroidUtils.getDisplaySize(context).x;

        if (albumSpanCount == 0) {
            albumSpanCount = displayWidth / DEFAULT_SPAN_WIDTH;
        } else {
            albumSpanWith = displayWidth / albumSpanCount;
        }

        albumSpanCount = AppearanceSettingsScreen.clampColumnsCount(albumSpanCount);

        return new SpanInfo(albumSpanCount, albumSpanWith);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        requireNavController().requireToolbar().removeToolbarHeightUpdatesCallback(this);
        globalWindowInsetsManager.removeInsetsUpdatesListener(this);

        if (fastScroller != null) {
            fastScroller.onCleanup();
            fastScroller = null;
        }

        recyclerView.swapAdapter(null, true);
    }

    @Override
    public void onToolbarHeightKnown(boolean heightChanged) {
        if (!heightChanged) {
            return;
        }

        onInsetsChanged();
    }

    @Override
    public void onInsetsChanged() {
        int bottomPadding = globalWindowInsetsManager.bottom();

        if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
            bottomPadding = 0;
        }

        KotlinExtensionsKt.updatePaddings(
                recyclerView,
                null,
                FastScrollerHelper.FAST_SCROLLER_WIDTH,
                requireNavController().requireToolbar().getToolbarHeight(),
                bottomPadding
        );
    }

    public void setImages(
            ChanDescriptor chanDescriptor,
            List<ChanPostImage> postImages,
            int index,
            String title
    ) {
        this.chanDescriptor = chanDescriptor;
        this.postImages = postImages;

        navigation.title = title;
        navigation.subtitle = getQuantityString(R.plurals.image, postImages.size(), postImages.size());
        targetIndex = index;
    }

    private void onToggleAlbumViewsImageInfoToggled(ToolbarMenuSubItem subItem) {
        PersistableChanState.showAlbumViewsImageDetails.toggle();
        albumAdapter.refresh();
    }

    private void downloadAlbumClicked(ToolbarMenuItem item) {
        AlbumDownloadController albumDownloadController = new AlbumDownloadController(context);
        albumDownloadController.setPostImages(postImages);
        navigationController.pushController(albumDownloadController);
    }

    private void toggleLayoutModeClicked(ToolbarMenuItem item) {
        PersistableChanState.getAlbumLayoutGridMode().toggle();
        updateRecyclerView(true);

        ToolbarMenuItem menuItem = navigation.findItem(ACTION_TOGGLE_LAYOUT_MODE);

        Drawable gridDrawable;

        if (PersistableChanState.getAlbumLayoutGridMode().get()) {
            gridDrawable = ContextCompat.getDrawable(context, R.drawable.ic_baseline_view_quilt_24);
        } else {
            gridDrawable = ContextCompat.getDrawable(context, R.drawable.ic_baseline_view_comfy_24);
        }

        gridDrawable.setTint(Color.WHITE);
        menuItem.setImage(gridDrawable);
    }

    @Nullable
    @Override
    public ThumbnailView getPreviewImageTransitionView(ChanPostImage postImage) {
        ThumbnailView thumbnail = null;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View view = recyclerView.getChildAt(i);
            if (view instanceof AlbumViewCell) {
                AlbumViewCell cell = (AlbumViewCell) view;
                if (postImage == cell.getPostImage()) {
                    thumbnail = cell.getThumbnailView();
                    break;
                }
            }
        }

        return thumbnail;
    }

    @Override
    public void scrollToImage(ChanPostImage postImage) {
        int index = postImages.indexOf(postImage);
        recyclerView.smoothScrollToPosition(index);
    }

    @Override
    public ImageViewerController.ImageViewerCallback goToPost(ChanPostImage postImage) {
        ThreadController threadController = null;

        if (previousSiblingController instanceof ThreadController) {
            //phone mode
            threadController = (ThreadController) previousSiblingController;
        } else if (previousSiblingController instanceof DoubleNavigationController) {
            //slide mode
            DoubleNavigationController doubleNav = (DoubleNavigationController) previousSiblingController;
            if (doubleNav.getRightController() instanceof ThreadController) {
                threadController = (ThreadController) doubleNav.getRightController();
            }
        } else if (previousSiblingController == null) {
            //split nav has no "sibling" to look at, so we go WAY back to find the view thread controller
            SplitNavigationController splitNav =
                    (SplitNavigationController) this.parentController.parentController.presentedByController;
            threadController = (ThreadController) splitNav.rightController.childControllers.get(0);
            threadController.selectPostImage(postImage);
            //clear the popup here because split nav is weirdly laid out in the stack
            splitNav.popController();
            return threadController;
        }

        if (threadController != null) {
            threadController.selectPostImage(postImage);
            navigationController.popController(false);
            return threadController;
        } else {
            return null;
        }
    }

    private void openImage(AlbumItemCellHolder albumItemCellHolder, ChanPostImage postImage) {
        // Just ignore the showImages request when the image is not loaded
        if (albumItemCellHolder.thumbnailView.getBitmap() != null) {
            final ImageViewerNavigationController imageViewer = new ImageViewerNavigationController(context);
            int index = postImages.indexOf(postImage);
            presentController(imageViewer, false);
            imageViewer.showImages(postImages, index, chanDescriptor, this, this);
        }
    }

    private void showImageLongClickOptions(ChanPostImage postImage) {
        thumbnailLongtapOptionsHelper.onThumbnailLongTapped(
                context,
                true,
                postImage,
                controller -> {
                    presentController(controller);
                    return Unit.INSTANCE;
                },
                chanFilterMutable -> {
                    return Unit.INSTANCE;
                }
        );
    }

    private class AlbumAdapter extends RecyclerView.Adapter<AlbumItemCellHolder> {
        public static final int ALBUM_CELL_TYPE = 1;

        public AlbumAdapter() {
            setHasStableIds(true);
        }

        @Override
        public int getItemViewType(int position) {
            return ALBUM_CELL_TYPE;
        }

        @Override
        public AlbumItemCellHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = inflate(parent.getContext(), R.layout.cell_album_view, parent, false);

            return new AlbumItemCellHolder(view);
        }

        @Override
        public void onBindViewHolder(AlbumItemCellHolder holder, int position) {
            ChanPostImage postImage = postImages.get(position);

            if (postImage != null) {
                boolean canUseHighResCells =
                        ColorizableGridRecyclerView.canUseHighResCells(recyclerView.getCurrentSpanCount());
                boolean isStaggeredGridMode =
                        recyclerView.getLayoutManager() instanceof StaggeredGridLayoutManager;

                holder.cell.bindPostImage(
                        postImage,
                        canUseHighResCells,
                        isStaggeredGridMode,
                        PersistableChanState.getShowAlbumViewsImageDetails().get()
                );
            }
        }

        @Override
        public void onViewRecycled(@NonNull AlbumItemCellHolder holder) {
            holder.cell.unbindPostImage();
        }

        @Override
        public int getItemCount() {
            return postImages.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        public void refresh() {
            notifyDataSetChanged();
        }
    }

    private class AlbumItemCellHolder
            extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener {
        private static final String ALBUM_VIEW_CELL_THUMBNAIL_CLICK_TOKEN = "ALBUM_VIEW_CELL_THUMBNAIL_CLICK";
        private static final String ALBUM_VIEW_CELL_THUMBNAIL_LONG_CLICK_TOKEN = "ALBUM_VIEW_CELL_THUMBNAIL_LONG_CLICK";

        private final AlbumViewCell cell;
        private final PostImageThumbnailView thumbnailView;

        public AlbumItemCellHolder(View itemView) {
            super(itemView);

            cell = (AlbumViewCell) itemView;
            thumbnailView = (PostImageThumbnailView) cell.getThumbnailView();

            thumbnailView.setOnImageClickListener(ALBUM_VIEW_CELL_THUMBNAIL_CLICK_TOKEN, this);
            thumbnailView.setOnImageLongClickListener(ALBUM_VIEW_CELL_THUMBNAIL_LONG_CLICK_TOKEN, this);
        }

        @Override
        public void onClick(View v) {
            int adapterPosition = getAdapterPosition();
            ChanPostImage postImage = postImages.get(adapterPosition);
            openImage(this, postImage);
        }

        @Override
        public boolean onLongClick(View v) {
            int adapterPosition = getAdapterPosition();
            ChanPostImage postImage = postImages.get(adapterPosition);
            showImageLongClickOptions(postImage);
            return true;
        }

    }

    private static class SpanInfo {
        public final int spanCount;
        public final int spanWidth;

        public SpanInfo(int spanCount, int spanWidth) {
            this.spanCount = spanCount;
            this.spanWidth = spanWidth;
        }
    }

    interface ThreadControllerCallbacks {
        void openFiltersController(ChanFilterMutable chanFilterMutable);
    }
}
