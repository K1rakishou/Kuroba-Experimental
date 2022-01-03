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

import static android.text.TextUtils.isEmpty;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;
import static com.github.k1rakishou.common.AndroidUtils.removeFromParentView;
import static com.github.k1rakishou.common.AndroidUtils.updatePaddings;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.base.Debouncer;
import com.github.k1rakishou.chan.ui.layout.SearchLayout;
import com.github.k1rakishou.chan.ui.theme.ArrowMenuDrawable;
import com.github.k1rakishou.chan.ui.theme.DropdownArrowDrawable;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.common.KotlinExtensionsKt;
import com.github.k1rakishou.core_themes.ChanTheme;
import com.github.k1rakishou.core_themes.ThemeEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

/**
 * The container for the views created by the toolbar for the navigation items.
 * <p>
 * It will strictly only transition between two views. If a new view is set
 * and a transition is in progress, it is stopped before adding the new view.
 * <p>
 * For normal animations the previousView is the view that is animated away from, and the
 * currentView is the view where is animated to. The previousView is removed and cleared if the
 * animation finished.
 * <p>
 * Transitions are user-controlled animations that can be canceled of finished. For that the
 * currentView describes the view that was originally there, and the transitionView is the view
 * what is possibly transitioned to.
 * <p>
 * This is also the class that is responsible for the orientation and animation of the arrow-menu
 * drawable.
 */
public class ToolbarContainer extends FrameLayout {
    private static final String TAG = "ToolbarContainer";
    private static final int ANIMATION_DURATION = 250;

    @Inject
    ThemeEngine themeEngine;

    private Callback callback;
    private ArrowMenuDrawable arrowMenu;

    @Nullable
    private ItemView previousView;
    @Nullable
    private ItemView currentView;
    @Nullable
    private ItemView transitionView;
    @Nullable
    private ToolbarPresenter.TransitionAnimationStyle transitionAnimationStyle;

    private Debouncer searchDebouncer = new Debouncer(false);
    private Map<View, Animator> animatorSet = new HashMap<>();

    public ToolbarContainer(Context context) {
        this(context, null);
    }

    public ToolbarContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ToolbarContainer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        AppModuleAndroidUtils.extractActivityComponent(getContext())
                .inject(this);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setArrowMenu(ArrowMenuDrawable arrowMenu) {
        this.arrowMenu = arrowMenu;
    }

    public void set(
            NavigationItem item,
            ChanTheme theme,
            ToolbarPresenter.AnimationStyle animation
    ) {
        set(item, theme, animation, null);
    }

