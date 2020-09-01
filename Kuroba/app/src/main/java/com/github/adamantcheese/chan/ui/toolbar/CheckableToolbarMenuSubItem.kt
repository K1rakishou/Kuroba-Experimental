package com.github.adamantcheese.chan.ui.toolbar

class CheckableToolbarMenuSubItem : ToolbarMenuSubItem {

  @JvmField
  var isCurrentlySelected = false

  @JvmOverloads
  constructor(
    id: Int,
    textId: Int,
    clicked: ClickCallback? = null,
    visible: Boolean = true,
    value: Any? = null,
    isCurrentlySelected: Boolean = false
  ) : super(id, textId, clicked, visible, value) {
    this.isCurrentlySelected = isCurrentlySelected
  }

}