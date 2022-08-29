package com.github.k1rakishou.chan.ui.toolbar

class CheckableToolbarMenuSubItem @JvmOverloads constructor(
  id: Int,
  text: String,
  clicked: ClickCallback? = null,
  visible: Boolean = true,
  value: Any? = null,
  val groupId: Any? = null,
  @JvmField var isChecked: Boolean = false
) : ToolbarMenuSubItem(id, text, clicked, visible, value)