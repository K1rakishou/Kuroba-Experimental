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

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.core.navigation.RequiresNoBottomNavBar;
import com.github.k1rakishou.chan.ui.cell.AlbumViewCell;
import com.github.k1rakishou.chan.ui.controller.navigation.DoubleNavigationController;
import com.github.k1rakishou.chan.ui.controller.navigation.SplitNavigationController;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableGridRecyclerView;
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem;
import com.github.k1rakishou.chan.ui.view.PostImageThumbnailView;
import com.github.k1rakishou.chan.ui.view.ThumbnailView;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;
import com.github.k1rakishou.model.data.post.ChanPostImage;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getQuantityString;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate;

public class AlbumViewController
        extends Controller
        implements ImageViewerController.ImageViewerCallback,
        ImageViewerController.GoPostCallback,
        RequiresNoBottomNavBar {
    private ColorizableGridRecyclerView recyclerView;

    private List<ChanPostImage> postImages;
    private int targetIndex = -1;
    private ChanDescriptor chanDescriptor;

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    public AlbumViewController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // View setup
        view = inflate(context, R.layout.controller_album_view);
        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(context, 3);
        recyclerView.setLayoutManager(gridLayoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setSpanWidth(dp(120));
        recyclerView.setItemAnimator(null);
        AlbumAdapter albumAdapter = new AlbumAdapter(chanDescriptor);
        recyclerView.setAdapter(albumAdapter);
        recyclerView.scrollToPosition(targetIndex);
    }

    public void setImages(ChanDescriptor chanDescriptor, List<ChanPostImage> postImages, int index, String title) {
        this.chanDescriptor = chanDescriptor;
        this.postImages = postImages;

        // Navigation
        Drawable downloadDrawable = context.getDrawable(R.drawable.ic_file_download_white_24dp);
        downloadDrawable.setTint(Color.WHITE);
        navigation.buildMenu()
                .withItem(Integer.MAX_VALUE, downloadDrawable, this::downloadAlbumClicked).build();

        navigation.title = title;
        navigation.subtitle = getQuantityString(R.plurals.image, postImages.size(), postImages.size());
        targetIndex = index;
    }

    private void downloadAlbumClicked(ToolbarMenuItem item) {
        AlbumDownloadController albumDownloadController = new AlbumDownloadController(context);
        albumDownloadController.setPostImages(chanDescriptor, postImages);
        navigationController.pushController(albumDownloadController);
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

    private class AlbumAdapter extends RecyclerView.Adapter<AlbumItemCellHolder> {
        private ChanDescriptor chanDescriptor;

        public AlbumAdapter(ChanDescriptor chanDescriptor) {
            setHasStableIds(true);

            this.chanDescriptor = chanDescriptor;
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
                holder.cell.setPostImage(postImage);
            }
        }

        @Override
        public int getItemCount() {
            return postImages.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }

    private class AlbumItemCellHolder
            extends RecyclerView.ViewHolder
            implements View.OnClickListener {
        private AlbumViewCell cell;
        private PostImageThumbnailView thumbnailView;

        public AlbumItemCellHolder(View itemView) {
            super(itemView);
            cell = (AlbumViewCell) itemView;
            thumbnailView = itemView.findViewById(R.id.thumbnail_view);
            thumbnailView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int adapterPosition = getAdapterPosition();
            ChanPostImage postImage = postImages.get(adapterPosition);
            openImage(this, postImage);
        }
    }
}
