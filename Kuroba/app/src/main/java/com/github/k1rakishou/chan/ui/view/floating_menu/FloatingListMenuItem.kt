package com.github.k1rakishou.chan.ui.view.floating_menu

import com.github.k1rakishou.chan.ui.toolbar.CheckableToolbarMenuSubItem
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem

open class FloatingListMenuItem @JvmOverloads constructor(
  val key: Any,
  val name: String,
  val value: Any? = null,
  val visible: Boolean = true,
  val enabled: Boolean = true,
  val more: MutableList<FloatingListMenuItem> = mutableListOf()
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FloatingListMenuItem

    if (key != other.key) return false
    if (name != other.name) return false
    if (value != other.value) return false
    if (visible != other.visible) return false
    if (enabled != other.enabled) return false

    return true
  }

  override fun hashCode(): Int {
    var result = key.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + visible.hashCode()
    result = 31 * result + enabled.hashCode()
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
            key = toolbarMenuSubItem.id,
            name = toolbarMenuSubItem.text!!,
            value = null,
            groupId = toolbarMenuSubItem.groupId,
            visible = toolbarMenuSubItem.visible,
            enabled = toolbarMenuSubItem.enabled,
            more = nestedItems,
            isCurrentlySelected = toolbarMenuSubItem.isChecked
          )
        }
        is ToolbarMenuSubItem -> {
          return FloatingListMenuItem(
            key = toolbarMenuSubItem.id,
            name = toolbarMenuSubItem.text!!,
            value = null,
            visible = toolbarMenuSubItem.visible,
            enabled = toolbarMenuSubItem.enabled,
            more = nestedItems
          )
        }
        else -> throw NotImplementedError("Not implemented for ${toolbarMenuSubItem::class.java.simpleName}")
      }
    }

  }
}