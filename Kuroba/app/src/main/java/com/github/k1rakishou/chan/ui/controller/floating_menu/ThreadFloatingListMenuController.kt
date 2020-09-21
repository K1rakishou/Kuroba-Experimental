package com.github.k1rakishou.chan.ui.controller.floating_menu

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenu
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AndroidUtils

class ThreadFloatingListMenuController(
  context: Context,
  items: List<FloatingListMenuItem>,
  itemClickListener: (item: FloatingListMenuItem) -> Unit,
  menuDismissListener: (() -> Unit)? = null
) : FloatingListMenuController(context, items, itemClickListener, menuDismissListener) {

  override fun stack(moreItems: List<FloatingListMenuItem>) {
    if (!AndroidUtils.isTablet()) {
      super.stack(moreItems)
      return
    }

    presentController(
      ThreadFloatingListMenuController(context, moreItems, itemClickListener, menuDismissListener)
    )
  }

  override fun setupListMenuGravity(menu: FloatingListMenu) {
    if (!AndroidUtils.isTablet()) {
      super.setupListMenuGravity(menu)
      return
    }

    (menu.layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
      params.verticalBias = 1f
      params.horizontalBias = 1f
    }
  }

}