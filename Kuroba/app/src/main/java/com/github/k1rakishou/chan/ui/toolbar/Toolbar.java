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

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen;
import static com.github.k1rakishou.common.AndroidUtils.hideKeyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener;
import com.github.k1rakishou.chan.ui.cell.CardPostCell;
import com.github.k1rakishou.chan.ui.theme.ArrowMenuDrawable;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.common.AndroidUtils;
import com.github.k1rakishou.common.KotlinExtensionsKt;
import com.github.k1rakishou.core_themes.ChanTheme;
import com.github.k1rakishou.core_themes.ThemeEngine;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import kotlin.collections.ArraysKt;

public class Toolbar
        extends LinearLayout
        implements View.OnClickListener,
        ToolbarPresenter.Callback,
        ToolbarContainer.Callback,
        WindowInsetsListener,
        ThemeEngine.ThemeChangesListener {
    private final static String TAG = "Toolbar";

    public static final int TOOLBAR_COLLAPSE_HIDE = 1000000;
    public static final int TOOLBAR_COLLAPSE_SHOW = -1000000;

    public static final Interpolator TOOLBAR_ANIMATION_INTERPOLATOR = new FastOutSlowInInterpolator();
    public static final long TOOLBAR_ANIMATION_DURATION_MS = 175L;

    @Inject
    ThemeEngine themeEngine;
    @Inject
    GlobalWindowInsetsManager globalWindowInsetsManager;

    private boolean isInImmersiveMode = false;

    private final RecyclerView.OnScrollListener recyclerViewOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            if (isAtTheTopOfThread(recyclerView)) {
                setCollapse(TOOLBAR_COLLAPSE_SHOW, false);
            } else {
                processScrollCollapse(dy, false);
            }
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (recyclerView.getLayoutManager() != null && newState == RecyclerView.SCROLL_STATE_IDLE) {
                checkToolbarCollapseState(recyclerView, false);
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
    private int scrollOffsetCounter;
    private boolean ignoreThemeChanges = false;
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

        AppModuleAndroidUtils.extractActivityComponent(context)
                .inject(this);

        presenter = new ToolbarPresenter(this, themeEngine);

        //initView
        FrameLayout leftButtonContainer = new FrameLayout(getContext());
        leftButtonContainer.setId(R.id.toolbar_arrow_menu_view_container);
        addView(leftButtonContainer, WRAP_CONTENT, MATCH_PARENT);

        arrowMenuView = new ImageView(getContext());
        arrowMenuView.setId(R.id.toolbar_arrow_menu_view);
        arrowMenuView.setOnClickListener(this);
        arrowMenuView.setFocusable(true);
        arrowMenuView.setScaleType(ImageView.ScaleType.CENTER);
        arrowMenuDrawable = new ArrowMenuDrawable(context);
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
        onThemeChanged();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        presenter.onAttached();

        updateToolbarTopPaddingAndHeight();
        globalWindowInsetsManager.addInsetsUpdatesListener(this);
        themeEngine.addListener(this);
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

    @Override
    public void onThemeChanged() {
        if (ignoreThemeChanges) {
            return;
        }

        setBackgroundColor(themeEngine.getChanTheme().getPrimaryColor());
        arrowMenuDrawable.onThemeChanged();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        themeEngine.removeListener(this);
        presenter.onDetached();
        globalWindowInsetsManager.removeInsetsUpdatesListener(this);
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

    public void setCustomBackgroundColor(int color) {
        setBackgroundColor(color);
    }

    public void setIgnoreThemeChanges() {
        ignoreThemeChanges = true;
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

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isFullyVisible()) {
            // Steal event from children
            return true;
        }

        return super.onInterceptTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isFullyVisible()) {
            // Pass event to other views
            return false;
        }

        return super.onTouchEvent(event);
    }

    private boolean isFullyVisible() {
        return getAlpha() >= 0.99f;
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

    public void collapseHide(boolean animated) {
        setCollapse(Toolbar.TOOLBAR_COLLAPSE_HIDE, animated);
    }

    public boolean isToolbarShown() {
        return scrollOffsetCounter == 0 || scrollOffsetCounter < (getToolbarHeight() / 2);
    }

    public void setCollapse(int offset, boolean animated) {
        int toolbarHeight = getToolbarHeight();

        scrollOffsetCounter += offset;
        scrollOffsetCounter = Math.max(0, Math.min(toolbarHeight, scrollOffsetCounter));

        float newScrollOffset = 0f;
        float newAlpha = 0f;

        if (toolbarHeight > 0) {
            newScrollOffset = scrollOffsetCounter / (float) toolbarHeight;
            newAlpha = 1f - newScrollOffset;
        }

        if (animated) {
            animate()
                    .alpha(newAlpha)
                    .setDuration(TOOLBAR_ANIMATION_DURATION_MS)
                    .setInterpolator(TOOLBAR_ANIMATION_INTERPOLATOR)
                    .start();

            boolean collapse = scrollOffsetCounter > 0;
            for (ToolbarCollapseCallback callback : collapseCallbacks) {
                callback.onCollapseAnimation(collapse);
            }

            return;
        }

        animate().cancel();
        setAlpha(newAlpha);

        for (ToolbarCollapseCallback callback : collapseCallbacks) {
            callback.onCollapseTranslation(newScrollOffset);
        }
    }

    public void attachRecyclerViewScrollStateListener(RecyclerView recyclerView) {
        recyclerView.addOnScrollListener(recyclerViewOnScrollListener);
    }

    public void detachRecyclerViewScrollStateListener(RecyclerView recyclerView) {
        recyclerView.removeOnScrollListener(recyclerViewOnScrollListener);
    }

    public void checkToolbarCollapseState(RecyclerView recyclerView, boolean onGainedFocus) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager == null) {
            return;
        }

        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (adapter == null) {
            return;
        }

        boolean isAtVeryBottom = getCompletelyVisibleViewPosition(recyclerView, false) == (adapter.getItemCount() - 1);
        boolean isAtVeryTop = getCompletelyVisibleViewPosition(recyclerView, true) == 0;

        if (onGainedFocus) {
            // If Toolbar is already shown we need to notify all the listeners about that otherwise
            // some views that depend of Toolbar's hidden/shown state will have incorrect state.
            boolean shouldShowToolbar = isAtVeryBottom || isAtVeryTop || isToolbarShown();
            if (shouldShowToolbar) {
                setCollapse(TOOLBAR_COLLAPSE_SHOW, true);
            }

            // Do not hide the toolbar when sliding between catalog/thread controller
        } else {
            boolean shouldShowToolbar = isAtVeryBottom || isAtVeryTop || lastScrollDeltaOffset <= 0;
            if (shouldShowToolbar) {
                setCollapse(TOOLBAR_COLLAPSE_SHOW, true);
            } else {
                setCollapse(TOOLBAR_COLLAPSE_HIDE, true);
            }
        }
    }

    private int getCompletelyVisibleViewPosition(RecyclerView recyclerView, boolean first) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager == null) {
            return -1;
        }

        if (layoutManager instanceof StaggeredGridLayoutManager) {
            StaggeredGridLayoutManager staggeredGridLayoutManager = (StaggeredGridLayoutManager) layoutManager;
            int itemsCount = staggeredGridLayoutManager.getItemCount() - 1;
            int spanCount = staggeredGridLayoutManager.getSpanCount();

            int[] positions;
            if (first) {
                positions = staggeredGridLayoutManager.findFirstVisibleItemPositions(null);
            } else {
                positions = staggeredGridLayoutManager.findLastVisibleItemPositions(null);
            }

            if (positions.length == 0) {
                return -1;
            }

            for (int position : positions) {
                if (position < 0) {
                    continue;
                }

                View view = staggeredGridLayoutManager.findViewByPosition(position);
                if (view == null) {
                    continue;
                }

                if (first) {
                    int recyclerTop = recyclerView.getPaddingTop();
                    if (getViewTop(view) == recyclerTop && position <= spanCount) {
                        return position;
                    }
                } else {
                    int recyclerBottom = recyclerView.getBottom() - recyclerView.getPaddingBottom();
                    if (getViewBottom(view) == recyclerBottom && position >= (itemsCount - spanCount)) {
                        return position;
                    }
                }
            }

            return -1;
        }

        if (layoutManager instanceof GridLayoutManager) {
            GridLayoutManager gridLayoutManager = (GridLayoutManager) layoutManager;
            int itemsCount = gridLayoutManager.getItemCount() - 1;

            int position;
            if (first) {
                position = gridLayoutManager.findFirstVisibleItemPosition();
            } else {
                position = gridLayoutManager.findLastVisibleItemPosition();
            }

            View view = gridLayoutManager.findViewByPosition(position);
            if (view == null) {
                return -1;
            }

            if (first) {
                int recyclerTop = recyclerView.getPaddingTop();
                if (getViewTop(view) == recyclerTop && position == 0) {
                    return position;
                }
            } else {
                int recyclerBottom = recyclerView.getBottom() - recyclerView.getPaddingBottom();
                if (getViewBottom(view) == recyclerBottom && position == itemsCount) {
                    return position;
                }
            }

            return -1;
        }

        if (layoutManager instanceof LinearLayoutManager) {
            LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
            int itemsCount = linearLayoutManager.getItemCount() - 1;

            int position;
            if (first) {
                position = linearLayoutManager.findFirstVisibleItemPosition();
            } else {
                position = linearLayoutManager.findLastVisibleItemPosition();
            }

            View view = linearLayoutManager.findViewByPosition(position);
            if (view == null) {
                return -1;
            }

            if (first) {
                int recyclerTop = recyclerView.getPaddingTop();
                if (getViewTop(view) == recyclerTop && position == 0) {
                    return position;
                }
            } else {
                int recyclerBottom = recyclerView.getBottom() - recyclerView.getPaddingBottom();
                if (getViewBottom(view) == recyclerBottom && position == itemsCount) {
                    return position;
                }
            }

            return -1;
        }

        throw new IllegalStateException("Not supported LayoutManager: " + layoutManager.getClass().getSimpleName());
    }

    private int getViewTop(View view) {
        if (view instanceof CardPostCell) {
            return view.getTop() - getDimen(R.dimen.grid_card_margin);
        }

        return view.getTop();
    }

    private int getViewBottom(View view) {
        if (view instanceof CardPostCell) {
            return view.getBottom() + getDimen(R.dimen.grid_card_margin);
        }

        return view.getBottom();
    }

    public void openSearchWithQuery(@Nullable String query) {
        presenter.openSearch(query, null);
    }

    public boolean closeSearch() {
        return presenter.closeSearch();
    }

    public boolean isSearchOpened() {
        return presenter.isSearchOpened();
    }

    public void enterSelectionMode(String text) {
        presenter.enterSelectionMode(text);
    }

    public boolean isInSelectionMode() {
        return presenter.isInSelectionMode();
    }

    public void exitSelectionMode() {
        presenter.exitSelectionMode();
    }

    public boolean isTransitioning() {
        return navigationItemContainer.isTransitioning();
    }

    public void setNavigationItem(
            final boolean animate,
            final boolean pushing,
            final NavigationItem item,
            ChanTheme theme
    ) {
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

    public void hideArrowMenu() {
        View arrowMenuContainer = KotlinExtensionsKt.findChild(
                this,
                view -> view.getId() == R.id.toolbar_arrow_menu_view_container
        );

        if (arrowMenuContainer == null || arrowMenuContainer.getVisibility() == View.GONE) {
            return;
        }

        arrowMenuContainer.setVisibility(View.GONE);
        KotlinExtensionsKt.updatePaddings(navigationItemContainer, dp(8f), null, null, null);
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

    public void updateSelectionTitle(NavigationItem navigationItem) {
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
    public void searchInput(@NonNull String input) {
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
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            int[] array = ((StaggeredGridLayoutManager) layoutManager).findFirstCompletelyVisibleItemPositions(null);
            if (array.length > 0) {
                //noinspection ConstantConditions
                firstVisibleElement = ArraysKt.minOrNull(array);
            }
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
