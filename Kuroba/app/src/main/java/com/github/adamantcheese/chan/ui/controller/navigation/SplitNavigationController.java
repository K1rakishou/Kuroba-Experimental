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
package com.github.adamantcheese.chan.ui.controller.navigation;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.controller.transition.ControllerTransition;
import com.github.adamantcheese.chan.controller.transition.PopControllerTransition;
import com.github.adamantcheese.chan.controller.transition.PushControllerTransition;
import com.github.adamantcheese.chan.features.drawer.DrawerCallbacks;
import com.github.adamantcheese.chan.ui.controller.PopupController;
import com.github.adamantcheese.chan.ui.layout.SplitNavigationControllerLayout;
import com.github.adamantcheese.common.KotlinExtensionsKt;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class SplitNavigationController
        extends Controller
        implements DoubleNavigationController {
    public Controller leftController;
    public Controller rightController;

    @Nullable
    private DrawerCallbacks drawerCallbacks;
    private FrameLayout leftControllerView;
    private FrameLayout rightControllerView;
    private ViewGroup emptyView;

    private PopupController popup;
    private StyledToolbarNavigationController popupChild;

    public SplitNavigationController(Context context) {
        super(context);
    }

    public void setDrawerCallbacks(@NotNull DrawerCallbacks drawerCallbacks) {
        this.drawerCallbacks = drawerCallbacks;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        doubleNavigationController = this;

        SplitNavigationControllerLayout container = new SplitNavigationControllerLayout(context);
        view = container;

        View dividerView = new View(context);
        dividerView.setBackgroundColor(getAttrColor(context, R.attr.divider_split_color));

        leftControllerView = new FrameLayout(context);
        rightControllerView = new FrameLayout(context);

        int bottomNavViewHeight = (int) context.getResources().getDimension(R.dimen.bottom_nav_view_height);
        KotlinExtensionsKt.updatePaddings(container, null, null, null, bottomNavViewHeight);

        container.setLeftView(leftControllerView);
        container.setRightView(rightControllerView);
        container.setDivider(dividerView);
        container.build();

        setRightController(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        drawerCallbacks = null;
    }

    @Override
    public void setEmptyView(ViewGroup emptyView) {
        this.emptyView = emptyView;
    }

    @Override
    public void setLeftController(Controller leftController) {
        if (this.leftController != null) {
            this.leftController.onHide();
            removeChildController(this.leftController);
        }

        this.leftController = leftController;

        if (leftController != null) {
            addChildController(leftController);
            leftController.attachToParentView(leftControllerView);
            leftController.onShow();
        }
    }

    @Override
    public void setRightController(Controller rightController) {
        if (this.rightController != null) {
            this.rightController.onHide();
            removeChildController(this.rightController);
        } else {
            rightControllerView.removeAllViews();
        }

        this.rightController = rightController;

        if (rightController != null) {
            addChildController(rightController);
            rightController.attachToParentView(rightControllerView);
            rightController.onShow();
        } else {
            rightControllerView.addView(emptyView);
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
    public void switchToController(boolean leftController) {
        // both are always visible
    }

    @Override
    public void openControllerWrappedIntoBottomNavAwareController(Controller controller) {
        requireStartActivity().openControllerWrappedIntoBottomNavAwareController(controller);
    }

    @Override
    public boolean pushController(final Controller to) {
        return pushController(to, true);
    }

    @Override
    public boolean pushController(final Controller to, boolean animated) {
        return pushController(to, animated ? new PushControllerTransition() : null);
    }

    @Override
    public boolean pushController(Controller to, ControllerTransition controllerTransition) {
        if (popup == null) {
            popup = new PopupController(context);
            presentController(popup);
            popupChild = new StyledToolbarNavigationController(context);
            popup.setChildController(popupChild);
            popupChild.pushController(to, false);
        } else {
            popupChild.pushController(to, controllerTransition);
        }

        return true;
    }

    @Override
    public boolean popController() {
        return popController(true);
    }

    @Override
    public boolean popController(boolean animated) {
        return popController(animated ? new PopControllerTransition() : null);
    }

    @Override
    public boolean popController(ControllerTransition controllerTransition) {
        if (popup != null) {
            if (popupChild.childControllers.size() == 1) {
                if (drawerCallbacks != null) {
                    drawerCallbacks.resetBottomNavViewCheckState();
                }

                Objects.requireNonNull(presentingThisController);
                presentingThisController.stopPresenting();

                popup = null;
                popupChild = null;
            } else {
                popupChild.popController(controllerTransition);
            }
            return true;
        } else {
            return false;
        }
    }

    public void popAll() {
        if (popup != null) {
            if (drawerCallbacks != null) {
                drawerCallbacks.resetBottomNavViewCheckState();
            }

            Objects.requireNonNull(presentingThisController);
            presentingThisController.stopPresenting();

            popup = null;
            popupChild = null;
        }
    }

    @Override
    public boolean onBack() {
        if (leftController != null && leftController.onBack()) {
            return true;
        } else if (rightController != null && rightController.onBack()) {
            return true;
        }
        return super.onBack();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return (rightController != null && rightController.dispatchKeyEvent(event)) || (leftController != null
                && leftController.dispatchKeyEvent(event)) || super.dispatchKeyEvent(event);
    }

}
