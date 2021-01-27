/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k1rakishou.chan.ui.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.view.MotionEvent;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ItemDecoration;
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener;
import androidx.recyclerview.widget.RecyclerView.OnScrollListener;

import com.github.k1rakishou.chan.Chan;
import com.github.k1rakishou.core_themes.ChanTheme;
import com.github.k1rakishou.core_themes.ThemeEngine;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Inject;

/**
 * Class responsible to animate and provide a fast scroller.
 * <p>
 * Clover changed: the original FastScroller didn't account for the recyclerview top padding we
 * require. A minimum thumb length parameter was also added.
 */
public class FastScroller extends ItemDecoration implements OnItemTouchListener, ThemeEngine.ThemeChangesListener {
    @IntDef({STATE_HIDDEN, STATE_VISIBLE, STATE_DRAGGING})
    @Retention(RetentionPolicy.SOURCE)
    private @interface State {}

    // Scroll thumb not showing
    private static final int STATE_HIDDEN = 0;
    // Scroll thumb visible and moving along with the scrollbar
    private static final int STATE_VISIBLE = 1;
    // Scroll thumb being dragged by user
    private static final int STATE_DRAGGING = 2;

    @IntDef({DRAG_X, DRAG_Y, DRAG_NONE})
    @Retention(RetentionPolicy.SOURCE)
    private @interface DragState {}

    private static final int DRAG_NONE = 0;
    private static final int DRAG_X = 1;
    private static final int DRAG_Y = 2;

    @IntDef({ANIMATION_STATE_OUT, ANIMATION_STATE_FADING_IN, ANIMATION_STATE_IN, ANIMATION_STATE_FADING_OUT})
    @Retention(RetentionPolicy.SOURCE)
    private @interface AnimationState {}

    private static final int ANIMATION_STATE_OUT = 0;
    private static final int ANIMATION_STATE_FADING_IN = 1;
    private static final int ANIMATION_STATE_IN = 2;
    private static final int ANIMATION_STATE_FADING_OUT = 3;

    private static final int SHOW_DURATION_MS = 300;
    private static final int HIDE_DELAY_AFTER_VISIBLE_MS = 1500;
    private static final int HIDE_DELAY_AFTER_DRAGGING_MS = 1200;
    private static final int HIDE_DURATION_MS = 300;
    private static final int SCROLLBAR_THUMB_ALPHA = 200;
    private static final int SCROLLBAR_TRACK_ALPHA_DRAGGING = 200;
    private static final int SCROLLBAR_TRACK_ALPHA_VISIBLE = 80;

    private static final int[] PRESSED_STATE_SET = {android.R.attr.state_pressed};
    private static final int[] HOVERED_STATE_SET = {android.R.attr.state_hovered};
    private static final int[] EMPTY_STATE_SET = {};

    @Nullable
    private ThumbDragListener thumbDragListener;

    @Nullable
    private final PostInfoMapItemDecoration postInfoMapItemDecoration;
    private final int mScrollbarMinimumRange;
    private final int mMargin;
    private final int mThumbMinLength;
    private final int defaultWidth;
    private final int toolbarPaddingTop;

    // Final values for the vertical scroll bar
    private StateListDrawable mVerticalThumbDrawable;
    private Drawable mVerticalTrackDrawable;
    private int mVerticalThumbWidth;
    private int mVerticalTrackWidth;

    // Dynamic values for the vertical scroll bar
    int verticalThumbHeight;
    int realVerticalThumbHeight;
    int mVerticalThumbCenterY;
    float mVerticalDragY;
    int mVerticalDragThumbHeight;

    private int mRecyclerViewWidth = 0;
    private int mRecyclerViewHeight = 0;

    private int mRecyclerViewLeftPadding = 0;
    private int mRecyclerViewTopPadding = 0;
    private int mRecyclerViewRightPadding = 0;
    private int mRecyclerViewBottomPadding = 0;

    private RecyclerView mRecyclerView;
    /**
     * Whether the document is long/wide enough to require scrolling. If not, we don't show the
     * relevant scroller.
     */
    private boolean mNeedVerticalScrollbar = false;
    @State
    private int mState = STATE_HIDDEN;
    @DragState
    private int mDragState = DRAG_NONE;

