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
package com.github.k1rakishou.chan.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.chan.utils.ViewUtils;
import com.github.k1rakishou.core_themes.ThemeEngine;

import javax.inject.Inject;

public class OptionalSwipeViewPager extends ViewPager {
    private boolean swipingEnabled;
    private int prevPosition = 0;
    private SwipeDirection swipeDirection = SwipeDirection.Default;

    @Inject
    ThemeEngine themeEngine;

    private ViewPager.OnPageChangeListener listener = new ViewPager.OnPageChangeListener() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            // no-op
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            // no-op
        }

        @Override
        public void onPageSelected(int position) {
            if (position > prevPosition) {
                swipeDirection = SwipeDirection.Forward;
            } else if (position < prevPosition) {
                swipeDirection = SwipeDirection.Backward;
            } else {
                swipeDirection = SwipeDirection.Default;
            }

            prevPosition = position;
        }
    };

    public OptionalSwipeViewPager(Context context) {
        super(context);
        init();
    }

    public OptionalSwipeViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        AppModuleAndroidUtils.extractActivityComponent(getContext())
                .inject(this);

        ViewUtils.changeEdgeEffect(this, themeEngine.chanTheme);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        addOnPageChangeListener(listener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        removeOnPageChangeListener(listener);
    }

    @Override
    public void setCurrentItem(int item) {
        super.setCurrentItem(item);
        prevPosition = item;
    }

    @Override
    public void setCurrentItem(int item, boolean smoothScroll) {
        super.setCurrentItem(item, smoothScroll);
        prevPosition = item;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return swipingEnabled && super.onTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        try {
            return swipingEnabled && super.onInterceptTouchEvent(ev);
        } catch (IllegalArgumentException ignored) {
            // Ignore pointer index out of range exceptions
            return false;
        }
    }

    public boolean swipeForward() {
        int itemCount = getItemCount();
        if (itemCount == 0 || prevPosition + 1 >= itemCount) {
            return false;
        }

        setCurrentItem(prevPosition + 1, true);
        return true;
    }

    public boolean swipeBackward() {
        int itemCount = getItemCount();
        if (itemCount == 0 || prevPosition - 1 < 0) {
            return false;
        }

        setCurrentItem(prevPosition - 1, true);
        return true;
    }

    private int getItemCount() {
        PagerAdapter adapter = getAdapter();
        if (adapter == null) {
            return 0;
        }

        return adapter.getCount();
    }

    public void setSwipingEnabled(boolean swipingEnabled) {
        this.swipingEnabled = swipingEnabled;
    }

    public SwipeDirection getSwipeDirection() {
        return swipeDirection;
    }

    public enum SwipeDirection {
        Default,
        Forward,
        Backward;

        public SwipeDirection withoutDefault() {
            if (this == Default) {
                return Forward;
            }

            return this;
        }
    }
}
