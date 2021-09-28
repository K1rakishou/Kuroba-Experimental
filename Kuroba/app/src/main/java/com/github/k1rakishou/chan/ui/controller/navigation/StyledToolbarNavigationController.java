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

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate;

import android.content.Context;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.controller.transition.ControllerTransition;
import com.github.k1rakishou.chan.controller.ui.NavigationControllerContainerLayout;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.features.drawer.MainController;
import com.github.k1rakishou.chan.ui.controller.PopupController;

import org.jetbrains.annotations.NotNull;

public class StyledToolbarNavigationController extends ToolbarNavigationController {

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    public StyledToolbarNavigationController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflate(context, R.layout.controller_navigation_toolbar);
        container = (NavigationControllerContainerLayout) view.findViewById(R.id.toolbar_navigation_controller_container);

        NavigationControllerContainerLayout nav = (NavigationControllerContainerLayout) container;
        nav.initThreadControllerTracking(
                ChanSettings.controllerSwipeable.get(),
                this
        );

        setToolbar(view.findViewById(R.id.toolbar));
        requireToolbar().setCallback(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        requireToolbar().removeCallback();
    }

    @Override
    public boolean popController(ControllerTransition controllerTransition) {
        return !requireToolbar().isTransitioning() && super.popController(controllerTransition);
    }

    @Override
    public boolean pushController(Controller to, ControllerTransition controllerTransition) {
        return !requireToolbar().isTransitioning() && super.pushController(to, controllerTransition);
    }

    @Override
    public void transition(
            Controller from,
            Controller to,
            boolean pushing,
            ControllerTransition controllerTransition
    ) {
        super.transition(from, to, pushing, controllerTransition);

        if (to != null) {
            MainController mainController = getMainController();
            if (mainController != null) {
                mainController.setDrawerEnabled(to.navigation.hasDrawer);
            }
        }
    }

    @Override
    public void endSwipeTransition(Controller from, Controller to, boolean finish) {
        super.endSwipeTransition(from, to, finish);

        if (finish) {
            MainController mainController = getMainController();
            if (mainController != null) {
                mainController.setDrawerEnabled(to.navigation.hasDrawer);
            }
        }
    }

    @Override
    public boolean onBack() {
        if (super.onBack()) {
            return true;
        }

        if (parentController instanceof PopupController && childControllers.size() == 1) {
            ((PopupController) parentController).dismiss();
            return true;
        }

        if (doubleNavigationController != null && childControllers.size() == 1) {
            if (doubleNavigationController.getRightController() == this) {
                doubleNavigationController.setRightController(null, false);
                return true;
            }

            return false;
        }

        return false;
    }

    @Override
    public void onMenuClicked() {
        MainController mainController = getMainController();
        if (mainController != null) {
            mainController.onMenuClicked();
        }
    }

    private MainController getMainController() {
        if (parentController instanceof MainController) {
            return (MainController) parentController;
        } else if (doubleNavigationController != null) {
            Controller doubleNav = (Controller) doubleNavigationController;
            if (doubleNav.parentController instanceof MainController) {
                return (MainController) doubleNav.parentController;
            }
        }
        return null;
    }
}
