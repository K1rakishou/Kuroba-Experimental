package com.github.k1rakishou.chan.ui.controller;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.cache.CacheFileType;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.core.image.ImageLoaderV2;
import com.github.k1rakishou.chan.ui.helper.RemovedPostsHelper;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCheckBox;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableListView;
import com.github.k1rakishou.chan.utils.BackgroundUtils;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.model.data.descriptor.PostDescriptor;
import com.github.k1rakishou.model.data.post.ChanPost;
import com.github.k1rakishou.model.data.post.ChanPostImage;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import okhttp3.HttpUrl;

public class RemovedPostsController
        extends BaseFloatingController
        implements View.OnClickListener, ThemeEngine.ThemeChangesListener {
    private static final String TAG = "RemovedPostsController";

    @Inject
    ImageLoaderV2 imageLoaderV2;
    @Inject
    ThemeEngine themeEngine;

    private RemovedPostsHelper removedPostsHelper;

    private ConstraintLayout viewHolder;
    private ColorizableListView postsListView;
    private ColorizableBarButton restorePostsButton;
    private ColorizableBarButton selectAllButton;

    @Override
    protected int getLayoutId() {
        return R.layout.layout_removed_posts;
    }

    @Nullable
    private RemovedPostAdapter adapter;

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    public RemovedPostsController(Context context, RemovedPostsHelper removedPostsHelper) {
        super(context);
        this.removedPostsHelper = removedPostsHelper;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        viewHolder = view.findViewById(R.id.removed_posts_view_holder);
        restorePostsButton = view.findViewById(R.id.removed_posts_restore_posts);
        selectAllButton = view.findViewById(R.id.removed_posts_select_all);
        postsListView = view.findViewById(R.id.removed_posts_posts_list);

        viewHolder.setOnClickListener(this);
        restorePostsButton.setOnClickListener(this);
        selectAllButton.setOnClickListener(this);

        themeEngine.addListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        themeEngine.removeListener(this);
    }

    @Override
    public void onThemeChanged() {
        ListAdapter adapter = postsListView.getAdapter();
        if (adapter instanceof RemovedPostAdapter) {
            ((RemovedPostAdapter) adapter).refresh();
        }
    }

    @Override
    public boolean onBack() {
        removedPostsHelper.pop();
        return true;
    }

    public void showRemovePosts(List<ChanPost> removedPosts) {
        BackgroundUtils.ensureMainThread();

        RemovedPost[] removedPostsArray = new RemovedPost[removedPosts.size()];

        for (int i = 0, removedPostsSize = removedPosts.size(); i < removedPostsSize; i++) {
            ChanPost post = removedPosts.get(i);

            removedPostsArray[i] = new RemovedPost(
                    post.getPostImages(),
                    post.getPostDescriptor(),
                    post.getPostComment().comment(),
                    false
            );
        }

        if (adapter == null) {
            adapter = new RemovedPostAdapter(
                    context,
                    imageLoaderV2,
                    themeEngine,
                    R.layout.layout_removed_posts
            );

            postsListView.setAdapter(adapter);
        }

        adapter.setRemovedPosts(removedPostsArray);
    }

    @Override
    public void onClick(View v) {
        if (v == viewHolder) {
            removedPostsHelper.pop();
        } else if (v == restorePostsButton) {
            onRestoreClicked();
        } else if (v == selectAllButton) {
            if (adapter != null) {
                adapter.selectAll();
            }
        }
    }

    private void onRestoreClicked() {
        if (adapter == null) {
            return;
        }

        List<PostDescriptor> selectedPosts = adapter.getSelectedPostDescriptorList();
        if (selectedPosts.isEmpty()) {
            return;
        }

        removedPostsHelper.onRestoreClicked(selectedPosts);
    }

    public static class RemovedPost {
        private List<ChanPostImage> images;
        private PostDescriptor postDescriptor;
        private CharSequence comment;
        private boolean checked;

        public RemovedPost(
                List<ChanPostImage> images,
                PostDescriptor postDescriptor,
                CharSequence comment,
                boolean checked
        ) {
            this.images = images;
            this.postDescriptor = postDescriptor;
            this.comment = comment;
            this.checked = checked;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
        }

        public List<ChanPostImage> getImages() {
            return images;
        }

        public PostDescriptor getPostDescriptor() {
            return postDescriptor;
        }

        public CharSequence getComment() {
            return comment;
        }

        public boolean isChecked() {
            return checked;
        }
    }

    public static class RemovedPostAdapter extends ArrayAdapter<RemovedPost> {
        private ImageLoaderV2 imageLoaderV2;
        private ThemeEngine themeEngine;
        private List<RemovedPost> removedPostsCopy = new ArrayList<>();

        public RemovedPostAdapter(
                @NonNull Context context,
                ImageLoaderV2 imageLoaderV2,
                ThemeEngine themeEngine,
                int resource
        ) {
            super(context, resource);

            this.imageLoaderV2 = imageLoaderV2;
            this.themeEngine = themeEngine;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            RemovedPost removedPost = getItem(position);

            if (removedPost == null) {
                throw new RuntimeException(
                        "removedPost is null! position = " + position + ", items count = " + getCount());
            }

            if (convertView == null) {
                convertView = inflate(getContext(), R.layout.layout_removed_post, parent, false);
            }

            LinearLayout viewHolder = convertView.findViewById(R.id.removed_post_view_holder);
            AppCompatTextView postNo = convertView.findViewById(R.id.removed_post_no);
            AppCompatTextView postComment = convertView.findViewById(R.id.removed_post_comment);
            ColorizableCheckBox checkbox = convertView.findViewById(R.id.removed_post_checkbox);
            AppCompatImageView postImage = convertView.findViewById(R.id.post_image);

            postNo.setTextColor(themeEngine.getChanTheme().getTextColorPrimary());
            postComment.setTextColor(themeEngine.getChanTheme().getTextColorPrimary());

            postNo.setText(String.format(Locale.ENGLISH, "No. %d", removedPost.postDescriptor.getPostNo()));
            postComment.setText(removedPost.comment);
            checkbox.setChecked(removedPost.isChecked());
            postImage.setVisibility(GONE);

            if (removedPost.images.size() > 0) {
                ChanPostImage image = removedPost.getImages().get(0);
                HttpUrl thumbnailUrl = image.getThumbnailUrl();

                if (thumbnailUrl != null) {
                    // load only the first image
                    postImage.setVisibility(VISIBLE);

                    loadImage(postImage, thumbnailUrl);
                }
            }

            checkbox.setOnClickListener(v -> onItemClick(position));
            viewHolder.setOnClickListener(v -> onItemClick(position));

            return convertView;
        }

        private void loadImage(AppCompatImageView postImage, HttpUrl thumbnailUrl) {
            ImageLoaderV2.FailureAwareImageListener listener = new ImageLoaderV2.FailureAwareImageListener() {
                @Override
                public void onResponse(@NotNull BitmapDrawable drawable, boolean isImmediate) {
                    postImage.setImageBitmap(drawable.getBitmap());
                }

                @Override
                public void onNotFound() {
                    onResponseError(new IOException("Not found"));
                }

                @Override
                public void onResponseError(@NotNull Throwable error) {
                    Logger.e(TAG, "Error while trying to download post image", error);
                    postImage.setVisibility(GONE);
                }
            };

            imageLoaderV2.loadFromNetwork(
                    getContext(),
                    thumbnailUrl.toString(),
                    CacheFileType.PostMediaThumbnail,
                    new ImageLoaderV2.ImageSize.FixedImageSize(
                            postImage.getWidth(),
                            postImage.getHeight()
                    ),
                    Collections.emptyList(),
                    listener,
                    null
            );
        }

        public void onItemClick(int position) {
            RemovedPost rp = getItem(position);
            if (rp == null) {
                return;
            }

            rp.setChecked(!rp.isChecked());
            removedPostsCopy.get(position).setChecked(rp.isChecked());

            notifyDataSetChanged();
        }

        public void setRemovedPosts(RemovedPost[] removedPostsArray) {
            removedPostsCopy.clear();
            removedPostsCopy.addAll(Arrays.asList(removedPostsArray));

            clear();
            addAll(removedPostsCopy);
            notifyDataSetChanged();
        }

        public List<PostDescriptor> getSelectedPostDescriptorList() {
            List<PostDescriptor> selectedPosts = new ArrayList<>();

            for (RemovedPost removedPost : removedPostsCopy) {
                if (removedPost == null) continue;

                if (removedPost.isChecked()) {
                    selectedPosts.add(removedPost.getPostDescriptor());
                }
            }

            return selectedPosts;
        }

        public void selectAll() {
            if (removedPostsCopy.isEmpty()) {
                return;
            }

            // If first item is selected - unselect all other items
            // If it's not selected - select all other items
            boolean select = !removedPostsCopy.get(0).isChecked();

            for (int i = 0; i < removedPostsCopy.size(); ++i) {
                RemovedPost rp = getItem(i);
                if (rp == null) {
                    return;
                }

                rp.setChecked(select);
                removedPostsCopy.get(i).setChecked(select);
            }

            notifyDataSetChanged();
        }

        public void refresh() {
            notifyDataSetChanged();
        }
    }
}