    private final int[] mVerticalRange = new int[2];
    private final ValueAnimator mShowHideAnimator = ValueAnimator.ofFloat(0, 1);
    @AnimationState
    private int mAnimationState = ANIMATION_STATE_OUT;
    private final Runnable mHideRunnable = () -> hide(HIDE_DURATION_MS);
    private final OnScrollListener mOnScrollListener = new OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            updateScrollPosition(recyclerView.computeVerticalScrollOffset());
        }
    };

    @Inject
    ThemeEngine themeEngine;

    public FastScroller(
            RecyclerView recyclerView,
            @Nullable
            PostInfoMapItemDecoration postInfoMapItemDecoration,
            int defaultWidth,
            int scrollbarMinimumRange,
            int margin,
            int thumbMinLength,
            int toolbarPaddingTop
    ) {
        Chan.getComponent().inject(this);

        this.mScrollbarMinimumRange = scrollbarMinimumRange;
        this.defaultWidth = defaultWidth;
        this.mMargin = margin;
        this.mThumbMinLength = thumbMinLength;
        this.postInfoMapItemDecoration = postInfoMapItemDecoration;
        this.toolbarPaddingTop = toolbarPaddingTop;

        onThemeChanged();

        mShowHideAnimator.addListener(new AnimatorListener());
        mShowHideAnimator.addUpdateListener(new AnimatorUpdater());

        attachToRecyclerView(recyclerView);
        themeEngine.addListener(this);
    }

    @Override
    public void onThemeChanged() {
        mVerticalThumbDrawable = getThumb(themeEngine.chanTheme);
        mVerticalTrackDrawable = getTrack(themeEngine.chanTheme);

        mVerticalThumbWidth = Math.max(defaultWidth, mVerticalThumbDrawable.getIntrinsicWidth());
        mVerticalTrackWidth = Math.max(defaultWidth, mVerticalTrackDrawable.getIntrinsicWidth());

        mVerticalThumbDrawable.setAlpha(SCROLLBAR_THUMB_ALPHA);
        mVerticalTrackDrawable.setAlpha(getTrackAlpha());

        requestRedraw();
    }

    private int getTrackAlpha() {
        if (mState == STATE_DRAGGING) {
            return SCROLLBAR_TRACK_ALPHA_DRAGGING;
        }

        return SCROLLBAR_TRACK_ALPHA_VISIBLE;
    }

    private static StateListDrawable getThumb(ChanTheme curTheme) {
        StateListDrawable list = new StateListDrawable();
        list.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(curTheme.getAccentColor()));
        list.addState(new int[]{}, new ColorDrawable(curTheme.getTextColorSecondary()));
        return list;
    }

    private static StateListDrawable getTrack(ChanTheme curTheme) {
        StateListDrawable list = new StateListDrawable();
        list.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(curTheme.getTextColorHint()));
        list.addState(new int[]{android.R.attr.state_hovered}, new ColorDrawable(curTheme.getTextColorHint()));
        list.addState(new int[]{}, new ColorDrawable(0));
        return list;
    }

    public void setThumbDragListener(@Nullable ThumbDragListener listener) {
        this.thumbDragListener = listener;
    }

    public void onCleanup() {
        themeEngine.removeListener(this);
        thumbDragListener = null;
    }

    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) {
        if (mRecyclerView == recyclerView) {
            return; // nothing to do
        }
        if (mRecyclerView != null) {
            destroyCallbacks();
        }
        mRecyclerView = recyclerView;
        if (mRecyclerView != null) {
            setupCallbacks();
        }
    }

    private void setupCallbacks() {
        mRecyclerView.addItemDecoration(this);
        mRecyclerView.addOnItemTouchListener(this);
        mRecyclerView.addOnScrollListener(mOnScrollListener);
    }

    public void destroyCallbacks() {
        mRecyclerView.removeItemDecoration(this);
        mRecyclerView.removeOnItemTouchListener(this);
        mRecyclerView.removeOnScrollListener(mOnScrollListener);
        cancelHide();

        if (postInfoMapItemDecoration != null) {
            postInfoMapItemDecoration.cancelAnimation();
        }
    }

    private void requestRedraw() {
        if (mRecyclerView != null) {
            mRecyclerView.invalidate();
        }
    }

    private void setState(@State int state) {
        if (state == STATE_DRAGGING && mState != STATE_DRAGGING) {
            mVerticalThumbDrawable.setState(PRESSED_STATE_SET);
            mVerticalTrackDrawable.setState(PRESSED_STATE_SET);
            cancelHide();
        }

        if (state == STATE_HIDDEN) {
            requestRedraw();
        } else {
            show();
        }

        if (mState == STATE_DRAGGING && state == STATE_VISIBLE) {
            mVerticalThumbDrawable.setState(EMPTY_STATE_SET);
            mVerticalTrackDrawable.setState(HOVERED_STATE_SET);
            resetHideDelay(HIDE_DELAY_AFTER_DRAGGING_MS);
        } else if (mState == STATE_DRAGGING && state == STATE_HIDDEN) {
            mVerticalThumbDrawable.setState(EMPTY_STATE_SET);
            mVerticalTrackDrawable.setState(EMPTY_STATE_SET);
            resetHideDelay(HIDE_DELAY_AFTER_DRAGGING_MS);
        } else if (state == STATE_VISIBLE) {
            mVerticalTrackDrawable.setState(HOVERED_STATE_SET);
            resetHideDelay(HIDE_DELAY_AFTER_VISIBLE_MS);
        }

        mState = state;
    }

    private boolean isLayoutRTL() {
        return ViewCompat.getLayoutDirection(mRecyclerView) == ViewCompat.LAYOUT_DIRECTION_RTL;
    }

    public boolean isDragging() {
        return mState == STATE_DRAGGING;
    }

    @VisibleForTesting
    boolean isVisible() {
        return mState == STATE_VISIBLE;
    }

    @VisibleForTesting
    boolean isHidden() {
        return mState == STATE_HIDDEN;
    }

    public void show() {
        switch (mAnimationState) {
            case ANIMATION_STATE_FADING_OUT:
                mShowHideAnimator.cancel();

                if (postInfoMapItemDecoration != null) {
                    postInfoMapItemDecoration.cancelAnimation();
                }
                // fall through
            case ANIMATION_STATE_OUT:
                mAnimationState = ANIMATION_STATE_FADING_IN;
                mShowHideAnimator.setFloatValues((float) mShowHideAnimator.getAnimatedValue(), 1);
                mShowHideAnimator.setDuration(SHOW_DURATION_MS);
                mShowHideAnimator.setStartDelay(0);
                mShowHideAnimator.start();

                if (postInfoMapItemDecoration != null) {
                    postInfoMapItemDecoration.show();
                }
                break;
            case ANIMATION_STATE_FADING_IN:
            case ANIMATION_STATE_IN:
                break;
        }
    }

    public void hide() {
        hide(0);
    }

    @VisibleForTesting
    void hide(int duration) {
        switch (mAnimationState) {
            case ANIMATION_STATE_FADING_IN:
                mShowHideAnimator.cancel();

                if (postInfoMapItemDecoration != null) {
                    postInfoMapItemDecoration.cancelAnimation();
                }
                // fall through
            case ANIMATION_STATE_IN:
                mAnimationState = ANIMATION_STATE_FADING_OUT;
                mShowHideAnimator.setFloatValues((float) mShowHideAnimator.getAnimatedValue(), 0);
                mShowHideAnimator.setDuration(duration);
                mShowHideAnimator.start();

                if (postInfoMapItemDecoration != null) {
                    postInfoMapItemDecoration.hide(duration);
                }

                break;
            case ANIMATION_STATE_FADING_OUT:
            case ANIMATION_STATE_OUT:
                break;
        }
    }

    private void cancelHide() {
        mRecyclerView.removeCallbacks(mHideRunnable);
    }

    private void resetHideDelay(int delay) {
        cancelHide();
        mRecyclerView.postDelayed(mHideRunnable, delay);
    }

    @Override
    public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (mRecyclerViewWidth != getRecyclerViewWidth() || mRecyclerViewHeight != getRecyclerViewHeight()) {
            mRecyclerViewWidth = getRecyclerViewWidth();
            mRecyclerViewHeight = getRecyclerViewHeight();
            mRecyclerViewLeftPadding = getRecyclerViewLeftPadding();
            mRecyclerViewTopPadding = getRecyclerViewTopPadding();
            mRecyclerViewRightPadding = getRecyclerViewRightPadding();
            mRecyclerViewBottomPadding = getRecyclerViewBottomPadding();

            // This is due to the different events ordering when keyboard is opened or
            // retracted vs rotate. Hence to avoid corner cases we just disable the
            // scroller when size changed, and wait until the scroll position is recomputed
            // before showing it back.
            setState(STATE_HIDDEN);
            return;
        }

        if (postInfoMapItemDecoration != null) {
            int recyclerHeight = mRecyclerView.getHeight();
            int recyclerWidth = getRecyclerViewWidth();

            // Draw under scrollbar
            postInfoMapItemDecoration.onDrawOver(
                    canvas,
                    mRecyclerView,
                    mRecyclerView.getPaddingTop(),
                    mRecyclerView.getPaddingBottom(),
                    recyclerHeight,
                    recyclerWidth
            );
        }

        if (mAnimationState != ANIMATION_STATE_OUT) {
            if (mNeedVerticalScrollbar) {
                drawVerticalScrollbar(canvas);
            }
        }
    }

    private int getRecyclerViewWidth() {
        return mNeedVerticalScrollbar
                ? mRecyclerView.getWidth()
                : mRecyclerView.getWidth() - mRecyclerView.getPaddingLeft() - mRecyclerView.getPaddingRight();
    }

    private int getRecyclerViewHeight() {
        return mRecyclerView.getHeight() - getRecyclerViewTopPadding() - getRecyclerViewBottomPadding();
    }

    private int getRecyclerViewLeftPadding() {
        return mNeedVerticalScrollbar ? 0 : mRecyclerView.getPaddingLeft();
    }

    private int getRecyclerViewTopPadding() {
        return mRecyclerView.getPaddingTop() + (toolbarPaddingTop / 2);
    }

    private int getRecyclerViewRightPadding() {
        return mNeedVerticalScrollbar ? 0 : mRecyclerView.getPaddingRight();
    }

    private int getRecyclerViewBottomPadding() {
        return mRecyclerView.getPaddingBottom() + (toolbarPaddingTop / 2);
    }

    private void drawVerticalScrollbar(Canvas canvas) {
        int viewWidth = mRecyclerViewWidth;

        int left = mRecyclerViewLeftPadding + viewWidth - mVerticalThumbWidth;
        int top = mVerticalThumbCenterY - verticalThumbHeight / 2;
        mVerticalThumbDrawable.setBounds(0, 0, mVerticalThumbWidth, verticalThumbHeight);

        int trackLength = mRecyclerViewHeight + mRecyclerViewTopPadding + mRecyclerViewBottomPadding;
        mVerticalTrackDrawable.setBounds(0, 0, mVerticalTrackWidth, trackLength);

        if (isLayoutRTL()) {
            mVerticalTrackDrawable.draw(canvas);
            canvas.translate(mVerticalThumbWidth, top);
            canvas.scale(-1, 1);
            mVerticalThumbDrawable.draw(canvas);
            canvas.scale(1, 1);
            canvas.translate(-mVerticalThumbWidth, -top);
        } else {
            canvas.translate(left, 0);
            mVerticalTrackDrawable.draw(canvas);
            canvas.translate(0, top);
            mVerticalThumbDrawable.draw(canvas);
            canvas.translate(-left, -top);
        }
    }

    /**
     * Notify the scroller of external change of the scroll, e.g. through dragging or flinging on
     * the view itself.
     *
     * @param offsetY The new scroll Y offset.
     */
    void updateScrollPosition(int offsetY) {
        int verticalContentLength = mRecyclerView.computeVerticalScrollRange();
        int verticalVisibleLength = mRecyclerViewHeight;

        mNeedVerticalScrollbar = verticalContentLength - verticalVisibleLength > 0
                && mRecyclerViewHeight >= mScrollbarMinimumRange;

        if (mNeedVerticalScrollbar) {
            float middleScreenPos = offsetY + verticalVisibleLength / 2.0f;
            mVerticalThumbCenterY =
                    mRecyclerViewTopPadding + (int) ((verticalVisibleLength * middleScreenPos) / verticalContentLength);

            int length = Math.min(
                    verticalVisibleLength,
                    (verticalVisibleLength * verticalVisibleLength) / verticalContentLength
            );

            verticalThumbHeight = Math.max(mThumbMinLength, length);
            realVerticalThumbHeight = length;
        }

        if (mState == STATE_HIDDEN || mState == STATE_VISIBLE) {
            setState(STATE_VISIBLE);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent ev) {
        final boolean handled;
        if (mState == STATE_VISIBLE) {
            boolean insideVerticalThumb = isPointInsideVerticalThumb(ev.getX(), ev.getY());
            if (ev.getAction() == MotionEvent.ACTION_DOWN && insideVerticalThumb) {
                mDragState = DRAG_Y;
                mVerticalDragY = (int) ev.getY();
                mVerticalDragThumbHeight = verticalThumbHeight;

                setState(STATE_DRAGGING);
                handled = true;
            } else {
                handled = false;
            }
        } else {
            handled = mState == STATE_DRAGGING;
        }
        return handled;
    }

    @Override
    public void onTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent me) {
        if (mState == STATE_HIDDEN) {
            return;
        }

        if (me.getAction() == MotionEvent.ACTION_DOWN) {
            boolean insideVerticalThumb = isPointInsideVerticalThumb(me.getX(), me.getY());
            if (insideVerticalThumb) {
                mDragState = DRAG_Y;
                mVerticalDragY = (int) me.getY();
                mVerticalDragThumbHeight = verticalThumbHeight;

                setState(STATE_DRAGGING);
                requestRedraw();

                if (thumbDragListener != null) {
                    thumbDragListener.onDragStarted();
                }

                verticalScrollTo(me.getY());
            }
        } else if (me.getAction() == MotionEvent.ACTION_UP && mState == STATE_DRAGGING) {
            mVerticalDragY = 0;
            setState(STATE_VISIBLE);
            mDragState = DRAG_NONE;
            requestRedraw();

            if (thumbDragListener != null) {
                thumbDragListener.onDragEnded();
            }
        } else if (me.getAction() == MotionEvent.ACTION_MOVE && mState == STATE_DRAGGING) {
            show();
            if (mDragState == DRAG_Y) {
                verticalScrollTo(me.getY());
            }
        }
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
    }

    private void verticalScrollTo(float y) {
        final int[] scrollbarRange = getVerticalRange();
        float touchFraction = calculateTouchFraction(y, scrollbarRange);

        int scrollPosition = (int) ((mRecyclerView.getAdapter().getItemCount() - 1) * touchFraction);
        if (scrollPosition < 0) {
            scrollPosition = 0;
        }

        RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();

        if (layoutManager instanceof GridLayoutManager) {
            ((GridLayoutManager) layoutManager).scrollToPositionWithOffset(scrollPosition, 0);
        } else if (layoutManager instanceof LinearLayoutManager) {
            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(scrollPosition, 0);
        }
    }

    private float calculateTouchFraction(float y, int[] scrollbarRange) {
        if (scrollbarRange[0] > scrollbarRange[1]) {
            int tmp = scrollbarRange[1];
            scrollbarRange[1] = scrollbarRange[0];
            scrollbarRange[0] = tmp;
        }

        if (y < scrollbarRange[0]) {
            return 0f;
        }

        if (y > scrollbarRange[1]) {
            return 1f;
        }

        float scrollbarHeight = scrollbarRange[1] - scrollbarRange[0];
        float convertedY = y - ((float) scrollbarRange[0]);

        return convertedY / scrollbarHeight;
    }

    @VisibleForTesting
    boolean isPointInsideVerticalThumb(float x, float y) {
        return isLayoutRTL()
                ? x <= mRecyclerViewLeftPadding + mVerticalThumbWidth / 2.0f
                : x >= mRecyclerViewLeftPadding + mRecyclerViewWidth - mVerticalThumbWidth;
    }

    /**
     * Gets the (min, max) vertical positions of the vertical scroll bar.
     */
    private int[] getVerticalRange() {
        mVerticalRange[0] = mRecyclerViewTopPadding + mMargin;
        mVerticalRange[1] = mRecyclerViewTopPadding + mRecyclerViewHeight - mMargin;
        return mVerticalRange;
    }

    private class AnimatorListener
            extends AnimatorListenerAdapter {

        private boolean mCanceled = false;

        @Override
        public void onAnimationEnd(Animator animation) {
            // Cancel is always followed by a new directive, so don't update state.
            if (mCanceled) {
                mCanceled = false;
                return;
            }
            if ((float) mShowHideAnimator.getAnimatedValue() == 0) {
                mAnimationState = ANIMATION_STATE_OUT;
                setState(STATE_HIDDEN);
            } else {
                mAnimationState = ANIMATION_STATE_IN;
                requestRedraw();
            }
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mCanceled = true;
        }
    }

    private class AnimatorUpdater implements AnimatorUpdateListener {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            int thumbAlpha = (int) (SCROLLBAR_THUMB_ALPHA * ((float) valueAnimator.getAnimatedValue()));
            int trackAlpha = (int) (getTrackAlpha() * ((float) valueAnimator.getAnimatedValue()));

            mVerticalThumbDrawable.setAlpha(thumbAlpha);
            mVerticalTrackDrawable.setAlpha(trackAlpha);
            requestRedraw();
        }
    }

    public interface ThumbDragListener {
        void onDragStarted();
        void onDragEnded();
    }
}