    public void set(
            NavigationItem item,
            ChanTheme theme,
            ToolbarPresenter.AnimationStyle animation,
            @Nullable ToolbarTransitionAnimationListener listener
    ) {
        if (transitionView != null) {
            // Happens when you are in Phone layout, moving the thread fragment and at the same time
            // thread update occurs. We probably should just skip it. But maybe there is a better
            // solution.
            if (listener != null) {
                listener.onAnimationEnded();
            }

            return;
        }

        endAnimations();
        ItemView itemView = new ItemView(item, theme);

        previousView = currentView;
        currentView = itemView;

        addView(itemView.view, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        if (getChildCount() > 2) {
            throw new IllegalArgumentException("More than 2 child views attached");
        }

        // Can't run the animation if there is no previous view
        // Otherwise just show it without an animation.
        if (animation != ToolbarPresenter.AnimationStyle.NONE && previousView != null) {
            setAnimation(itemView, previousView, animation, listener);

            setArrowProgress(1f, !previousView.item.hasArrow());
            itemView.attach();
            return;
        }

        if (previousView != null) {
            removeItem(previousView);
            previousView = null;
        }

        setArrowProgress(1f, !currentView.item.hasArrow());
        itemView.attach();

        if (listener != null) {
            listener.onAnimationEnded();
        }
    }

    public void update(NavigationItem item) {
        View view = viewForItem(item);
        if (view == null) {
            return;
        }

        if (item.selectionMode) {
            TextView textView = view.findViewById(R.id.toolbar_selection_text_view);
            if (textView != null) {
                textView.setText(item.selectionStateText);
            }

            return;
        }

        TextView titleView = view.findViewById(R.id.title);
        if (titleView != null) {
            if (isEmpty(item.title)) {
                titleView.setText("", TextView.BufferType.SPANNABLE);
            } else {
                titleView.setText(item.title, TextView.BufferType.SPANNABLE);
            }
        }

        TextView subtitleView = view.findViewById(R.id.subtitle);
        if (subtitleView != null) {
            if (isEmpty(item.subtitle)) {
                subtitleView.setText("", TextView.BufferType.SPANNABLE);
            } else {
                subtitleView.setText(item.subtitle, TextView.BufferType.SPANNABLE);
            }
        }
    }

    public View viewForItem(NavigationItem item) {
        ItemView itemView = itemViewForItem(item);
        return itemView == null ? null : itemView.view;
    }

    private ItemView itemViewForItem(NavigationItem item) {
        if (currentView != null && item == currentView.item) {
            return currentView;
        } else if (previousView != null && item == previousView.item) {
            return previousView;
        } else if (transitionView != null && item == transitionView.item) {
            return transitionView;
        } else {
            return null;
        }
    }

    public boolean isTransitioning() {
        return transitionView != null || previousView != null;
    }

    public void startTransition(NavigationItem item, ToolbarPresenter.TransitionAnimationStyle style) {
        if (transitionView != null) {
            throw new IllegalStateException("Already in transition mode");
        }

        endAnimations();

        ItemView itemView = new ItemView(item, null);

        transitionView = itemView;
        transitionAnimationStyle = style;
        addView(itemView.view, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        if (getChildCount() > 2) {
            throw new IllegalArgumentException("More than 2 child views attached");
        }

        itemView.attach();
    }

    public void stopTransition(boolean didComplete) {
        if (transitionView == null) {
            throw new IllegalStateException("Not in transition mode");
        }

        endAnimations();

        if (didComplete) {
            removeItem(currentView);
            currentView = transitionView;
        } else {
            removeItem(transitionView);
        }

        transitionView = null;

        if (getChildCount() != 1) {
            throw new IllegalStateException("Not 1 view attached");
        }
    }

    public void setTransitionProgress(float progress) {
        if (transitionView == null) {
            throw new IllegalStateException("Not in transition mode");
        }

        transitionProgressAnimation(progress, transitionAnimationStyle);
    }

    private void endAnimations() {
        if (previousView != null) {
            endAnimation(previousView.view);
            if (previousView != null) {
                throw new IllegalStateException("Animation end did not remove view");
            }
        }

        if (currentView != null) {
            endAnimation(currentView.view);
        }
    }

    private void endAnimation(View view) {
        Animator a = animatorSet.remove(view);
        if (a != null) {
            a.end();
        }
    }

    private void setAnimation(
            ItemView newView,
            ItemView previousView,
            ToolbarPresenter.AnimationStyle animationStyle,
            @Nullable ToolbarTransitionAnimationListener listener
    ) {
        if (animationStyle == ToolbarPresenter.AnimationStyle.PUSH
                || animationStyle == ToolbarPresenter.AnimationStyle.POP) {
            setPushPopAnimation(newView, previousView, animationStyle, listener);
            return;
        }

        if (animationStyle == ToolbarPresenter.AnimationStyle.FADE) {
            setFadeAnimation(newView, previousView, listener);
            return;
        }

        throw new RuntimeException("Not implemented for animationStyle: " + animationStyle);
    }

    private void setFadeAnimation(
            ItemView newView,
            ItemView previousView,
            @Nullable ToolbarTransitionAnimationListener listener
    ) {
        AtomicInteger animationsCount = new AtomicInteger(3);
        AtomicBoolean listenerCalled = new AtomicBoolean(false);

        // Previous animation
        ValueAnimator previousAnimation = ObjectAnimator.ofFloat(previousView.view, View.ALPHA, 1f, 0f);
        previousAnimation.setDuration(ANIMATION_DURATION);
        previousAnimation.setInterpolator(new LinearInterpolator());
        previousAnimation.addListener(new AnimatorListenerAdapter() {
            private void onAnimationEndInternal(boolean end) {
                animatorSet.remove(previousView.view);
                removeItem(previousView);
                ToolbarContainer.this.previousView = null;

                if (animationsCount.decrementAndGet() <= 0
                        && listenerCalled.compareAndSet(false, true)) {
                    if (listener != null) {
                        listener.onAnimationEnded();
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEndInternal(false);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                onAnimationEndInternal(true);
            }
        });
        animatorSet.put(previousView.view, previousAnimation);

        post(previousAnimation::start);

        // Current animation + arrow
        newView.view.setAlpha(0f);
        ValueAnimator newAnimation = ObjectAnimator.ofFloat(newView.view, View.ALPHA, 0f, 1f);
        newAnimation.setDuration(ANIMATION_DURATION);
        newAnimation.setInterpolator(new LinearInterpolator());
        newAnimation.addListener(new AnimatorListenerAdapter() {
            private void onAnimationEndInternal(boolean end) {
                animatorSet.remove(newView.view);

                if (animationsCount.decrementAndGet() <= 0
                        && listenerCalled.compareAndSet(false, true)) {
                    if (listener != null) {
                        listener.onAnimationEnded();
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEndInternal(false);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                onAnimationEndInternal(true);
            }
        });

        // A different animator for the arrow because that one needs the deceleration
        // interpolator.
        ValueAnimator arrowAnimation = ValueAnimator.ofFloat(0f, 1f);
        arrowAnimation.setDuration(ANIMATION_DURATION);
        arrowAnimation.setInterpolator(new DecelerateInterpolator(2f));
        arrowAnimation.addUpdateListener(a -> {
            float value = (float) a.getAnimatedValue();
            if (previousView.item.hasArrow() != currentView.item.hasArrow()) {
                setArrowProgress(value, !currentView.item.hasArrow());
            }
        });

        AnimatorSet animationAndArrow = new AnimatorSet();
        animationAndArrow.playTogether(newAnimation, arrowAnimation);
        animationAndArrow.addListener(new AnimatorListenerAdapter() {
            private void onAnimationEndInternal(boolean end) {
                animatorSet.remove(newView.view);

                if (animationsCount.decrementAndGet() <= 0
                        && listenerCalled.compareAndSet(false, true)) {
                    if (listener != null) {
                        listener.onAnimationEnded();
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEndInternal(false);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                onAnimationEndInternal(true);
            }
        });

        animatorSet.put(newView.view, animationAndArrow);

        post(animationAndArrow::start);
    }

    private void setPushPopAnimation(
            ItemView newView,
            ItemView previousView,
            ToolbarPresenter.AnimationStyle animationStyle,
            @Nullable ToolbarTransitionAnimationListener listener
    ) {
        final boolean pushing = animationStyle == ToolbarPresenter.AnimationStyle.PUSH;
        AtomicInteger animationsCount = new AtomicInteger(2);
        AtomicBoolean listenerCalled = new AtomicBoolean(false);

        // Previous animation
        ValueAnimator previousAnimation = getShortAnimator();
        previousAnimation.addUpdateListener(a -> {
            float value = (float) a.getAnimatedValue();
            setPreviousAnimationProgress(previousView.view, pushing, value);
        });
        previousAnimation.addListener(new AnimatorListenerAdapter() {
            private void onAnimationEndInternal(boolean end) {
                animatorSet.remove(previousView.view);
                removeItem(previousView);
                ToolbarContainer.this.previousView = null;

                if (animationsCount.decrementAndGet() <= 0
                        && listenerCalled.compareAndSet(false, true)) {
                    if (listener != null) {
                        listener.onAnimationEnded();
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEndInternal(false);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                onAnimationEndInternal(true);
            }
        });

        if (!pushing) {
            previousAnimation.setStartDelay(100);
        }

        animatorSet.put(previousView.view, previousAnimation);
        post(previousAnimation::start);

        // Current animation + arrow
        newView.view.setAlpha(0f);
        ValueAnimator newAnimation = getShortAnimator();
        newAnimation.addUpdateListener(a -> {
            float value = (float) a.getAnimatedValue();
            setAnimationProgress(newView.view, pushing, value);

            if (previousView.item.hasArrow() != currentView.item.hasArrow()) {
                setArrowProgress(value, !currentView.item.hasArrow());
            }
        });
        newAnimation.addListener(new AnimatorListenerAdapter() {
            private void onAnimationEndInternal(boolean end) {
                animatorSet.remove(newView.view);

                if (animationsCount.decrementAndGet() <= 0
                        && listenerCalled.compareAndSet(false, true)) {
                    if (listener != null) {
                        listener.onAnimationEnded();
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEndInternal(false);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                onAnimationEndInternal(true);
            }
        });

        if (!pushing) {
            newAnimation.setStartDelay(100);
        }

        animatorSet.put(newView.view, newAnimation);
        post(newAnimation::start);
    }

    private void setPreviousAnimationProgress(View view, boolean pushing, float progress) {
        final int offset = dp(16);
        view.setTranslationY((pushing ? -offset : offset) * progress);
        view.setAlpha(1f - progress);
    }

    private void setAnimationProgress(View view, boolean pushing, float progress) {
        final int offset = dp(16);
        view.setTranslationY((pushing ? offset : -offset) * (1f - progress));
        view.setAlpha(progress);
    }

    private void setArrowProgress(float progress, boolean reverse) {
        if (reverse) {
            progress = 1f - progress;
        }
        progress = Math.max(0f, Math.min(1f, progress));

        arrowMenu.setProgress(progress);
    }

    private void transitionProgressAnimation(float progress, ToolbarPresenter.TransitionAnimationStyle style) {
        progress = Math.max(0f, Math.min(1f, progress));

        final int offset = dp(16);

        boolean pushing = style == ToolbarPresenter.TransitionAnimationStyle.PUSH;

        transitionView.view.setTranslationY((pushing ? offset : -offset) * (1f - progress));
        transitionView.view.setAlpha(progress);

        currentView.view.setTranslationY((pushing ? -offset : offset) * progress);
        currentView.view.setAlpha(1f - progress);

        if (transitionView.item.hasArrow() != currentView.item.hasArrow()) {
            setArrowProgress(progress, !transitionView.item.hasArrow());
        }
    }

    private ValueAnimator getShortAnimator() {
        final ValueAnimator animator = ObjectAnimator.ofFloat(0f, 1f);
        animator.setDuration(ANIMATION_DURATION);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        return animator;
    }

    private void removeItem(ItemView item) {
        item.remove();
        removeView(item.view);
    }

    private class ItemView {
        final View view;
        final NavigationItem item;

        @Nullable
        private ToolbarMenuView menuView;

        public ItemView(NavigationItem item, ChanTheme theme) {
            this.view = createNavigationItemView(item, theme);
            this.item = item;
        }

        public void attach() {
            if (item.menu != null && menuView != null) {
                menuView.attach(item.menu);
            }
        }

        public void remove() {
            if (menuView != null) {
                menuView.detach();
            }
        }

        private LinearLayout createNavigationItemView(final NavigationItem item, ChanTheme theme) {
            if (item.search) {
                return createSearchLayout(item);
            } else if (item.selectionMode) {
                return createSelectionLayout(item);
            } else {
                return createNavigationLayout(item, theme);
            }
        }

        @NonNull
        private LinearLayout createNavigationLayout(NavigationItem item, ChanTheme theme) {
            @SuppressLint("InflateParams")
            LinearLayout menu = (LinearLayout) inflate(getContext(), R.layout.toolbar_menu, null);
            menu.setGravity(Gravity.CENTER_VERTICAL);

            FrameLayout titleContainer = menu.findViewById(R.id.title_container);

            // Title
            final TextView titleView = menu.findViewById(R.id.title);
            titleView.setTypeface((theme != null ? theme : themeEngine.getChanTheme()).getMainFont());
            titleView.setText(item.title);
            titleView.setTextColor(Color.WHITE);

            if (item.scrollableTitle) {
                titleView.setSingleLine(true);
                titleView.setMarqueeRepeatLimit(-1);
                titleView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                titleView.setSelected(true);
            }

            // Middle title with arrow and callback
            if (item.middleMenu != null) {
                final Drawable arrowDrawable = themeEngine.tintDrawable(
                        new DropdownArrowDrawable(dp(12), dp(12), true),
                        true
                );

                arrowDrawable.setBounds(0, 0, arrowDrawable.getIntrinsicWidth(), arrowDrawable.getIntrinsicHeight());
                ImageView dropdown = new ImageView(getContext());
                dropdown.setImageDrawable(arrowDrawable);
                KotlinExtensionsKt.updatePaddings(dropdown, null, dp(16), null, null);

                titleContainer.addView(
                        dropdown,
                        new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT)
                );
                titleContainer.setOnClickListener(v -> item.middleMenu.show(titleView));
            }

            // Possible subtitle.
            TextView subtitleView = menu.findViewById(R.id.subtitle);
            if (!isEmpty(item.subtitle)) {
                ViewGroup.LayoutParams titleParams = titleView.getLayoutParams();
                titleParams.height = WRAP_CONTENT;
                titleView.setLayoutParams(titleParams);
                subtitleView.setText(item.subtitle);
                subtitleView.setTextColor(Color.WHITE);
                updatePaddings(titleView, -1, -1, dp(5f), -1);
            } else {
                titleContainer.removeView(subtitleView);
            }

            // Possible view shown at the right side.
            if (item.rightView != null) {
                removeFromParentView(item.rightView);
                item.rightView.setPadding(0, 0, dp(16), 0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT);
                menu.addView(item.rightView, lp);
            }

            // Possible menu with items.
            if (item.menu != null) {
                menuView = new ToolbarMenuView(getContext());

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT);
                menu.addView(this.menuView, lp);
            }

            return menu;
        }

        @NonNull
        private LinearLayout createSearchLayout(NavigationItem item) {
            SearchLayout searchLayout = new SearchLayout(getContext());
            searchLayout.setCallback(true, input -> {
                searchDebouncer.post(() -> {
                    callback.searchInput(input);
                }, 250L);
            });

            if (item.searchText != null) {
                searchLayout.setTextIgnoringWatcher(item.searchText);
            }

            searchLayout.setCatalogSearchColors();
            updatePaddings(searchLayout, dp(16), -1, -1, -1);

            return searchLayout;
        }

        private LinearLayout createSelectionLayout(NavigationItem item) {
            LinearLayout linearLayout = new LinearLayout(getContext());
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            linearLayout.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

            TextView textView = new TextView(getContext());
            textView.setId(R.id.toolbar_selection_text_view);
            textView.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            textView.setGravity(Gravity.CENTER_VERTICAL);
            textView.setTextColor(Color.WHITE);
            textView.setTextSize(20f);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setSingleLine(true);
            textView.setText(item.selectionStateText);

            KotlinExtensionsKt.updatePaddings(textView, (int) dp(4f), null, null, null);

            linearLayout.addView(textView);
            return linearLayout;
        }

    }

    public interface ToolbarTransitionAnimationListener {
        void onAnimationEnded();
    }

    public interface Callback {
        void searchInput(@NonNull String input);
    }
}
