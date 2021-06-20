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
package com.github.k1rakishou.chan.ui.controller.navigation

import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.navigation.ControllerWithNavigation
import com.github.k1rakishou.chan.core.navigation.HasNavigation

interface DoubleNavigationController : ControllerWithNavigation, HasNavigation {
  fun setLeftController(leftController: Controller?, animated: Boolean)
  fun setRightController(rightController: Controller?, animated: Boolean)
  fun getLeftController(): Controller?
  fun getRightController(): Controller?

  fun switchToController(leftController: Boolean, animated: Boolean)
  fun switchToController(leftController: Boolean)
  fun openControllerWrappedIntoBottomNavAwareController(controller: Controller?)
}