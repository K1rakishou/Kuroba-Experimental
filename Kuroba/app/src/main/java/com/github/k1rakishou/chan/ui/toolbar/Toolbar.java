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
package com.github.k1rakishou.chan.ui.toolbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener;
import com.github.k1rakishou.chan.ui.theme.ArrowMenuDrawable;
import com.github.k1rakishou.chan.ui.theme.ChanTheme;
import com.github.k1rakishou.chan.ui.theme.ThemeEngine;
import com.github.k1rakishou.chan.utils.AndroidUtils;
import com.github.k1rakishou.common.KotlinExtensionsKt;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.k1rakishou.chan.Chan.inject;
import static com.github.k1rakishou.chan.utils.AndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getDimen;
import static com.github.k1rakishou.chan.utils.AndroidUtils.hideKeyboard;

public class Toolbar
        extends LinearLayout
        implements View.OnClickListener,
        ToolbarPresenter.Callback,
        ToolbarContainer.Callback,
        WindowInsetsListener {
    private final static String TAG = "Toolbar";

    public static final int TOOLBAR_COLLAPSE_HIDE = 1000000;
    public static final int TOOLBAR_COLLAPSE_SHOW = -1000000;
    private static final int MIN_SCROLL_SLOP = dp(64);

    private static final Interpolator SLOWDOWN_INTERPOLATOR = new DecelerateInterpolator(2f);

    @Inject
    ThemeEngine themeEngine;
    @Inject
    GlobalWindowInsetsManager globalWindowInsetsManager;

    private boolean isInImmersiveMode = false;

    private int prevScrollState = RecyclerView.SCROLL_STATE_IDLE;
    private int pixelsToConsumeBeforeShowingToolbar = MIN_SCROLL_SLOP;

    private final RecyclerView.OnScrollListener recyclerViewOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            if (isAtTheTopOfThread(recyclerView)) {
                setCollapse(TOOLBAR_COLLAPSE_SHOW, false);
                return;
            }

            int currentState = recyclerView.getScrollState();

            // Consume the scroll events without showing the toolbar if we have some unconsumed
            // pixels left.
            if (dy < 0 &&
                    prevScrollState == RecyclerView.SCROLL_STATE_IDLE
                    && currentState != RecyclerView.SCROLL_STATE_IDLE) {
                if (pixelsToConsumeBeforeShowingToolbar > 0) {
                    pixelsToConsumeBeforeShowingToolbar -= Math.abs(dy);
                } else {
                    prevScrollState = currentState;
                }

                return;
            }

            // Show the UI
            processScrollCollapse(dy, false);
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (recyclerView.getLayoutManager() != null && newState == RecyclerView.SCROLL_STATE_IDLE) {
                processRecyclerViewScroll(recyclerView);
            }

            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                prevScrollState = RecyclerView.SCROLL_STATE_IDLE;
                pixelsToConsumeBeforeShowingToolbar = MIN_SCROLL_SLOP;
            }
        }
    };

    @Nullable
    private ToolbarCallback callback;

    private ToolbarPresenter presenter;
    private ImageView arrowMenuView;
    private ArrowMenuDrawable arrowMenuDrawable;
    private ToolbarContainer navigationItemContainer;
    private int lastScrollDeltaOffset;
    private int scrollOffset;
    private List<ToolbarCollapseCallback> collapseCallbacks = new ArrayList<>();
    private List<ToolbarHeightUpdatesCallback> heightUpdatesCallbacks = new ArrayList<>();

    public Toolbar(Context context) {
        this(context, null);
    }

    public Toolbar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Toolbar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setOrientation(HORIZONTAL);

        if (isInEditMode()) {
            return;
        }

        inject(this);
        presenter = new ToolbarPresenter(this, themeEngine);

        //initView
        FrameLayout leftButtonContainer = new FrameLayout(getContext());
        addView(leftButtonContainer, WRAP_CONTENT, MATCH_PARENT);

        arrowMenuView = new ImageView(getContext());
        arrowMenuView.setOnClickListener(this);
        arrowMenuView.setFocusable(true);
        arrowMenuView.setScaleType(ImageView.ScaleType.CENTER);
        arrowMenuDrawable = new ArrowMenuDrawable();
        arrowMenuView.setImageDrawable(arrowMenuDrawable);

        AndroidUtils.setBoundlessRoundRippleBackground(arrowMenuView);
        int toolbarSize = getDimen(R.dimen.toolbar_height);

        FrameLayout.LayoutParams leftButtonContainerLp = new FrameLayout.LayoutParams(
                toolbarSize,
                MATCH_PARENT,
                Gravity.CENTER_VERTICAL
        );
        leftButtonContainer.addView(arrowMenuView, leftButtonContainerLp);

        navigationItemContainer = new ToolbarContainer(getContext());
        addView(navigationItemContainer, new LayoutParams(0, MATCH_PARENT, 1f));

        navigationItemContainer.setCallback(this);
        navigationItemContainer.setArrowMenu(arrowMenuDrawable);

        setElevation(dp(4f));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        presenter.onAttached();

        updateToolbarTopPaddingAndHeight();
        globalWindowInsetsManager.addInsetsUpdatesListener(this);
    }

    @Override
    public void onInsetsChanged() {
        if (isInImmersiveMode) {
            return;
        }

        boolean heightChanged = updateToolbarTopPaddingAndHeight();

        for (ToolbarHeightUpdatesCallback heightUpdatesCallback : heightUpdatesCallbacks) {
            heightUpdatesCallback.onToolbarHeightKnown(heightChanged);
        }
    }

    private boolean updateToolbarTopPaddingAndHeight() {
        int toolbarSize = getDimen(R.dimen.toolbar_height);
        int newHeight = toolbarSize + globalWindowInsetsManager.top();
        int oldHeight = getLayoutParams().height;

        if (oldHeight == newHeight) {
            return false;
        }

        getLayoutParams().height = newHeight;

        KotlinExtensionsKt.updatePaddings(
                this,
                null,
                null,
                globalWindowInsetsManager.top(),
                null
        );

        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        presenter.onDetached();
        globalWindowInsetsManager.removeInsetsUpdatesListener(this);
    }

    public void setInImmersiveMode(boolean inImmersiveMode) {
        this.isInImmersiveMode = inImmersiveMode;
    }

    public void updateToolbarMenuStartPadding(int newPadding) {
        AndroidUtils.updatePaddings(
                this,
                newPadding,
                -1,
                -1,
                -1
        );
    }

    public void updateToolbarMenuEndPadding(int newPadding) {
        AndroidUtils.updatePaddings(
                this,
                -1,
                newPadding,
                -1,
                -1
        );
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return isTransitioning() || super.dispatchTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    public int getToolbarHeight() {
        return getLayoutParams().height;
    }

    public void addToolbarHeightUpdatesCallback(ToolbarHeightUpdatesCallback callback) {
        heightUpdatesCallbacks.add(callback);
    }

    public void removeToolbarHeightUpdatesCallback(ToolbarHeightUpdatesCallback callback) {
        heightUpdatesCallbacks.remove(callback);
    }

    public void addCollapseCallback(ToolbarCollapseCallback callback) {
        collapseCallbacks.add(callback);
    }

    public void removeCollapseCallback(ToolbarCollapseCallback callback) {
        collapseCallbacks.remove(callback);
    }

    public void processScrollCollapse(int offset, boolean animated) {
        lastScrollDeltaOffset = offset;
        setCollapse(offset, animated);
    }

    public void collapseShow(boolean animated) {
        setCollapse(Toolbar.TOOLBAR_COLLAPSE_SHOW, animated);
    }

    public void setCollapse(int offset, boolean animated) {
        scrollOffset += offset;
        scrollOffset = Math.max(0, Math.min(getHeight(), scrollOffset));

        if (animated) {
            animate().translationY(-scrollOffset)
                    .setDuration(300)
                    .setInterpolator(SLOWDOWN_INTERPOLATOR)
                    .start();

            boolean collapse = scrollOffset > 0;
            for (ToolbarCollapseCallback c : collapseCallbacks) {
                c.onCollapseAnimation(collapse);
            }

            return;
        }

        animate().cancel();
        setTranslationY(-scrollOffset);

        for (ToolbarCollapseCallback c : collapseCallbacks) {
            float newScrollOffset = 0f;
            int height = getHeight();

            if (height > 0) {
                newScrollOffset = scrollOffset / (float) height;
            }

            c.onCollapseTranslation(newScrollOffset);
        }
    }

    public void attachRecyclerViewScrollStateListener(RecyclerView recyclerView) {
        recyclerView.addOnScrollListener(recyclerViewOnScrollListener);
    }

    public void detachRecyclerViewScrollStateListener(RecyclerView recyclerView) {
        recyclerView.removeOnScrollListener(recyclerViewOnScrollListener);
    }

    public void checkToolbarCollapseState(RecyclerView recyclerView) {
        processRecyclerViewScroll(recyclerView);
    }

    private void processRecyclerViewScroll(RecyclerView recyclerView) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager == null) {
            return;
        }

        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (adapter == null) {
            return;
        }

        View positionZero = layoutManager.findViewByPosition(0);
        View lastPosition = layoutManager.findViewByPosition(adapter.getItemCount() - 1);

        boolean allowHide = lastPosition == null && (positionZero == null || positionZero.getTop() < 0);
        if (allowHide || lastScrollDeltaOffset <= 0) {
            setCollapse(lastScrollDeltaOffset <= 0 ? TOOLBAR_COLLAPSE_SHOW : TOOLBAR_COLLAPSE_HIDE, true);
        } else {
            setCollapse(TOOLBAR_COLLAPSE_SHOW, true);
        }
    }

    public void openSearchWithCallback(@Nullable ToolbarContainer.ToolbarTransitionAnimationListener listener) {
        presenter.openSearch(listener);
    }

    public void openSearchWithQuery(@Nullable String query) {
        presenter.openSearch(query, null);
    }

    public boolean closeSearch() {
        return presenter.closeSearch();
    }

    public void closeSearchPhoneMode() {
        presenter.closeSearchPhoneMode();
    }

    public boolean isTransitioning() {
        return navigationItemContainer.isTransitioning();
    }

    public void setNavigationItem(
            final boolean animate,
            final boolean pushing,
            final NavigationItem item,
            ChanTheme theme
    )  {
        setNavigationItem(animate, pushing, item, theme, null);
    }

    public void setNavigationItem(
            final boolean animate,
            final boolean pushing,
            final NavigationItem item,
            ChanTheme theme,
            ToolbarContainer.ToolbarTransitionAnimationListener listener
    ) {
        ToolbarPresenter.AnimationStyle animationStyle;
        if (!animate) {
            animationStyle = ToolbarPresenter.AnimationStyle.NONE;
        } else if (pushing) {
            animationStyle = ToolbarPresenter.AnimationStyle.PUSH;
        } else {
            animationStyle = ToolbarPresenter.AnimationStyle.POP;
        }

        presenter.set(item, theme, animationStyle, listener);
    }

    public void beginTransition(NavigationItem newItem) {
        presenter.startTransition(newItem);
    }

    public void transitionProgress(float progress) {
        presenter.setTransitionProgress(progress);
    }

    public void finishTransition(boolean completed) {
        presenter.stopTransition(completed);
    }

    public void setCallback(ToolbarCallback callback) {
        this.callback = callback;
    }

    public void removeCallback() {
        this.callback = null;
    }

    @Override
    public void onClick(View v) {
        if (v == arrowMenuView) {
            if (callback != null) {
                callback.onMenuOrBackClicked(arrowMenuDrawable.getProgress() == 1f);
            }
        }
    }

    public ArrowMenuDrawable getArrowMenuDrawable() {
        return arrowMenuDrawable;
    }

    public void updateTitle(NavigationItem navigationItem) {
        presenter.update(navigationItem);
    }

    @Override
    public void showForNavigationItem(NavigationItem item, ChanTheme theme, ToolbarPresenter.AnimationStyle animation) {
        showForNavigationItem(item, theme, animation, null);
    }

    @Override
    public void showForNavigationItem(
            NavigationItem item,
            ChanTheme theme,
            ToolbarPresenter.AnimationStyle animation,
            ToolbarContainer.ToolbarTransitionAnimationListener listener
    ) {
        navigationItemContainer.set(item, theme, animation, listener);
    }

    @Override
    public void containerStartTransition(NavigationItem item, ToolbarPresenter.TransitionAnimationStyle animation) {
        navigationItemContainer.startTransition(item, animation);
    }

    @Override
    public void containerStopTransition(boolean didComplete) {
        navigationItemContainer.stopTransition(didComplete);
    }

    @Override
    public void containerSetTransitionProgress(float progress) {
        navigationItemContainer.setTransitionProgress(progress);
    }

    @Override
    public void searchInput(String input) {
        presenter.searchInput(input);
    }

    @Override
    public void onSearchVisibilityChanged(NavigationItem item, boolean visible) {
        if (callback != null) {
            callback.onSearchVisibilityChanged(item, visible);
        }

        if (!visible) {
            hideKeyboard(navigationItemContainer);
        }
    }

    @Override
    public void onSearchInput(NavigationItem item, String input) {
        if (callback != null) {
            callback.onSearchEntered(item, input);
        }
    }

    @Override
    public void updateViewForItem(NavigationItem item) {
        navigationItemContainer.update(item);
    }

    private boolean isAtTheTopOfThread(RecyclerView recyclerView) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager == null) {
            return false;
        }

        int firstVisibleElement = -1;

        if (layoutManager instanceof GridLayoutManager) {
            firstVisibleElement = ((GridLayoutManager) layoutManager).findFirstCompletelyVisibleItemPosition();
        } else if (layoutManager instanceof LinearLayoutManager) {
            firstVisibleElement = ((LinearLayoutManager) layoutManager).findFirstCompletelyVisibleItemPosition();
        } else {
            throw new IllegalStateException("Not implemented for " + layoutManager.getClass().getName());
        }

        return firstVisibleElement == 0;
    }

    public interface ToolbarCallback {
        void onMenuOrBackClicked(boolean isArrow);

        void onSearchVisibilityChanged(NavigationItem item, boolean visible);

        void onSearchEntered(NavigationItem item, String entered);
    }

    public interface ToolbarCollapseCallback {
        void onCollapseTranslation(float offset);

        void onCollapseAnimation(boolean collapse);
    }

    public interface ToolbarHeightUpdatesCallback {
        void onToolbarHeightKnown(boolean heightChanged);
    }
}
