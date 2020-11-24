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
package com.github.k1rakishou.chan.ui.toolbar

import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.DoNotStrip
import java.util.*

/**
 * An item for a submenu of a ToolbarMenuItem. Most common as subitem for the overflow button.
 * Add with NavigationItem MenuBuilder.
 */
@DoNotStrip
open class ToolbarMenuSubItem {
  @JvmField
  var id = 0
  @JvmField
  var text: String? = null
  @JvmField
  var visible = true
  @JvmField
  var enabled = true
  @JvmField
  var value: Any? = null
  @JvmField
  var moreItems: MutableList<ToolbarMenuSubItem> = ArrayList()

  var clickCallback: ClickCallback? = null

  @JvmOverloads
  constructor(
    id: Int,
    textId: Int,
    clicked: ClickCallback? = null,
    visible: Boolean = true,
    value: Any? = null
  ) {
    this.id = id
    this.text = getString(textId)
    this.visible = visible
    this.value = value
    this.clickCallback = clicked
  }

  @JvmOverloads
  constructor(
    id: Int,
    text: String,
    clicked: ClickCallback? = null,
    visible: Boolean = true,
    value: Any? = null
  ) {
    this.id = id
    this.text = text
    this.visible = visible
    this.value = value
    this.clickCallback = clicked
  }

  fun addNestedItem(subItem: ToolbarMenuSubItem) {
    moreItems.add(subItem)
  }

  fun performClick() {
    clickCallback?.clicked(this)
  }

  fun interface ClickCallback {
    fun clicked(subItem: ToolbarMenuSubItem)
  }
}