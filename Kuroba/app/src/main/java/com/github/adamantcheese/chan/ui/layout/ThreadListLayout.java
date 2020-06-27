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
package com.github.adamantcheese.chan.ui.layout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.interactors.ExtractPostMapInfoHolderUseCase;
import com.github.adamantcheese.chan.core.manager.PostFilterManager;
import com.github.adamantcheese.chan.core.manager.ReplyViewStateManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.presenter.ReplyPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.ui.adapter.PostAdapter;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.ui.cell.PostCell;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface;
import com.github.adamantcheese.chan.ui.cell.PostStubCell;
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.ui.view.FastScroller;
import com.github.adamantcheese.chan.ui.view.FastScrollerHelper;
import com.github.adamantcheese.chan.ui.view.PostInfoMapItemDecoration;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import org.jetbrains.annotations.Nullable;

import java.util.Calendar;
import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode.CARD;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode.LIST;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.TYPE_POST;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDimen;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;
import static com.github.adamantcheese.chan.utils.AndroidUtils.waitForLayout;

/**
 * A layout that wraps around a {@link RecyclerView} and a {@link ReplyLayout} to manage showing and replying to posts.
 */
public class ThreadListLayout
        extends FrameLayout
        implements ReplyLayout.ReplyLayoutCallback {
    private static final String TAG = "ThreadListLayout";
    public static final int MAX_SMOOTH_SCROLL_DISTANCE = 20;

    @Inject
    WatchManager watchManager;
    @Inject
    ThemeHelper themeHelper;
    @Inject
    PostFilterManager postFilterManager;
    @Inject
    ReplyViewStateManager replyViewStateManager;
    @Inject
    ExtractPostMapInfoHolderUseCase extractPostMapInfoHolderUseCase;

    private ReplyLayout reply;
    private TextView searchStatus;
    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;
    private FastScroller fastScroller;
    private PostInfoMapItemDecoration postInfoMapItemDecoration;
    private PostAdapter postAdapter;
    private ThreadListLayoutPresenterCallback callback;
    private ThreadListLayoutCallback threadListLayoutCallback;
    private boolean replyOpen;
    private ChanSettings.PostViewMode postViewMode;
    private int spanCount = 2;
    private int background;
    private boolean searchOpen;
    private int lastPostCount;

    @Nullable
    private ChanThread showingThread;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            onRecyclerViewScrolled();
        }
    };

    public ThreadListLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void onDestroy() {
        forceRecycleAllPostViews();
        recyclerView.setAdapter(null);
    }

    private void forceRecycleAllPostViews() {
        RecyclerView.Adapter adapter = recyclerView.getAdapter();
        if (adapter instanceof PostAdapter) {
            recyclerView.getRecycledViewPool().clear();
            ((PostAdapter) adapter).cleanup();
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        inject(this);

        // View binding
        reply = findViewById(R.id.reply);
        searchStatus = findViewById(R.id.search_status);
        recyclerView = findViewById(R.id.recycler_view);

        LayoutParams params = (LayoutParams) reply.getLayoutParams();
        params.gravity = Gravity.BOTTOM;
        reply.setLayoutParams(params);

        // View setup
        reply.setCallback(this);
        searchStatus.setTypeface(themeHelper.getTheme().mainFont);
    }

    public void setCallbacks(
            PostAdapter.PostAdapterCallback postAdapterCallback,
            PostCell.PostCellCallback postCellCallback,
            ThreadStatusCell.Callback statusCellCallback,
            ThreadListLayoutPresenterCallback callback,
            ThreadListLayoutCallback threadListLayoutCallback
    ) {
        this.callback = callback;
        this.threadListLayoutCallback = threadListLayoutCallback;

        postAdapter = new PostAdapter(
                postFilterManager,
                recyclerView,
                postAdapterCallback,
                postCellCallback,
                statusCellCallback,
                themeHelper.getTheme()
        );

        recyclerView.setAdapter(postAdapter);
        // Man, fuck the RecycledViewPool. Sometimes when scrolling away from a view and the swiftly
        // back to it onViewRecycled() will be called TWICE for that view. Setting setMaxRecycledViews
        // for TYPE_POST to 0 solves this problem. What a buggy piece of shit.
        recyclerView.getRecycledViewPool().setMaxRecycledViews(TYPE_POST, 0);
        recyclerView.addOnScrollListener(scrollListener);

        setFastScroll(false);
        attachToolbarScroll(true);

        reply.setPadding(0, 0, 0, 0);
        updatePaddings(searchStatus, -1, -1, searchStatus.getPaddingTop() + toolbarHeight(), -1);
    }

    private void onRecyclerViewScrolled() {
        // onScrolled can be called after cleanup()
        if (showingThread != null) {
            int[] indexTop = getIndexAndTop();

            showingThread.getLoadable().setListViewIndex(indexTop[0]);
            showingThread.getLoadable().setListViewTop(indexTop[1]);

            int last = getCompleteBottomAdapterPosition();
            if (last == postAdapter.getItemCount() - 1 && last > lastPostCount) {
                lastPostCount = last;

                // As requested by the RecyclerView, make sure that the adapter isn't changed
                // while in a layout pass. Postpone to the next frame.
                mainHandler.post(() -> ThreadListLayout.this.callback.onListScrolledToBottom());
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int cardWidth = getDimen(R.dimen.grid_card_width);
        int gridCountSetting = ChanSettings.boardGridSpanCount.get();
        boolean compactMode;
        if (gridCountSetting > 0) {
            spanCount = gridCountSetting;
            compactMode = (getMeasuredWidth() / spanCount) < dp(120);
        } else {
            spanCount = Math.max(1, Math.round((float) getMeasuredWidth() / cardWidth));
            compactMode = false;
        }

        if (postViewMode == CARD) {
            postAdapter.setCompact(compactMode);

            ((GridLayoutManager) layoutManager).setSpanCount(spanCount);
        }
    }

    public void setPostViewMode(ChanSettings.PostViewMode postViewMode) {
        if (this.postViewMode != postViewMode) {
            this.postViewMode = postViewMode;

            layoutManager = null;

            switch (postViewMode) {
                case LIST:
                    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext()) {
                        @Override
                        public boolean requestChildRectangleOnScreen(
                                RecyclerView parent,
                                View child,
                                Rect rect,
                                boolean immediate,
                                boolean focusedChildVisible
                        ) {
                            return false;
                        }
                    };
                    setRecyclerViewPadding();
                    recyclerView.setLayoutManager(linearLayoutManager);
                    layoutManager = linearLayoutManager;

                    if (background != R.attr.backcolor) {
                        background = R.attr.backcolor;
                        setBackgroundColor(getAttrColor(getContext(), R.attr.backcolor));
                    }

                    break;
                case CARD:
                    GridLayoutManager gridLayoutManager =
                            new GridLayoutManager(null, spanCount, GridLayoutManager.VERTICAL, false) {
                                @Override
                                public boolean requestChildRectangleOnScreen(
                                        RecyclerView parent,
                                        View child,
                                        Rect rect,
                                        boolean immediate,
                                        boolean focusedChildVisible
                                ) {
                                    return false;
                                }
                            };
                    setRecyclerViewPadding();
                    recyclerView.setLayoutManager(gridLayoutManager);
                    layoutManager = gridLayoutManager;

                    if (background != R.attr.backcolor_secondary) {
                        background = R.attr.backcolor_secondary;
                        setBackgroundColor(getAttrColor(getContext(), R.attr.backcolor_secondary));
                    }

                    break;
            }

            recyclerView.getRecycledViewPool().clear();

            postAdapter.setPostViewMode(postViewMode);
        }
    }

    public void showPosts(
            ChanThread thread,
            PostsFilter filter,
            boolean initial,
            boolean refreshAfterHideOrRemovePosts
    ) {
        showingThread = thread;

        if (initial) {
            reply.bindLoadable(showingThread.getLoadable());

            recyclerView.setLayoutManager(null);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.getRecycledViewPool().clear();

            int index = thread.getLoadable().listViewIndex;
            int top = thread.getLoadable().listViewTop;

            switch (postViewMode) {
                case LIST:
                    ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(index, top);
                    break;
                case CARD:
                    ((GridLayoutManager) layoutManager).scrollToPositionWithOffset(index, top);
                    break;
            }

            party();
        }

        setFastScroll(true);

        postAdapter.setThread(
                thread.getLoadable(),
                thread.getPostPreloadedInfoHolder(),
                filter.apply(thread.getPosts(), thread.getLoadable()),
                refreshAfterHideOrRemovePosts
        );
    }

    public boolean onBack() {
        if (reply.onBack()) {
            return true;
        } else if (replyOpen) {
            openReply(false);
            return true;
        } else {
            return threadListLayoutCallback.threadBackPressed();
        }
    }

    public boolean sendKeyEvent(KeyEvent event) {
        if (ChanSettings.volumeKeysScrolling.get()) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        boolean down = event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN;
                        int scroll = (int) (getHeight() * 0.75);
                        recyclerView.smoothScrollBy(0, down ? scroll : -scroll);
                    }
                    return true;
            }
        }
        return false;
    }

    public void gainedFocus() {
        showToolbarIfNeeded();
    }

    public void openReply(boolean open) {
        if (showingThread == null || replyOpen == open) {
            return;
        }

        ChanThread thread = getThread();
        if (thread == null) {
            return;
        }

        Loadable loadable = thread.getLoadable();
        if (loadable != null) {
            if (loadable.mode == Loadable.Mode.INVALID) {
                throw new RuntimeException("Bad loadable mode");
            }

            replyViewStateManager.replyViewStateChanged(loadable.isCatalogMode(), open);
        }

        this.replyOpen = open;

        reply.measure(
                MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );

        int height = reply.getMeasuredHeight();

        final ViewPropertyAnimator viewPropertyAnimator = reply.animate();
        viewPropertyAnimator.setListener(null);
        viewPropertyAnimator.setInterpolator(new DecelerateInterpolator(2f));
        viewPropertyAnimator.setDuration(600);

        boolean isSplitMode = ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT;

        if (open) {
            reply.setVisibility(VISIBLE);
            reply.setTranslationY(height);
            viewPropertyAnimator.translationY(0f);

            if (!isSplitMode) {
                threadListLayoutCallback.hideBottomNavBar(true, true);
            }
        } else {
            if (!isSplitMode) {
                threadListLayoutCallback.showBottomNavBar(true, true);
            }

            reply.setTranslationY(0f);
            viewPropertyAnimator.translationY(height);
            viewPropertyAnimator.setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    viewPropertyAnimator.setListener(null);
                    reply.setVisibility(GONE);
                }
            });
        }

        reply.onOpen(open);
        setRecyclerViewPadding();

        if (!open) {
            hideKeyboard(reply);
        }
        threadListLayoutCallback.replyLayoutOpen(open);

        attachToolbarScroll(!(open || searchOpen));
    }

    public ReplyPresenter getReplyPresenter() {
        return reply.getPresenter();
    }

    public void showError(String error) {
        postAdapter.showError(error);
    }

    public void openSearch(boolean open) {
        if (showingThread == null || searchOpen == open) {
            return;
        }

        searchOpen = open;

        searchStatus.measure(
                MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );

        int height = searchStatus.getMeasuredHeight();

        final ViewPropertyAnimator viewPropertyAnimator = searchStatus.animate();
        viewPropertyAnimator.setListener(null);
        viewPropertyAnimator.setInterpolator(new DecelerateInterpolator(2f));
        viewPropertyAnimator.setDuration(600);

        if (open) {
            searchStatus.setVisibility(VISIBLE);
            searchStatus.setTranslationY(-height);
            viewPropertyAnimator.translationY(0f);
        } else {
            searchStatus.setTranslationY(0f);
            viewPropertyAnimator.translationY(-height);
            viewPropertyAnimator.setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    viewPropertyAnimator.setListener(null);
                    searchStatus.setVisibility(GONE);
                }
            });
        }

        setRecyclerViewPadding();
        if (open) {
            searchStatus.setText(R.string.search_empty);
        } else {
            threadListLayoutCallback.getToolbar().closeSearchPhoneMode();
        }

        attachToolbarScroll(!(open || replyOpen));
    }

    @SuppressLint("StringFormatMatches")
    //android studio doesn't like the nested getQuantityString and messes up, but nothing is wrong
    public void setSearchStatus(String query, boolean setEmptyText, boolean hideKeyboard) {
        if (hideKeyboard) {
            hideKeyboard(this);
        }

        if (setEmptyText) {
            searchStatus.setText(R.string.search_empty);
        }

        if (query != null) {
            int size = getDisplayingPosts().size();
            searchStatus.setText(getString(R.string.search_results,
                    getQuantityString(R.plurals.posts, size, size),
                    query
            ));
        }
    }

    public boolean canChildScrollUp() {
        if (replyOpen) return true;

        if (getTopAdapterPosition() == 0) {
            View top = layoutManager.findViewByPosition(0);
            if (top == null) return true;

            if (searchOpen) {
                int searchExtraHeight = findViewById(R.id.search_status).getHeight();
                if (postViewMode == LIST) {
                    return top.getTop() != searchExtraHeight;
                } else {
                    if (top instanceof PostStubCell) {
                        // PostStubCell does not have grid_card_margin
                        return top.getTop() != searchExtraHeight + dp(1);
                    } else {
                        return top.getTop() != getDimen(R.dimen.grid_card_margin) + dp(1) + searchExtraHeight;
                    }
                }
            }

            switch (postViewMode) {
                case LIST:
                    return top.getTop() != toolbarHeight();
                case CARD:
                    if (top instanceof PostStubCell) {
                        // PostStubCell does not have grid_card_margin
                        return top.getTop() != toolbarHeight() + dp(1);
                    } else {
                        return top.getTop() != getDimen(R.dimen.grid_card_margin) + dp(1) + toolbarHeight();
                    }
            }
        }
        return true;
    }

    public boolean scrolledToBottom() {
        return getCompleteBottomAdapterPosition() == postAdapter.getItemCount() - 1;
    }

    public void smoothScrollNewPosts(int displayPosition) {
        if (layoutManager instanceof LinearLayoutManager) {
            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(
                    displayPosition + 1,
                    //position + 1 for last seen view, dp(4) for it's height
                    recyclerView.getHeight() - recyclerView.getPaddingTop() - dp(4)
            );
        } else {
            Logger.wtf(TAG, "Layout manager is grid inside thread??");
        }
    }

    public void cleanup() {
        postAdapter.cleanup();
        reply.cleanup();
        openReply(false);

        if (showingThread != null && showingThread.getLoadable().isThreadMode()) {
            openSearch(false);
        }

        showingThread = null;
        lastPostCount = 0;
        noParty();
    }

    public List<Post> getDisplayingPosts() {
        return postAdapter.getDisplayList();
    }

    public ThumbnailView getThumbnail(PostImage postImage) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager == null) {
            return null;
        }

        for (int i = 0; i < layoutManager.getChildCount(); i++) {
            View view = layoutManager.getChildAt(i);
            if (view instanceof PostCellInterface) {
                PostCellInterface postView = (PostCellInterface) view;
                Post post = postView.getPost();

                if (post != null) {
                    for (PostImage image : post.getPostImages()) {
                        if (image.equalUrl(postImage)) {
                            return postView.getThumbnailView(postImage);
                        }
                    }
                }
            }
        }

        return null;
    }

    public void scrollTo(int displayPosition, boolean smooth) {
        if (displayPosition < 0) {
            int bottom = postAdapter.getItemCount() - 1;
            int difference = Math.abs(bottom - getTopAdapterPosition());
            if (difference > MAX_SMOOTH_SCROLL_DISTANCE) {
                smooth = false;
            }

            if (smooth) {
                recyclerView.smoothScrollToPosition(bottom);
            } else {
                recyclerView.scrollToPosition(bottom);
                // No animation means no animation, wait for the layout to finish and skip all animations
                final RecyclerView.ItemAnimator itemAnimator = recyclerView.getItemAnimator();
                waitForLayout(recyclerView, view -> {
                    itemAnimator.endAnimations();
                    return true;
                });
            }
        } else {
            int scrollPosition = postAdapter.getScrollPosition(displayPosition);

            int difference = Math.abs(scrollPosition - getTopAdapterPosition());
            if (difference > MAX_SMOOTH_SCROLL_DISTANCE) {
                smooth = false;
            }

            if (smooth) {
                recyclerView.smoothScrollToPosition(scrollPosition);
            } else {
                recyclerView.scrollToPosition(scrollPosition);
                // No animation means no animation, wait for the layout to finish and skip all animations
                final RecyclerView.ItemAnimator itemAnimator = recyclerView.getItemAnimator();
                waitForLayout(recyclerView, view -> {
                    itemAnimator.endAnimations();
                    return true;
                });
            }
        }
    }

    public void highlightPost(Post post) {
        postAdapter.highlightPost(post);
    }

    public void highlightPostId(String id) {
        postAdapter.highlightPostId(id);
    }

    public void highlightPostTripcode(CharSequence tripcode) {
        postAdapter.highlightPostTripcode(tripcode);
    }

    public void selectPost(int post) {
        postAdapter.selectPost(post);
    }

    @Override
    public void highlightPostNo(int no) {
        postAdapter.highlightPostNo(no);
    }

    @Override
    public void showThread(Loadable loadable) {
        callback.showThread(loadable);
    }

    @Override
    public void requestNewPostLoad() {
        callback.requestNewPostLoad();
    }

    @Nullable
    @Override
    public ChanThread getThread() {
        return showingThread;
    }

    @Override
    public void showImageReencodingWindow(boolean supportsReencode) {
        threadListLayoutCallback.showImageReencodingWindow(supportsReencode);
    }

    public int[] getIndexAndTop() {
        int index = 0;
        int top = 0;
        if (recyclerView.getLayoutManager().getChildCount() > 0) {
            View topChild = recyclerView.getLayoutManager().getChildAt(0);

            index = ((RecyclerView.LayoutParams) topChild.getLayoutParams()).getViewLayoutPosition();

            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) topChild.getLayoutParams();
            top = layoutManager.getDecoratedTop(topChild) - params.topMargin - recyclerView.getPaddingTop();
        }

        return new int[]{index, top};
    }

    private boolean shouldToolbarCollapse() {
        return ChanSettings.getCurrentLayoutMode() != ChanSettings.LayoutMode.SPLIT
                && !ChanSettings.neverHideToolbar.get();
    }

    private void attachToolbarScroll(boolean attach) {
        if (shouldToolbarCollapse()) {
            Toolbar toolbar = threadListLayoutCallback.getToolbar();
            if (attach) {
                toolbar.attachRecyclerViewScrollStateListener(recyclerView);
            } else {
                toolbar.detachRecyclerViewScrollStateListener(recyclerView);
                toolbar.collapseShow(true);
            }
        }
    }

    private void showToolbarIfNeeded() {
        if (shouldToolbarCollapse()) {
            // Of coming back to focus from a dual controller, like the threadlistcontroller,
            // check if we should show the toolbar again (after the other controller made it hide).
            // It should show if the search or reply is open, or if the thread was scrolled at the
            // top showing an empty space.

            Toolbar toolbar = threadListLayoutCallback.getToolbar();
            if (searchOpen || replyOpen) {
                // force toolbar to show
                toolbar.collapseShow(true);
            } else {
                // check if it should show if it was scrolled at the top
                toolbar.checkToolbarCollapseState(recyclerView);
            }
        }
    }

    private void setFastScroll(boolean enabled) {
        if (!enabled) {
            if (fastScroller != null) {
                recyclerView.removeItemDecoration(fastScroller);
                fastScroller = null;
            }
        } else {
            if (postInfoMapItemDecoration == null) {
                postInfoMapItemDecoration = new PostInfoMapItemDecoration(getContext());
            }

            ChanThread thread = getThread();
            if (thread != null) {
                postInfoMapItemDecoration.setItems(
                        extractPostMapInfoHolderUseCase.execute(thread.getPosts()),
                        thread.getPostsCount()
                );

                if (fastScroller == null) {
                    fastScroller = FastScrollerHelper.create(
                            recyclerView,
                            postInfoMapItemDecoration,
                            themeHelper.getTheme()
                    );
                }
            }
        }

        recyclerView.setVerticalScrollBarEnabled(!enabled);
    }

    private void setRecyclerViewPadding() {
        int defaultPadding = postViewMode == CARD ? dp(1) : 0;
        int recyclerTop = defaultPadding + toolbarHeight();
        int recyclerBottom = defaultPadding;
        // reply view padding calculations (before measure)
        reply.setPadding(0, 0, 0, 0);

        // measurements
        if (replyOpen) {
            reply.measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
        }
        if (searchOpen) {
            searchStatus.measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
        }

        // recycler view padding calculations
        if (replyOpen) {
            recyclerBottom += reply.getMeasuredHeight();
        }
        if (searchOpen) {
            // search status has built-in padding for the toolbar height
            recyclerTop += searchStatus.getMeasuredHeight();
            recyclerTop -= toolbarHeight();
        }
        recyclerView.setPadding(defaultPadding, recyclerTop, defaultPadding, recyclerBottom);

        // reply view padding calculations (after measure)
        reply.setPadding(0, 0, 0, 0);
    }

    @Override
    public void updatePadding() {
        setRecyclerViewPadding();
    }

    public int toolbarHeight() {
        Toolbar toolbar = threadListLayoutCallback.getToolbar();
        return toolbar.getToolbarHeight();
    }

    private int getTopAdapterPosition() {
        switch (postViewMode) {
            case LIST:
                return ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
            case CARD:
                return ((GridLayoutManager) layoutManager).findFirstVisibleItemPosition();
        }
        return -1;
    }

    private int getCompleteBottomAdapterPosition() {
        switch (postViewMode) {
            case LIST:
                return ((LinearLayoutManager) layoutManager).findLastCompletelyVisibleItemPosition();
            case CARD:
                return ((GridLayoutManager) layoutManager).findLastCompletelyVisibleItemPosition();
        }
        return -1;
    }

    private Bitmap hat;

    private final RecyclerView.ItemDecoration PARTY = new RecyclerView.ItemDecoration() {
        @Override
        public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
            if (hat == null) {
                hat = BitmapFactory.decodeResource(getResources(), R.drawable.partyhat);
            }

            for (int i = 0, j = parent.getChildCount(); i < j; i++) {
                View child = parent.getChildAt(i);
                if (child instanceof PostCellInterface) {
                    PostCellInterface postView = (PostCellInterface) child;
                    Post post = postView.getPost();

                    if (post == null || !post.isOP || post.getPostImages().isEmpty()) {
                        continue;
                    }

                    RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();
                    int top = child.getTop() + params.topMargin;
                    int left = child.getLeft() + params.leftMargin;
                    c.drawBitmap(hat,
                            left - parent.getPaddingLeft() - dp(25),
                            top - dp(80) - parent.getPaddingTop() + toolbarHeight(),
                            null
                    );
                }
            }
        }
    };

    private void party() {
        if (showingThread.getLoadable().site instanceof Chan4) {
            Calendar calendar = Calendar.getInstance();
            if (calendar.get(Calendar.MONTH) == Calendar.OCTOBER && calendar.get(Calendar.DAY_OF_MONTH) == 1) {
                recyclerView.addItemDecoration(PARTY);
            }
        }
    }

    private void noParty() {
        recyclerView.removeItemDecoration(PARTY);
    }

    public void onImageOptionsApplied(Reply modifiedReply, boolean filenameRemoved) {
        reply.onImageOptionsApplied(modifiedReply, filenameRemoved);
    }

    public void onImageOptionsComplete() {
        reply.onImageOptionsComplete();
    }

    public void onPostUpdated(Post post) {
        BackgroundUtils.ensureMainThread();

        if (postAdapter != null) {
            postAdapter.updatePost(post);
        }
    }
    public interface ThreadListLayoutPresenterCallback {
        void showThread(Loadable loadable);
        void requestNewPostLoad();
        void onListScrolledToBottom();
    }

    public interface ThreadListLayoutCallback {
        void hideBottomNavBar(Boolean lockTranslation, Boolean lockCollapse);
        void showBottomNavBar(Boolean unlockTranslation, Boolean unlockCollapse);
        void replyLayoutOpen(boolean open);
        Toolbar getToolbar();
        void showImageReencodingWindow(boolean supportsReencode);
        boolean threadBackPressed();
        @Nullable Loadable getLoadable();
    }
}
