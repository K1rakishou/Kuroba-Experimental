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

import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.controller.transition.ControllerTransition;
import com.github.k1rakishou.chan.core.navigation.HasNavigation;

public interface DoubleNavigationController extends HasNavigation {
    void setLeftController(Controller leftController, boolean animated);

    void setRightController(Controller rightController, boolean animated);

    Controller getLeftController();

    Controller getRightController();

    void switchToController(boolean leftController, boolean animated);

    void switchToController(boolean leftController);

    void openControllerWrappedIntoBottomNavAwareController(Controller controller);

    boolean pushController(Controller to);

    boolean pushController(Controller to, boolean animated);

    boolean pushController(Controller to, ControllerTransition controllerTransition);

    boolean popController();

    boolean popController(boolean animated);

    boolean popController(ControllerTransition controllerTransition);
}
