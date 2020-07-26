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
package com.github.adamantcheese.chan.controller.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;

import com.github.adamantcheese.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder;
import com.github.adamantcheese.chan.features.gesture_editor.ExclusionZone;
import com.github.adamantcheese.chan.ui.controller.BrowseController;
import com.github.adamantcheese.chan.ui.controller.navigation.NavigationController;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;

import kotlin.Unit;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isAndroid10;

public class NavigationControllerContainerLayout extends FrameLayout {
    @Nullable
    private ControllerTracker controllerTracker;

    @Inject
    Android10GesturesExclusionZonesHolder exclusionZonesHolder;

    public NavigationControllerContainerLayout(Context context) {
        super(context);
        preInit();
    }

    public NavigationControllerContainerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        preInit();
    }

    public NavigationControllerContainerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        preInit();
    }

    private void preInit() {
        inject(this);
    }

    public void initThreadControllerTracking(boolean swipeEnabled, NavigationController navigationController) {
        Objects.requireNonNull(navigationController);

        controllerTracker = new ThreadControllerTracker(
                swipeEnabled,
                this::getWidth,
                this::getHeight,
                () -> {
                    invalidate();
                    return Unit.INSTANCE;
                },
                (runnable) -> {
                    ViewCompat.postOnAnimation(this, runnable);
                    return Unit.INSTANCE;
                },
                navigationController,
                getContext()
        );
    }

    public void initBrowseControllerTracker(
            BrowseController browseController,
            NavigationController navigationController
    ) {
        Objects.requireNonNull(browseController);

        controllerTracker = new BrowseControllerTracker(
                getContext(),
                browseController,
                navigationController
        );
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (AndroidUtils.isAndroid10()) {
            // To trigger onLayout() which will call provideAndroid10GesturesExclusionZones()
            requestLayout();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (controllerTracker == null) {
            return false;
        }

        return controllerTracker.onInterceptTouchEvent(event);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept && controllerTracker != null) {
            controllerTracker.requestDisallowInterceptTouchEvent();
        }

        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (controllerTracker == null) {
            return false;
        }

        return controllerTracker.onTouchEvent(getParent(), event);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (controllerTracker == null) {
            return;
        }

        if (controllerTracker instanceof ThreadControllerTracker) {
            ((ThreadControllerTracker) controllerTracker).dispatchDraw(canvas);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // We should check that changed is true, otherwise there will be way too may events, we don't
        // want that many.
        if (isAndroid10() && changed) {
            // This shouldn't be called very often (like once per configuration change or even
            // less often) so it's okay to allocate lists. Just to not use this method in onDraw
            provideAndroid10GesturesExclusionZones();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void provideAndroid10GesturesExclusionZones() {
        Map<Integer, Set<ExclusionZone>> zonesMap = exclusionZonesHolder.getZones();
        if (zonesMap.size() > 0) {
            int orientation = getContext().getResources().getConfiguration().orientation;
            Set<ExclusionZone> zones = zonesMap.get(orientation);

            if (zones != null && zones.size() > 0) {
                List<Rect> rects = new ArrayList<>();

                for (ExclusionZone exclusionZone : zones) {
                    rects.add(exclusionZone.getZoneRect());
                }

                setSystemGestureExclusionRects(rects);
            }
        }
    }
}
