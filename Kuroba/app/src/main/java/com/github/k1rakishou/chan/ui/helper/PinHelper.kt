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
package com.github.k1rakishou.chan.ui.helper

object PinHelper {

  @JvmStatic
  fun getShortUnreadCount(value: Int): String {
    if (value < 1000) {
      return value.toString()
    }

    val thousands = value.toFloat() / 1000f
    if (thousands >= 1000f) {
      return thousands.toString() + "kk"
    }

    return "%.${1}f".format(thousands) + "k"
  }

}