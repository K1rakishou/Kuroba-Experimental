package com.github.k1rakishou.chan.ui.view.floating_menu

import com.github.k1rakishou.chan.ui.toolbar.CheckableToolbarMenuSubItem
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem

open class FloatingListMenuItem @JvmOverloads constructor(
  val key: Any,
  val name: String,
  val value: Any? = null,
  val visible: Boolean = true,
  val more: MutableList<FloatingListMenuItem> = mutableListOf()
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FloatingListMenuItem

    if (key != other.key) return false
    if (name != other.name) return false
    if (value != other.value) return false

    return true
  }

  override fun hashCode(): Int {
    var result = key.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + (value?.hashCode() ?: 0)
    return result
  }

  companion object {

    @JvmStatic
    fun createFromToolbarMenuSubItem(
      toolbarMenuSubItem: ToolbarMenuSubItem,
      withNestedItems: Boolean = true
    ): FloatingListMenuItem {
      val nestedItems = if (withNestedItems) {
        toolbarMenuSubItem.moreItems.map { item -> createFromToolbarMenuSubItem(item) }.toMutableList()
      } else {
        mutableListOf()
      }

      when (toolbarMenuSubItem) {
        is CheckableToolbarMenuSubItem -> {
          return CheckableFloatingListMenuItem(
            toolbarMenuSubItem.id,
            toolbarMenuSubItem.text!!,
            null,
            toolbarMenuSubItem.visible,
            nestedItems,
            toolbarMenuSubItem.isCurrentlySelected
          )
        }
        is ToolbarMenuSubItem -> {
          return FloatingListMenuItem(
            toolbarMenuSubItem.id,
            toolbarMenuSubItem.text!!,
            null,
            toolbarMenuSubItem.visible,
            nestedItems
          )
        }
        else -> throw NotImplementedError("Not implemented for ${toolbarMenuSubItem::class.java.simpleName}")
      }
    }

  }
}