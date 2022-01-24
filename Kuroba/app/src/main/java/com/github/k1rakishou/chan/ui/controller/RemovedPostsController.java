package com.github.k1rakishou.chan.ui.controller;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString;
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
import com.github.k1rakishou.model.data.post.ChanPostHide;
import com.github.k1rakishou.model.data.post.ChanPostImage;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import coil.request.Disposable;
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

    public void showRemovePosts(List<RemovedPostsHelper.HiddenOrRemovedPost> removedPosts) {
        BackgroundUtils.ensureMainThread();

        HiddenOrRemovedPost[] hiddenOrRemovedPosts = new HiddenOrRemovedPost[removedPosts.size()];

        for (int i = 0, removedPostsSize = removedPosts.size(); i < removedPostsSize; i++) {
            ChanPost post = removedPosts.get(i).getChanPost();
            ChanPostHide postHide = removedPosts.get(i).getChanPostHide();

            hiddenOrRemovedPosts[i] = new HiddenOrRemovedPost(
                    post.getPostImages(),
                    post.getPostDescriptor(),
                    post.getPostComment().comment(),
                    false,
                    postHide.getOnlyHide(),
                    postHide.getManuallyRestored()
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

        adapter.setRemovedPosts(hiddenOrRemovedPosts);
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

    public static class HiddenOrRemovedPost {
        private List<ChanPostImage> images;
        private PostDescriptor postDescriptor;
        private CharSequence comment;
        private boolean checked;
        private boolean isHidden;
        private boolean manuallyRestored;

        public HiddenOrRemovedPost(
                List<ChanPostImage> images,
                PostDescriptor postDescriptor,
                CharSequence comment,
                boolean checked,
                boolean isHidden,
                boolean manuallyRestored
        ) {
            this.images = images;
            this.postDescriptor = postDescriptor;
            this.comment = comment;
            this.checked = checked;
            this.isHidden = isHidden;
            this.manuallyRestored = manuallyRestored;
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

    public static class RemovedPostAdapter extends ArrayAdapter<HiddenOrRemovedPost> {
        private ImageLoaderV2 imageLoaderV2;
        private ThemeEngine themeEngine;
        private List<HiddenOrRemovedPost> hiddenOrRemovedPosts = new ArrayList<>();

        private Map<PostDescriptor, Disposable> activeImageLoadRequests = new HashMap<>();

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

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public long getItemId(int position) {
            return hiddenOrRemovedPosts.get(position).postDescriptor.hashCode();
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            HiddenOrRemovedPost hiddenOrRemovedPost = getItem(position);
            if (hiddenOrRemovedPost == null) {
                throw new RuntimeException("removedPost is null! position = " + position + ", items count = " + getCount());
            }

            PostDescriptor postDescriptor = hiddenOrRemovedPost.postDescriptor;
            convertView = inflate(getContext(), R.layout.layout_removed_post, parent, false);

            LinearLayout viewHolder = convertView.findViewById(R.id.removed_post_view_holder);
            AppCompatTextView postNo = convertView.findViewById(R.id.removed_post_no);
            AppCompatTextView postComment = convertView.findViewById(R.id.removed_post_comment);
            ColorizableCheckBox checkbox = convertView.findViewById(R.id.removed_post_checkbox);
            AppCompatImageView postImage = convertView.findViewById(R.id.post_image);

            postNo.setTextColor(themeEngine.getChanTheme().getTextColorPrimary());
            postComment.setTextColor(themeEngine.getChanTheme().getTextColorPrimary());

            StringBuilder additionalPostHideInfo = formatAdditionalPostHideInfo(hiddenOrRemovedPost);

            String postNoFormatted = String.format(
                    Locale.ENGLISH,
                    "%s\nNo. %d",
                    additionalPostHideInfo.toString(),
                    hiddenOrRemovedPost.postDescriptor.getPostNo()
            );

            postNo.setText(postNoFormatted);

            postComment.setText(hiddenOrRemovedPost.comment);
            checkbox.setChecked(hiddenOrRemovedPost.isChecked());

            Disposable activeRequestDisposable = activeImageLoadRequests.remove(postDescriptor);
            if (activeRequestDisposable != null && !activeRequestDisposable.isDisposed()) {
                activeRequestDisposable.dispose();
            }

            if (hiddenOrRemovedPost.images.size() > 0) {
                ChanPostImage image = hiddenOrRemovedPost.getImages().get(0);
                HttpUrl thumbnailUrl = image.getThumbnailUrl();

                if (thumbnailUrl != null) {
                    // load only the first image
                    postImage.setVisibility(VISIBLE);

                    Disposable disposable = loadImage(postImage, thumbnailUrl);
                    activeImageLoadRequests.put(postDescriptor, disposable);
                } else {
                    postImage.setImageBitmap(null);
                    postImage.setVisibility(GONE);
                }
            } else {
                postImage.setImageBitmap(null);
                postImage.setVisibility(GONE);
            }

            checkbox.setOnClickListener(v -> onItemClick(position));
            viewHolder.setOnClickListener(v -> onItemClick(position));

            return convertView;
        }

        @NonNull
        private StringBuilder formatAdditionalPostHideInfo(HiddenOrRemovedPost hiddenOrRemovedPost) {
            StringBuilder additionalPostHideInfo = new StringBuilder();
            additionalPostHideInfo.append("(");

            if (!hiddenOrRemovedPost.manuallyRestored) {
                if (hiddenOrRemovedPost.isHidden) {
                    additionalPostHideInfo.append(getString(R.string.hidden_or_removed_posts_post_hidden));
                } else {
                    additionalPostHideInfo.append(getString(R.string.hidden_or_removed_posts_post_removed));
                }
            } else {
                // we are checking additionalPostHideInfo's length to be greater than 1 because
                // we always insert the "(" at the very beginning
                if (additionalPostHideInfo.length() > 1) {
                    additionalPostHideInfo.append(", ");
                }

                additionalPostHideInfo.append(getString(R.string.hidden_or_removed_posts_post_manually_restored));
            }

            additionalPostHideInfo.append(")");

            return additionalPostHideInfo;
        }

        private Disposable loadImage(
                AppCompatImageView postImage,
                HttpUrl thumbnailUrl
        ) {
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

                    postImage.setImageBitmap(null);
                    postImage.setVisibility(GONE);
                }
            };

            return imageLoaderV2.loadFromNetwork(
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
            HiddenOrRemovedPost hiddenOrRemovedPost = getItem(position);
            if (hiddenOrRemovedPost == null) {
                return;
            }

            hiddenOrRemovedPost.setChecked(!hiddenOrRemovedPost.isChecked());
            hiddenOrRemovedPosts.get(position).setChecked(hiddenOrRemovedPost.isChecked());

            notifyDataSetChanged();
        }

        public void setRemovedPosts(HiddenOrRemovedPost[] hiddenOrRemovedPosts) {
            this.hiddenOrRemovedPosts.clear();
            this.hiddenOrRemovedPosts.addAll(Arrays.asList(hiddenOrRemovedPosts));

            clear();
            addAll(this.hiddenOrRemovedPosts);
            notifyDataSetChanged();
        }

        public List<PostDescriptor> getSelectedPostDescriptorList() {
            List<PostDescriptor> selectedPosts = new ArrayList<>();

            for (HiddenOrRemovedPost hiddenOrRemovedPost : hiddenOrRemovedPosts) {
                if (hiddenOrRemovedPost == null) continue;

                if (hiddenOrRemovedPost.isChecked()) {
                    selectedPosts.add(hiddenOrRemovedPost.getPostDescriptor());
                }
            }

            return selectedPosts;
        }

        public void selectAll() {
            if (hiddenOrRemovedPosts.isEmpty()) {
                return;
            }

            // If first item is selected - unselect all other items
            // If it's not selected - select all other items
            boolean select = !hiddenOrRemovedPosts.get(0).isChecked();

            for (int i = 0; i < hiddenOrRemovedPosts.size(); ++i) {
                HiddenOrRemovedPost hiddenOrRemovedPost = getItem(i);
                if (hiddenOrRemovedPost == null) {
                    return;
                }

                hiddenOrRemovedPost.setChecked(select);
                hiddenOrRemovedPosts.get(i).setChecked(select);
            }

            notifyDataSetChanged();
        }

        public void refresh() {
            notifyDataSetChanged();
        }
    }
}
