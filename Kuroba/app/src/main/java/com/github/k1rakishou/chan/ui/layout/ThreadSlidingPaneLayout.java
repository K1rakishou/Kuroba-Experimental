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
package com.github.k1rakishou.chan.ui.layout;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.waitForLayout;

import android.content.Context;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.ViewGroup;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController;
import com.github.k1rakishou.chan.ui.widget.SlidingPaneLayoutEx;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.core_themes.IColorizableWidget;
import com.github.k1rakishou.core_themes.ThemeEngine;

import javax.inject.Inject;

public class ThreadSlidingPaneLayout extends SlidingPaneLayoutEx implements IColorizableWidget {
    private static final int SLIDE_PANE_OVERHANG_SIZE = dp(20);

    @Inject
    ThemeEngine themeEngine;

    public ViewGroup leftPane;
    public ViewGroup rightPane;

    private ThreadSlideController threadSlideController;

    public ThreadSlidingPaneLayout(Context context) {
        this(context, null);
        init();
    }

    public ThreadSlidingPaneLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        init();
    }

    public ThreadSlidingPaneLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        if (!isInEditMode()) {
            AppModuleAndroidUtils.extractActivityComponent(getContext())
                    .inject(this);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        leftPane = findViewById(R.id.left_pane);
        rightPane = findViewById(R.id.right_pane);

        setOverhangSize(currentOverhangSize());
    }

    private int currentOverhangSize() {
        if (ChanSettings.isSlideLayoutMode()) {
            return SLIDE_PANE_OVERHANG_SIZE;
        }

        return 0;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Forces a relayout after it has already been layed out, because SlidingPaneLayout sucks and otherwise
        // gives the children too much room until they request a relayout.
        waitForLayout(this, view -> {
            requestLayout();
            return false;
        });

        applyColors();
    }

    public void setThreadSlideController(ThreadSlideController threadSlideController) {
        this.threadSlideController = threadSlideController;
    }

    @Override
    public void applyColors() {
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
        if (threadSlideController != null) {
            threadSlideController.onSlidingPaneLayoutStateRestored();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);

        ViewGroup.LayoutParams leftParams = leftPane.getLayoutParams();
        ViewGroup.LayoutParams rightParams = rightPane.getLayoutParams();

        leftParams.width = width - currentOverhangSize();
        rightParams.width = width;

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
