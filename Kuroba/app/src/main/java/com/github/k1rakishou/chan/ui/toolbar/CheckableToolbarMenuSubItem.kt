package com.github.k1rakishou.chan.ui.toolbar

class CheckableToolbarMenuSubItem @JvmOverloads constructor(
  id: Int,
  textId: Int,
  clicked: ClickCallback? = null,
  visible: Boolean = true,
  value: Any? = null,
  @JvmField var isCurrentlySelected: Boolean = false
) : ToolbarMenuSubItem(id, textId, clicked, visible, value)