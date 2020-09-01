package com.github.adamantcheese.chan.ui.view.floating_menu

class CheckableFloatingListMenuItem(
  key: Any,
  name: String,
  value: Any? = null,
  visible: Boolean = true,
  more: MutableList<FloatingListMenuItem> = mutableListOf(),
  val isCurrentlySelected: Boolean = false
) : FloatingListMenuItem(key, name, value, visible, more)