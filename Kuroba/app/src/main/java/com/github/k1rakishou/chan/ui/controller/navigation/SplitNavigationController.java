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
package com.github.k1rakishou.chan.ui.controller.navigation;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.controller.transition.ControllerTransition;
import com.github.k1rakishou.chan.controller.transition.PopControllerTransition;
import com.github.k1rakishou.chan.controller.transition.PushControllerTransition;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks;
import com.github.k1rakishou.chan.ui.controller.PopupController;
import com.github.k1rakishou.chan.ui.layout.SplitNavigationControllerLayout;
import com.github.k1rakishou.chan.ui.view.NavigationViewContract;
import com.github.k1rakishou.common.KotlinExtensionsKt;
import com.github.k1rakishou.core_themes.ThemeEngine;

import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

public class SplitNavigationController
        extends Controller
        implements DoubleNavigationController, ThemeEngine.ThemeChangesListener {

    @Inject
    ThemeEngine themeEngine;

    public Controller leftController;
    public Controller rightController;

    @Nullable
    private MainControllerCallbacks mainControllerCallbacks;
    private FrameLayout leftControllerView;
    private FrameLayout rightControllerView;
    private ViewGroup emptyView;
    private TextView selectThreadText;

    private PopupController popup;
    private StyledToolbarNavigationController popupChild;

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    public SplitNavigationController(Context context, ViewGroup emptyView, @NotNull MainControllerCallbacks mainControllerCallbacks) {
        super(context);

        this.emptyView = emptyView;
        this.mainControllerCallbacks = mainControllerCallbacks;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        doubleNavigationController = this;

        SplitNavigationControllerLayout container = new SplitNavigationControllerLayout(context);
        view = container;

        leftControllerView = new FrameLayout(context);
        rightControllerView = new FrameLayout(context);

        if (mainControllerCallbacks.getNavigationViewContractType() == NavigationViewContract.Type.BottomNavView) {
            int bottomNavViewHeight = (int) context.getResources().getDimension(R.dimen.navigation_view_size);
            KotlinExtensionsKt.updatePaddings(container, null, null, null, bottomNavViewHeight);
        }

        container.setLeftView(leftControllerView);
        container.setRightView(rightControllerView);
        container.setDivider(new View(context));
        container.build();

        selectThreadText = emptyView.findViewById(R.id.select_thread_text);

        setRightController(null, false);
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
        if (selectThreadText != null) {
            selectThreadText.setTextColor(themeEngine.getChanTheme().getTextColorSecondary());
        }
    }

    @Override
    public void setLeftController(Controller leftController, boolean animated) {
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
    public void setRightController(Controller rightController, boolean animated) {
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
    public void switchToController(boolean leftController, boolean animated) {
        // both are always visible
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
                if (mainControllerCallbacks != null) {
                    mainControllerCallbacks.resetBottomNavViewCheckState();
                }

                if (presentingThisController != null) {
                    presentingThisController.stopPresenting();
                }

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
            if (mainControllerCallbacks != null) {
                mainControllerCallbacks.resetBottomNavViewCheckState();
            }

            if (presentingThisController != null) {
                presentingThisController.stopPresenting();
            }

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
        return (rightController != null && rightController.dispatchKeyEvent(event))
                || (leftController != null && leftController.dispatchKeyEvent(event))
                || super.dispatchKeyEvent(event);
    }

}
