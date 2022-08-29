package com.github.k1rakishou.chan.ui.view.floating_menu

class CheckableFloatingListMenuItem(
  key: Any,
  name: String,
  value: Any? = null,
  val groupId: Any? = null,
  visible: Boolean = true,
  enabled: Boolean = true,
  more: MutableList<FloatingListMenuItem> = mutableListOf(),
  val isCurrentlySelected: Boolean = false
) : FloatingListMenuItem(key, name, value, visible, enabled, more)