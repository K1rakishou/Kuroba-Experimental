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
package com.github.k1rakishou.chan.ui.controller;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.controller.transition.ControllerTransition;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks;
import com.github.k1rakishou.chan.ui.controller.navigation.DoubleNavigationController;
import com.github.k1rakishou.chan.ui.layout.ThreadSlidingPaneLayout;
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem;
import com.github.k1rakishou.chan.ui.toolbar.Toolbar;
import com.github.k1rakishou.chan.ui.widget.SlidingPaneLayoutEx;
import com.github.k1rakishou.core_themes.ThemeEngine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;

public class ThreadSlideController
        extends Controller
        implements DoubleNavigationController,
        SlidingPaneLayoutEx.PanelSlideListener,
        ThemeEngine.ThemeChangesListener {
    private static final String TAG = "ThreadSlideController";

    @Inject
    ThemeEngine themeEngine;

    public BrowseController leftController;
    public ViewThreadController rightController;

    @Nullable
    private MainControllerCallbacks mainControllerCallbacks;
    private boolean leftOpen = true;
    private ViewGroup emptyView;
    private ThreadSlidingPaneLayout slidingPaneLayout;

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    public ThreadSlideController(Context context, ViewGroup emptyView, @NotNull MainControllerCallbacks mainControllerCallbacks) {
        super(context);

        this.emptyView = emptyView;
        this.mainControllerCallbacks = mainControllerCallbacks;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        doubleNavigationController = this;

        navigation.swipeable = false;
        navigation.hasDrawer = true;

        view = inflate(context, R.layout.controller_thread_slide);

        slidingPaneLayout = view.findViewById(R.id.sliding_pane_layout);
        slidingPaneLayout.setThreadSlideController(this);
        slidingPaneLayout.setPanelSlideListener(this);
        slidingPaneLayout.setParallaxDistance(dp(100));
        slidingPaneLayout.allowedToSlide(ChanSettings.viewThreadControllerSwipeable.get());

        if (ChanSettings.isSlideLayoutMode()) {
            slidingPaneLayout.setShadowResourceLeft(R.drawable.panel_shadow);
        }

        slidingPaneLayout.openPane();

        setLeftController(null, false);
        setRightController(null, false);

        TextView textView = emptyView.findViewById(R.id.select_thread_text);
        if (textView != null) {
            textView.setTextColor(themeEngine.getChanTheme().getTextColorSecondary());
        }

        themeEngine.addListener(this);
        onThemeChanged();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        themeEngine.removeListener(this);
        mainControllerCallbacks = null;
    }

    @Override
    public void onThemeChanged() {
        if (slidingPaneLayout != null) {
            int fadeColor = (themeEngine.getChanTheme().getBackColor() & 0xffffff) + 0xCC000000;
            slidingPaneLayout.setSliderFadeColor(fadeColor);
            slidingPaneLayout.requestLayout();
        }
    }

    @Override
    public void onShow() {
        super.onShow();

        if (mainControllerCallbacks != null) {
            mainControllerCallbacks.resetBottomNavViewCheckState();
        }
    }

    public void onSlidingPaneLayoutStateRestored() {
        boolean restoredOpen = slidingPaneLayout.getPreservedOpenState();

        if (restoredOpen != leftOpen) {
            leftOpen = restoredOpen;
            slideStateChanged(false);
        }
    }

    @Override
    public void onPanelSlide(@NonNull View panel, float slideOffset) {
    }

    @Override
    public void onPanelOpened(@NonNull View panel) {
        if (this.leftOpen != leftOpen()) {
            this.leftOpen = leftOpen();
            slideStateChanged();
        }
    }

    @Override
    public void onPanelClosed(@NonNull View panel) {
        if (this.leftOpen != leftOpen()) {
            this.leftOpen = leftOpen();
            slideStateChanged();
        }
    }

    @Override
    public void switchToController(boolean leftController) {
        switchToController(leftController, true);
    }

    @Override
    public void switchToController(boolean leftController, boolean animated) {
        if (leftController != leftOpen()) {
            if (leftController) {
                slidingPaneLayout.openPane();
            } else {
                slidingPaneLayout.closePane();
            }

            requireNavController().requireToolbar().processScrollCollapse(
                    Toolbar.TOOLBAR_COLLAPSE_SHOW,
                    true
            );

            leftOpen = leftController;
            slideStateChanged(animated);
        }
    }

    public void setLeftController(@Nullable Controller leftController, boolean animated) {
        if (this.leftController != null) {
            this.leftController.onHide();
            removeChildController(this.leftController);
        }

        this.leftController = (BrowseController) leftController;

        if (leftController != null) {
            addChildController(leftController);
            leftController.attachToParentView(slidingPaneLayout.leftPane);
            leftController.onShow();
            if (leftOpen()) {
                setParentNavigationItem(true, animated);
            }
        }
    }

    public void setRightController(@Nullable Controller rightController, boolean animated) {
        if (this.rightController != null) {
            this.rightController.onHide();
            removeChildController(this.rightController);
        } else {
            this.slidingPaneLayout.rightPane.removeAllViews();
        }

        this.rightController = (ViewThreadController) rightController;

        if (rightController != null) {
            addChildController(rightController);
            rightController.attachToParentView(slidingPaneLayout.rightPane);
            rightController.onShow();
            if (!leftOpen()) {
                setParentNavigationItem(false, animated);
            }
        } else {
            slidingPaneLayout.rightPane.addView(emptyView);
        }
    }

    @Override
    public Controller getLeftController() {
        return leftController;
    }

    @Override
    public Controller getRightController() {
        return rightController;
    }

    @Override
    public void openControllerWrappedIntoBottomNavAwareController(Controller controller) {
        requireStartActivity().openControllerWrappedIntoBottomNavAwareController(controller);
    }

    @Override
    public boolean pushController(Controller to) {
        return navigationController.pushController(to);
    }

    @Override
    public boolean pushController(Controller to, boolean animated) {
        return navigationController.pushController(to, animated);
    }

    @Override
    public boolean pushController(Controller to, ControllerTransition controllerTransition) {
        return navigationController.pushController(to, controllerTransition);
    }

    @Override
    public boolean popController() {
        return navigationController.popController();
    }

    @Override
    public boolean popController(boolean animated) {
        return navigationController.popController(animated);
    }

    @Override
    public boolean popController(ControllerTransition controllerTransition) {
        return navigationController.popController(controllerTransition);
    }

    @Override
    public boolean onBack() {
        if (!leftOpen()) {
            if (rightController != null && rightController.onBack()) {
                return true;
            } else {
                switchToController(true);
                return true;
            }
        } else {
            if (leftController != null && leftController.onBack()) {
                return true;
            }
        }

        return super.onBack();
    }

    public boolean leftOpen() {
        return slidingPaneLayout.isOpen();
    }

    private void slideStateChanged() {
        slideStateChanged(true);
    }

    private void slideStateChanged(boolean animated) {
        setParentNavigationItem(leftOpen, animated);

        if (leftOpen && rightController != null) {
            ((ReplyAutoCloseListener) rightController).onReplyViewShouldClose();
        } else if (!leftOpen && leftController != null) {
            ((ReplyAutoCloseListener) leftController).onReplyViewShouldClose();
        }

        notifyFocusLost(
                leftOpen ? ThreadControllerType.Thread : ThreadControllerType.Catalog,
                leftOpen ? rightController : leftController
        );

        notifyFocusGained(
                leftOpen ? ThreadControllerType.Catalog : ThreadControllerType.Thread,
                leftOpen ? leftController : rightController
        );
    }

    private void notifyFocusLost(ThreadControllerType controllerType, Controller controller) {
        if (controller == null) {
            return;
        }

        if (controller instanceof SlideChangeListener) {
            ((SlideChangeListener) controller).onLostFocus(controllerType);
        }

        for (Controller childController : controller.childControllers) {
            notifyFocusGained(controllerType, childController);
        }
    }

    private void notifyFocusGained(ThreadControllerType controllerType, Controller controller) {
        if (controller == null) {
            return;
        }

        if (controller instanceof SlideChangeListener) {
            ((SlideChangeListener) controller).onGainedFocus(controllerType);
        }

        for (Controller childController : controller.childControllers) {
            notifyFocusGained(controllerType, childController);
        }
    }

    private void setParentNavigationItem(boolean left, boolean animate) {
        Toolbar toolbar = requireNavController().requireToolbar();

        // default, blank navigation item with no menus or titles, so other layouts don't mess up
        NavigationItem item = new NavigationItem();
        if (left) {
            if (leftController != null) {
                item = leftController.navigation;
            }
        } else {
            if (rightController != null) {
                item = rightController.navigation;
            }
        }

        navigation = item;
        navigation.swipeable = false;
        navigation.hasDrawer = true;

        toolbar.setNavigationItem(animate, true, navigation, null);
    }

    public boolean passMotionEventIntoSlidingPaneLayout(@NotNull MotionEvent event) {
        return slidingPaneLayout.onTouchEvent(event);
    }

    public interface ReplyAutoCloseListener {
        void onReplyViewShouldClose();
    }

    public interface SlideChangeListener {
        void onGainedFocus(@NonNull ThreadControllerType controllerType);
        void onLostFocus(@NonNull ThreadControllerType controllerType);
    }

    public enum ThreadControllerType {
        Catalog,
        Thread
    }
}
