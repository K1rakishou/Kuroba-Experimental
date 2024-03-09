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

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.core.helper.DialogFactory;
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener;
import com.github.k1rakishou.chan.core.navigation.RequiresNoBottomNavBar;
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2;
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2OptionsController;
import com.github.k1rakishou.chan.ui.cell.post_thumbnail.PostImageThumbnailView;
import com.github.k1rakishou.chan.ui.globalstate.fastsroller.FastScrollerControllerType;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableGridRecyclerView;
import com.github.k1rakishou.chan.ui.toolbar.Toolbar;
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem;
import com.github.k1rakishou.chan.ui.view.FastScroller;
import com.github.k1rakishou.chan.ui.view.FastScrollerHelper;
import com.github.k1rakishou.chan.ui.view.ThumbnailView;
import com.github.k1rakishou.chan.utils.RecyclerUtils;
import com.github.k1rakishou.common.KotlinExtensionsKt;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.model.data.post.ChanPostImage;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.Lazy;
import kotlin.Unit;

public class AlbumDownloadController
        extends Controller
        implements View.OnClickListener,
        WindowInsetsListener,
        RequiresNoBottomNavBar,
        Toolbar.ToolbarHeightUpdatesCallback {
    private static final String ALBUM_DOWNLOAD_VIEW_CELL_THUMBNAIL_CLICK_TOKEN = "ALBUM_DOWNLOAD_VIEW_CELL_THUMBNAIL_CLICK";

    private ColorizableGridRecyclerView recyclerView;
    private ColorizableFloatingActionButton download;

    private List<AlbumDownloadItem> items = new ArrayList<>();
    private static final Interpolator slowdown = new DecelerateInterpolator(3f);
    private boolean allChecked = true;

    @Nullable
    private FastScroller fastScroller;

    @Inject
    Lazy<ImageSaverV2> imageSaverV2;
    @Inject
    GlobalWindowInsetsManager globalWindowInsetsManager;
    @Inject
    DialogFactory dialogFactory;
    @Inject
    FileManager fileManager;

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    public AlbumDownloadController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflate(context, R.layout.controller_album_download);

        updateTitle();
        navigation.buildMenu(context)
                .withItem(R.drawable.ic_select_all_white_24dp, this::onCheckAllClicked)
                .build();

        download = view.findViewById(R.id.download);
        download.setOnClickListener(this);
        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(context, 3);
        recyclerView.setLayoutManager(gridLayoutManager);
        recyclerView.setSpanWidth(dp(90));

        AlbumAdapter adapter = new AlbumAdapter();
        recyclerView.setAdapter(adapter);

        fastScroller = FastScrollerHelper.create(
                FastScrollerControllerType.Album,
                recyclerView,
                null
        );

        globalWindowInsetsManager.addInsetsUpdatesListener(this);
        requireNavController().requireToolbar().addToolbarHeightUpdatesCallback(this);

        onInsetsChanged();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        globalWindowInsetsManager.removeInsetsUpdatesListener(this);
        requireNavController().requireToolbar().removeToolbarHeightUpdatesCallback(this);

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
        int bottomPaddingDp = calculateBottomPaddingForRecyclerInDp(
                globalWindowInsetsManager,
                null
        );

        int bottomPaddingPx = dp(bottomPaddingDp);
        int fabSize = dp(64f);
        int recyclerBottomPadding = bottomPaddingPx + fabSize;

        KotlinExtensionsKt.updatePaddings(
                recyclerView,
                null,
                FastScrollerHelper.FAST_SCROLLER_WIDTH,
                requireNavController().requireToolbar().getToolbarHeight(),
                recyclerBottomPadding
        );

        if (!ChanSettings.isSplitLayoutMode()) {
            KotlinExtensionsKt.updateMargins(download, null, null, null, null, null, bottomPaddingPx);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == download) {
            onDownloadClicked();
        }
    }

    private void onDownloadClicked() {
        int checkCount = getCheckCount();
        if (checkCount == 0) {
            showToast(R.string.album_download_none_checked);
            return;
        }

        List<ImageSaverV2.SimpleSaveableMediaInfo> simpleSaveableMediaInfoList = new ArrayList<>(items.size());
        for (AlbumDownloadItem item : items) {
            if (item.postImage.isInlined() || item.postImage.getHidden()) {
                // Do not download inlined files via the Album downloads (because they often
                // fail with SSL exceptions) and we can't really trust those files.
                // Also don't download filter hidden items
                continue;
            }

            if (item.checked) {
                ImageSaverV2.SimpleSaveableMediaInfo simpleSaveableMediaInfo =
                        ImageSaverV2.SimpleSaveableMediaInfo.fromChanPostImage(item.postImage);

                if (simpleSaveableMediaInfo != null) {
                    simpleSaveableMediaInfoList.add(simpleSaveableMediaInfo);
                }
            }
        }

        if (simpleSaveableMediaInfoList.isEmpty()) {
            showToast(R.string.album_download_no_suitable_images);
            return;
        }

        ImageSaverV2OptionsController.Options options = new ImageSaverV2OptionsController.Options.MultipleImages(
                (updatedImageSaverV2Options) -> {
                    imageSaverV2.get().saveMany(updatedImageSaverV2Options, simpleSaveableMediaInfoList);

                    // Close this controller
                    navigationController.popController();

                    return Unit.INSTANCE;
                }
        );

        ImageSaverV2OptionsController controller = new ImageSaverV2OptionsController(
                context,
                options
        );

        presentController(controller);
    }

    private void onCheckAllClicked(ToolbarMenuItem menuItem) {
        RecyclerUtils.clearRecyclerCache(recyclerView);

        for (int i = 0, itemsSize = items.size(); i < itemsSize; i++) {
            AlbumDownloadItem item = items.get(i);
            if (item.checked == allChecked) {
                item.checked = !allChecked;
                AlbumDownloadCell cell = (AlbumDownloadCell) recyclerView.findViewHolderForAdapterPosition(i);
                if (cell != null) {
                    setItemChecked(cell, item.checked, true);
                }
            }
        }

        updateAllChecked();
        updateTitle();
    }

    public void setPostImages(List<ChanPostImage> postImages) {
        for (int i = 0, postImagesSize = postImages.size(); i < postImagesSize; i++) {
            ChanPostImage postImage = postImages.get(i);
            if (postImage == null || postImage.isInlined() || postImage.getHidden()) {
                // Do not allow downloading inlined files via the Album downloads (because they often
                // fail with SSL exceptions) and we can't really trust those files.
                // Also don't allow filter hidden items
                continue;
            }

            items.add(new AlbumDownloadItem(postImage, true, i));
        }
    }

    private void updateTitle() {
        navigation.title = getString(R.string.album_download_screen, getCheckCount(), items.size());
        requireNavController().requireToolbar().updateTitle(navigation);
    }

    private void updateAllChecked() {
        allChecked = getCheckCount() == items.size();
    }

    private int getCheckCount() {
        int checkCount = 0;
        for (AlbumDownloadItem item : items) {
            if (item.checked) {
                checkCount++;
            }
        }
        return checkCount;
    }

    private static class AlbumDownloadItem {
        @NonNull
        public ChanPostImage postImage;
        public boolean checked;
        public int id;

        public AlbumDownloadItem(@NonNull ChanPostImage postImage, boolean checked, int id) {
            this.postImage = postImage;
            this.checked = checked;
            this.id = id;
        }
    }

    private class AlbumAdapter extends RecyclerView.Adapter<AlbumDownloadCell> {
        public static final int ALBUM_CELL_TYPE = 1;

        public AlbumAdapter() {
            setHasStableIds(true);
        }

        @Override
        public int getItemViewType(int position) {
            return ALBUM_CELL_TYPE;
        }

        @Override
        public AlbumDownloadCell onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = inflate(parent.getContext(), R.layout.cell_album_download, parent, false);

            return new AlbumDownloadCell(view);
        }

        @Override
        public void onBindViewHolder(AlbumDownloadCell holder, int position) {
            AlbumDownloadItem item = items.get(position);

            holder.thumbnailView.bindPostImage(
                    item.postImage,
                    ColorizableGridRecyclerView.canUseHighResCells(recyclerView.getCurrentSpanCount()),
                    new ThumbnailView.ThumbnailViewOptions(ChanSettings.PostThumbnailScaling.CenterCrop, false, true)
            );

            setItemChecked(holder, item.checked, false);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }
    }

    private class AlbumDownloadCell extends RecyclerView.ViewHolder implements View.OnClickListener {
        private ImageView checkbox;
        private PostImageThumbnailView thumbnailView;

        public AlbumDownloadCell(View itemView) {
            super(itemView);
            itemView.getLayoutParams().height = recyclerView.getRealSpanWidth();
            checkbox = itemView.findViewById(R.id.checkbox);
            thumbnailView = itemView.findViewById(R.id.thumbnail_image_view);
            thumbnailView.setImageClickListener(ALBUM_DOWNLOAD_VIEW_CELL_THUMBNAIL_CLICK_TOKEN, this);
        }

        @Override
        public void onClick(View v) {
            int adapterPosition = getAdapterPosition();
            AlbumDownloadItem item = items.get(adapterPosition);
            item.checked = !item.checked;
            updateAllChecked();
            updateTitle();
            setItemChecked(this, item.checked, true);
        }
    }

    private void setItemChecked(AlbumDownloadCell cell, boolean checked, boolean animated) {
        float scale = checked ? 0.75f : 1f;
        if (animated) {
            cell.thumbnailView.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setInterpolator(slowdown)
                    .setDuration(500)
                    .start();
        } else {
            cell.thumbnailView.setScaleX(scale);
            cell.thumbnailView.setScaleY(scale);
        }

        int drawableId = checked
                ? R.drawable.ic_blue_checkmark_24dp
                : R.drawable.ic_radio_button_unchecked_white_24dp;

        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        cell.checkbox.setImageDrawable(drawable);
    }
}
