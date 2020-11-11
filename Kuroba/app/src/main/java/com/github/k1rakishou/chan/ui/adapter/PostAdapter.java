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
package com.github.k1rakishou.chan.ui.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager;
import com.github.k1rakishou.chan.core.manager.PostFilterManager;
import com.github.k1rakishou.chan.ui.cell.PostCell;
import com.github.k1rakishou.chan.ui.cell.PostCellInterface;
import com.github.k1rakishou.chan.ui.cell.ThreadStatusCell;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.chan.utils.BackgroundUtils;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.core_themes.ChanTheme;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;
import com.github.k1rakishou.model.data.descriptor.PostDescriptor;
import com.github.k1rakishou.model.data.post.ChanPost;
import com.github.k1rakishou.model.data.post.PostIndexed;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;

import static com.github.k1rakishou.common.AndroidUtils.inflate;

public class PostAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "PostAdapter";

    //we don't recycle POST cells because of layout changes between cell contents
    public static final int TYPE_POST = 0;
    private static final int TYPE_STATUS = 1;
    private static final int TYPE_POST_STUB = 2;
    private static final int TYPE_LAST_SEEN = 3;

    @Inject
    ChanThreadViewableInfoManager chanThreadViewableInfoManager;
    @Inject
    ThemeEngine themeEngine;

    private final PostAdapterCallback postAdapterCallback;
    private final PostCellInterface.PostCellCallback postCellCallback;
    private RecyclerView recyclerView;
    private PostFilterManager postFilterManager;
    private ChanTheme chanTheme;

    private final ThreadStatusCell.Callback statusCellCallback;
    private final List<ChanPost> displayList = new ArrayList<>();
    private final List<PostIndexed> indexedDisplayList = new ArrayList<>();
    /**
     * A hack for OnDemandContentLoader see comments in {@link #onViewRecycled}
     */
    private final Set<Long> updatingPosts = new HashSet<>(64);

    private ChanDescriptor chanDescriptor = null;
    private String error = null;
    private PostDescriptor highlightedPostDescriptor;
    private String highlightedPostId;
    private Set<Long> highlightedPostNo = new HashSet<>();
    private CharSequence highlightedPostTripcode;
    private long selectedPost = -1L;
    private int lastSeenIndicatorPosition = -1;

    private ChanSettings.PostViewMode postViewMode;
    private boolean compact = false;

    public PostAdapter(
            PostFilterManager postFilterManager,
            RecyclerView recyclerView,
            PostAdapterCallback postAdapterCallback,
            PostCellInterface.PostCellCallback postCellCallback,
            ThreadStatusCell.Callback statusCellCallback
    ) {
        AppModuleAndroidUtils.extractStartActivityComponent(recyclerView.getContext())
                .inject(this);

        this.postFilterManager = postFilterManager;
        this.recyclerView = recyclerView;
        this.postAdapterCallback = postAdapterCallback;
        this.postCellCallback = postCellCallback;
        this.statusCellCallback = statusCellCallback;

        themeEngine.preloadAttributeResource(
                recyclerView.getContext(),
                android.R.attr.selectableItemBackgroundBorderless
        );

        setHasStableIds(true);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context inflateContext = parent.getContext();
        switch (viewType) {
            case TYPE_POST:
                int layout = 0;
                switch (postViewMode) {
                    case LIST:
                        layout = R.layout.cell_post;
                        break;
                    case CARD:
                        layout = R.layout.cell_post_card;
                        break;
                }

                PostCellInterface postCell = (PostCellInterface) inflate(
                        inflateContext,
                        layout,
                        parent,
                        false
                );

                return new PostViewHolder(postCell);
            case TYPE_POST_STUB:
                PostCellInterface postCellStub = (PostCellInterface) inflate(
                        inflateContext,
                        R.layout.cell_post_stub,
                        parent,
                        false
                );
                return new PostViewHolder(postCellStub);
            case TYPE_LAST_SEEN:
                return new LastSeenViewHolder(
                        themeEngine,
                        inflate(inflateContext, R.layout.cell_post_last_seen, parent, false)
                );
            case TYPE_STATUS:
                ThreadStatusCell statusCell = (ThreadStatusCell) inflate(
                        inflateContext,
                        R.layout.cell_thread_status,
                        parent,
                        false
                );

                StatusViewHolder statusViewHolder = new StatusViewHolder(statusCell);
                statusCell.setCallback(statusCellCallback);
                statusCell.setError(error);
                return statusViewHolder;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        int itemViewType = getItemViewType(position);

        switch (itemViewType) {
            case TYPE_POST:
            case TYPE_POST_STUB:
                if (chanDescriptor == null) {
                    throw new IllegalStateException("catalogDescriptor cannot be null");
                }

                PostViewHolder postViewHolder = (PostViewHolder) holder;
                int postPosition = getPostPosition(position);
                ChanPost post = displayList.get(postPosition);
                PostIndexed postIndexed = indexedDisplayList.get(postPosition);
                boolean highlight = shouldHighlightPost(post);

                PostCellInterface postCell = ((PostCellInterface) postViewHolder.itemView);

                postCell.setPost(
                        chanDescriptor,
                        post,
                        postIndexed.getCurrentPostIndex(),
                        postIndexed.getRealPostIndex(),
                        postCellCallback,
                        false,
                        highlight,
                        post.postNo() == selectedPost,
                        -1,
                        true,
                        postViewMode,
                        compact,
                        chanTheme
                );

                if (itemViewType == TYPE_POST_STUB) {
                    holder.itemView.setOnClickListener(v -> postAdapterCallback.onUnhidePostClick(post));
                }
                break;
            case TYPE_STATUS:
                ((ThreadStatusCell) holder.itemView).update();
                break;
            case TYPE_LAST_SEEN:
                ((LastSeenViewHolder) holder).updateLabelColor();
                break;
        }
    }

    private boolean shouldHighlightPost(ChanPost post) {
        return post.getPostDescriptor().equals(highlightedPostDescriptor)
                || post.getPosterId().equals(highlightedPostId)
                || highlightedPostNo.contains(post.postNo())
                || (post.getTripcode() != null && post.getTripcode().equals(highlightedPostTripcode));
    }

    @Override
    public int getItemCount() {
        int size = displayList.size();

        if (showStatusView()) {
            size++;
        }

        if (lastSeenIndicatorPosition >= 0) {
            size++;
        }

        return size;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == lastSeenIndicatorPosition) {
            return TYPE_LAST_SEEN;
        } else if (showStatusView() && position == getItemCount() - 1) {
            return TYPE_STATUS;
        } else {
            ChanPost post = displayList.get(getPostPosition(position));
            if (postFilterManager.getFilterStub(post.getPostDescriptor())) {
                return TYPE_POST_STUB;
            } else {
                return TYPE_POST;
            }
        }
    }

    @Override
    public long getItemId(int position) {
        int itemViewType = getItemViewType(position);
        if (itemViewType == TYPE_STATUS) {
            return -1;
        } else if (itemViewType == TYPE_LAST_SEEN) {
            return -2;
        } else {
            ChanPost post = displayList.get(getPostPosition(position));
            int repliesFromCount = post.getRepliesFromCount();
            return ((long) repliesFromCount << 32L) + post.postNo() + (compact ? 1L : 0L);
        }
    }

    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        //this is a hack to make sure text is selectable
        super.onViewAttachedToWindow(holder);
        if (holder.itemView instanceof PostCell) {
            PostCell cell = (PostCell) holder.itemView;
            cell.findViewById(R.id.comment).setEnabled(false);
            cell.findViewById(R.id.comment).setEnabled(true);
        }
    }

    /**
     * Do not use onViewAttachedToWindow/onViewDetachedFromWindow in PostCell/CardPostCell etc to
     * bind/unbind posts because it's really bad and will cause a shit-ton of problems. We should
     * only use onViewRecycled() instead (because onViewAttachedToWindow/onViewDetachedFromWindow
     * and onViewRecycled() have different lifecycles). So by using onViewAttachedToWindow/
     * onViewDetachedFromWindow we may end up in a situation where we unbind a post
     * (null out the callbacks) but in reality the post (view) is still alive in internal RecycleView
     * cache so the next time recycler decides to update the view it will either throw a NPE or will
     * just show an empty view. Using onViewRecycled to unbind posts is the correct way to handle
     * this issue.
     */
    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder.itemView instanceof PostCellInterface) {
            ChanPost post = ((PostCellInterface) holder.itemView).getPost();
            Objects.requireNonNull(post);

            long postNo = post.postNo();
            boolean isActuallyRecycling = !updatingPosts.remove(postNo);

            /**
             * Hack! (kinda)
             *
             * We have some managers that we want to release all their resources once a post is
             * recycled. However, onViewRecycled may not only get called when the view is offscreen
             * and RecyclerView decides to recycle it, but also when we call notifyItemChanged
             * {@link #updatePost}. So the point of this hack is to check whether onViewRecycled was
             * called because of us calling notifyItemChanged or it was the RecyclerView that
             * actually decided to recycle it. For that we put a post id into a HashSet before
             * calling notifyItemChanged and then checking, right here, whether the current post's
             * id exists in the HashSet. If it exists in the HashSet that means that it was
             * (most likely) us calling notifyItemChanged that triggered onViewRecycled call.
             * */
            ((PostCellInterface) holder.itemView).onPostRecycled(isActuallyRecycling);
        }
    }

    public void setThread(
            ChanDescriptor chanDescriptor,
            List<PostIndexed> indexedPosts,
            ChanTheme chanTheme
    ) {
        BackgroundUtils.ensureMainThread();

        this.chanDescriptor = chanDescriptor;
        this.chanTheme = chanTheme;
        showError(null);

        List<ChanPost> posts = extractPosts(indexedPosts);

        updatingPosts.clear();
        displayList.clear();
        displayList.addAll(posts);
        indexedDisplayList.clear();
        indexedDisplayList.addAll(indexedPosts);
        lastSeenIndicatorPosition = getLastSeenIndicatorPosition(chanDescriptor);

        notifyDataSetChanged();
        Logger.d(TAG, "setThread() notifyDataSetChanged called, displayList.size=" + displayList.size());
    }

    private List<ChanPost> extractPosts(List<PostIndexed> indexedPosts) {
        if (indexedPosts.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChanPost> posts = new ArrayList<>(indexedPosts.size());

        for (PostIndexed indexedPost : indexedPosts) {
            posts.add(indexedPost.getPost());
        }

        return posts;
    }

    private int getLastSeenIndicatorPosition(ChanDescriptor chanDescriptor) {
        Integer index = chanThreadViewableInfoManager.view(chanDescriptor, chanThreadViewableInfoView -> {
            if (chanThreadViewableInfoView.getLastViewedPostNo() >= 0) {
                // Do not process the last post, the indicator does not have to appear at the bottom
                for (int i = 0, displayListSize = displayList.size() - 1; i < displayListSize; i++) {
                    ChanPost post = displayList.get(i);
                    if (post.postNo() == chanThreadViewableInfoView.getLastViewedPostNo()) {
                        return i + 1;
                    }
                }
            }

            return -1;
        });

        if (index == null) {
            return -1;
        }

        return index;
    }

    public List<PostDescriptor> getDisplayList() {
        if (displayList.isEmpty()) {
            return new ArrayList<>();
        }

        int size = Math.min(16, displayList.size());
        List<PostDescriptor> postDescriptors = new ArrayList<>(size);

        for (ChanPost chanPost : displayList) {
            postDescriptors.add(chanPost.getPostDescriptor());
        }

        return postDescriptors;
    }

    public void cleanup() {
        highlightedPostDescriptor = null;
        highlightedPostId = null;
        highlightedPostNo.clear();
        highlightedPostTripcode = null;

        selectedPost = -1L;
        lastSeenIndicatorPosition = -1;
        error = null;

        updatingPosts.clear();
        displayList.clear();
        indexedDisplayList.clear();

        notifyDataSetChanged();
    }

    public void showError(String error) {
        this.error = error;
        if (showStatusView()) {
            final int childCount = recyclerView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = recyclerView.getChildAt(i);
                if (child instanceof ThreadStatusCell) {
                    ThreadStatusCell threadStatusCell = (ThreadStatusCell) child;
                    threadStatusCell.setError(error);
                    threadStatusCell.update();
                }
            }
        }
    }

    public void highlightPost(PostDescriptor postDescriptor) {
        highlightedPostDescriptor = postDescriptor;
        highlightedPostId = null;
        highlightedPostNo.clear();
        highlightedPostTripcode = null;
        notifyDataSetChanged();
    }

    public void highlightPostId(String id) {
        highlightedPostDescriptor = null;
        highlightedPostId = id;
        highlightedPostNo.clear();
        highlightedPostTripcode = null;
        notifyDataSetChanged();
    }

    public void highlightPostTripcode(CharSequence tripcode) {
        highlightedPostDescriptor = null;
        highlightedPostId = null;
        highlightedPostNo.clear();
        highlightedPostTripcode = tripcode;
        notifyDataSetChanged();
    }

    public void highlightPostNos(Set<Long> postNos) {
        highlightedPostDescriptor = null;
        highlightedPostId = null;
        highlightedPostNo.clear();
        highlightedPostNo.addAll(postNos);
        highlightedPostTripcode = null;
        notifyDataSetChanged();
    }

    public void selectPost(long no) {
        selectedPost = no;
        notifyDataSetChanged();
    }

    public void setPostViewMode(ChanSettings.PostViewMode postViewMode) {
        this.postViewMode = postViewMode;
    }

    public void setCompact(boolean compact) {
        if (this.compact != compact) {
            this.compact = compact;
            notifyDataSetChanged();
        }
    }

    public int getPostPosition(int position) {
        int postPosition = position;
        if (lastSeenIndicatorPosition >= 0 && position > lastSeenIndicatorPosition) {
            postPosition--;
        }
        return postPosition;
    }

    public int getScrollPosition(int displayPosition) {
        int postPosition = displayPosition;
        if (lastSeenIndicatorPosition >= 0 && displayPosition > lastSeenIndicatorPosition) {
            postPosition++;
        }
        return postPosition;
    }

    private boolean showStatusView() {
        ChanDescriptor chanDescriptor = postAdapterCallback.getCurrentChanDescriptor();
        // the chanDescriptor can be null while this adapter is used between cleanup and the removal
        // of the recyclerview from the view hierarchy, although it's rare.
        return chanDescriptor != null;
    }

    public void updatePost(ChanPost post) {
        BackgroundUtils.ensureMainThread();

        int postIndex = displayList.indexOf(post);
        if (postIndex < 0) {
            return;
        }

        if (lastSeenIndicatorPosition >= 0 && postIndex >= lastSeenIndicatorPosition) {
            ++postIndex;
        }

        if (postIndex < 0 && postIndex > getItemCount()) {
            Logger.e(TAG, "postIndex is out of bounds (0.." + postIndex + ".." + getItemCount() + ")");
            return;
        }

        updatingPosts.add(post.postNo());
        notifyItemChanged(postIndex);
    }

    public long getPostNo(int itemPosition) {
        int correctedPosition = getPostPosition(itemPosition);
        int itemViewType = getItemViewType(correctedPosition);

        if (itemViewType == TYPE_STATUS) {
            correctedPosition = getPostPosition(correctedPosition - 1);
            itemViewType = getItemViewType(correctedPosition);
        }

        if (itemViewType != TYPE_POST && itemViewType != TYPE_POST_STUB) {
            return -1L;
        }

        if (correctedPosition < 0 || correctedPosition >= displayList.size()) {
            return -1L;
        }

        ChanPost post = displayList.get(correctedPosition);
        return post.postNo();
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        public PostViewHolder(PostCellInterface postView) {
            super((View) postView);
        }
    }

    public static class StatusViewHolder extends RecyclerView.ViewHolder {
        public StatusViewHolder(ThreadStatusCell threadStatusCell) {
            super(threadStatusCell);
        }
    }

    public static class LastSeenViewHolder extends RecyclerView.ViewHolder {
        private final ThemeEngine themeEngine;

        public LastSeenViewHolder(ThemeEngine themeEngine, View itemView) {
            super(itemView);

            this.themeEngine = themeEngine;
            updateLabelColor();
        }

        public void updateLabelColor() {
            itemView.setBackgroundColor(themeEngine.getChanTheme().getAccentColor());
        }
    }

    public interface PostAdapterCallback {
        @Nullable ChanDescriptor getCurrentChanDescriptor();
        void onUnhidePostClick(ChanPost post);
    }
}
