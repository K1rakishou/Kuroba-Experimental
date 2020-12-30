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

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.controller.transition.ControllerTransition;
import com.github.k1rakishou.chan.core.di.component.activity.StartActivityComponent;
import com.github.k1rakishou.chan.features.drawer.DrawerCallbacks;
import com.github.k1rakishou.chan.ui.controller.navigation.DoubleNavigationController;
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController;
import com.github.k1rakishou.chan.ui.layout.ThreadSlidingPaneLayout;
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem;
import com.github.k1rakishou.chan.ui.toolbar.Toolbar;
import com.github.k1rakishou.chan.ui.widget.SlidingPaneLayoutEx;
import com.github.k1rakishou.core_themes.ThemeEngine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate;

public class ThreadSlideController
        extends Controller
        implements DoubleNavigationController,
        SlidingPaneLayoutEx.PanelSlideListener,
        ToolbarNavigationController.ToolbarSearchCallback,
        ThemeEngine.ThemeChangesListener {
    private static final String TAG = "ThreadSlideController";

    @Inject
    ThemeEngine themeEngine;

    public Controller leftController;
    public Controller rightController;

    @Nullable
    private DrawerCallbacks drawerCallbacks;
    private boolean leftOpen = true;
    private ViewGroup emptyView;
    private ThreadSlidingPaneLayout slidingPaneLayout;

    @Override
    protected void injectDependencies(@NotNull StartActivityComponent component) {
        component.inject(this);
    }

    public ThreadSlideController(Context context, ViewGroup emptyView, @NotNull DrawerCallbacks drawerCallbacks) {
        super(context);

        this.emptyView = emptyView;
        this.drawerCallbacks = drawerCallbacks;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        doubleNavigationController = this;

        navigation.swipeable = false;
        navigation.handlesToolbarInset = true;
        navigation.hasDrawer = true;

        view = inflate(context, R.layout.controller_thread_slide);

        slidingPaneLayout = view.findViewById(R.id.sliding_pane_layout);
        slidingPaneLayout.setThreadSlideController(this);
        slidingPaneLayout.setPanelSlideListener(this);
        slidingPaneLayout.setParallaxDistance(dp(100));

        if (ChanSettings.slidePaneLayoutShowOverhang.get()) {
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
        drawerCallbacks = null;
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

        if (drawerCallbacks != null) {
            drawerCallbacks.resetBottomNavViewCheckState();
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
    public void onPanelSlide(View panel, float slideOffset) {
    }

    @Override
    public void onPanelOpened(View panel) {
        if (this.leftOpen != leftOpen()) {
            this.leftOpen = leftOpen();
            slideStateChanged();
        }
    }

    @Override
    public void onPanelClosed(View panel) {
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

    public void setLeftController(Controller leftController, boolean animated) {
        if (this.leftController != null) {
            this.leftController.onHide();
            removeChildController(this.leftController);
        }

        this.leftController = leftController;

        if (leftController != null) {
            addChildController(leftController);
            leftController.attachToParentView(slidingPaneLayout.leftPane);
            leftController.onShow();
            if (leftOpen()) {
                setParentNavigationItem(true, animated);
            }
        }
    }

    public void setRightController(Controller rightController, boolean animated) {
        if (this.rightController != null) {
            this.rightController.onHide();
            removeChildController(this.rightController);
        } else {
            this.slidingPaneLayout.rightPane.removeAllViews();
        }

        this.rightController = rightController;

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

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        if (leftOpen() && leftController != null
                && leftController instanceof ToolbarNavigationController.ToolbarSearchCallback) {
            ((ToolbarNavigationController.ToolbarSearchCallback) leftController).onSearchVisibilityChanged(visible);
        }
        if (!leftOpen() && rightController != null
                && rightController instanceof ToolbarNavigationController.ToolbarSearchCallback) {
            ((ToolbarNavigationController.ToolbarSearchCallback) rightController).onSearchVisibilityChanged(visible);
        }
    }

    @Override
    public void onSearchEntered(@NonNull String entered) {
        if (leftOpen() && leftController != null
                && leftController instanceof ToolbarNavigationController.ToolbarSearchCallback) {
            ((ToolbarNavigationController.ToolbarSearchCallback) leftController).onSearchEntered(entered);
        }
        if (!leftOpen() && rightController != null
                && rightController instanceof ToolbarNavigationController.ToolbarSearchCallback) {
            ((ToolbarNavigationController.ToolbarSearchCallback) rightController).onSearchEntered(entered);
        }
    }

    private boolean leftOpen() {
        return slidingPaneLayout.isOpen();
    }

    private void slideStateChanged() {
        slideStateChanged(true);
    }

    private void slideStateChanged(boolean animated) {
        setParentNavigationItem(leftOpen, animated);

        if (leftOpen && rightController instanceof ReplyAutoCloseListener) {
            ((ReplyAutoCloseListener) rightController).onReplyViewShouldClose();
        } else if (!leftOpen && leftController instanceof ReplyAutoCloseListener) {
            ((ReplyAutoCloseListener) leftController).onReplyViewShouldClose();
        }

        notifySlideChanged(leftOpen ? leftController : rightController);
    }

    private void notifySlideChanged(Controller controller) {
        if (controller == null) {
            return;
        }

        if (controller instanceof SlideChangeListener) {
            ((SlideChangeListener) controller).onSlideChanged(leftOpen);
        }

        for (Controller childController : controller.childControllers) {
            notifySlideChanged(childController);
        }
    }

    private void setParentNavigationItem(boolean left) {
        setParentNavigationItem(left, true);
    }

    private void setParentNavigationItem(boolean left, boolean animate) {
        Toolbar toolbar = requireNavController().requireToolbar();

        //default, blank navigation item with no menus or titles, so other layouts don't mess up
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
        navigation.handlesToolbarInset = true;
        navigation.hasDrawer = true;
        toolbar.setNavigationItem(animate, true, navigation, null);
    }

    public interface ReplyAutoCloseListener {
        void onReplyViewShouldClose();
    }

    public interface SlideChangeListener {
        void onSlideChanged(boolean leftOpen);
    }
}
