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
package com.github.adamantcheese.chan.ui.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.utils.KtExtensionsKt;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

public class HidingFloatingActionButton
        extends FloatingActionButton
        implements Toolbar.ToolbarCollapseCallback {
    private static final Interpolator SLOWDOWN = new DecelerateInterpolator(2f);

    private boolean attachedToWindow;
    private Toolbar toolbar;
    private boolean attachedToToolbar;
    private CoordinatorLayout coordinatorLayout;
    private float currentCollapseScale;
    private int bottomNavViewHeight;

    public HidingFloatingActionButton(Context context) {
        super(context);
        init();
    }

    public HidingFloatingActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HidingFloatingActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // We apply the bottom paddings directly in SplitNavigationController when we are in SPLIT
        // mode, so we don't need to do that twice and that's why we set bottomNavViewHeight to 0
        // when in SPLIT mode.
        if (ChanSettings.getCurrentLayoutMode() != ChanSettings.LayoutMode.SPLIT) {
            bottomNavViewHeight =
                    (int) getContext().getResources().getDimension(R.dimen.bottom_nav_view_height);
        } else {
            bottomNavViewHeight = 0;
        }
    }

    public void setToolbar(Toolbar toolbar) {
        this.toolbar = toolbar;

        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) getLayoutParams();

        KtExtensionsKt.updateMargins(
                this,
                null,
                null,
                null,
                null,
                null,
                layoutParams.bottomMargin + bottomNavViewHeight
        );

        if (attachedToWindow && !attachedToToolbar) {
            toolbar.addCollapseCallback(this);
            attachedToToolbar = true;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;

        if (!(getParent() instanceof CoordinatorLayout)) {
            throw new IllegalArgumentException("HidingFloatingActionButton " +
                    "must be a parent of CoordinatorLayout");
        }

        coordinatorLayout = (CoordinatorLayout) getParent();

        if (toolbar != null && !attachedToToolbar) {
            toolbar.addCollapseCallback(this);
            attachedToToolbar = true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;

        if (attachedToToolbar) {
            toolbar.removeCollapseCallback(this);
            attachedToToolbar = false;
        }

        coordinatorLayout = null;
    }

    @Override
    public void onCollapseTranslation(float offset) {
        if (isSnackbarShowing()) {
            return;
        }

        if (offset != currentCollapseScale) {
            currentCollapseScale = 1f - offset;

            if (offset < 1f) {
                if (getVisibility() != ViewGroup.VISIBLE) {
                    setVisibility(ViewGroup.VISIBLE);
                }
            } else {
                if (getVisibility() != ViewGroup.GONE) {
                    setVisibility(ViewGroup.GONE);
                }
            }

            setScaleX(currentCollapseScale);
            setScaleY(currentCollapseScale);
        }
    }

    @Override
    public void onCollapseAnimation(boolean collapse) {
        if (isSnackbarShowing()) {
            return;
        }

        float scale = collapse ? 0f : 1f;
        if (scale != currentCollapseScale) {
            currentCollapseScale = scale;

            animate()
                    .scaleX(currentCollapseScale)
                    .scaleY(currentCollapseScale)
                    .setDuration(300)
                    .setStartDelay(0)
                    .setInterpolator(SLOWDOWN)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);

                            if (collapse) {
                                setVisibility(ViewGroup.GONE);
                            } else {
                                setVisibility(ViewGroup.VISIBLE);
                            }
                        }
                    })
                    .start();
        }
    }

    private boolean isSnackbarShowing() {
        for (int i = 0; i < coordinatorLayout.getChildCount(); i++) {
            if (coordinatorLayout.getChildAt(i) instanceof Snackbar.SnackbarLayout) {
                currentCollapseScale = -1;
                return true;
            }
        }

        return false;
    }
}
