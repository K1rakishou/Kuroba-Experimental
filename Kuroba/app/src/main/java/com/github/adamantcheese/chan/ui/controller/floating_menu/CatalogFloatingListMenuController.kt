package com.github.adamantcheese.chan.ui.controller.floating_menu

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.adamantcheese.chan.ui.view.floating_menu.FloatingListMenu
import com.github.adamantcheese.chan.utils.AndroidUtils

class CatalogFloatingListMenuController(
  context: Context,
  items: List<FloatingListMenu.FloatingListMenuItem>,
  itemClickListener: (item: FloatingListMenu.FloatingListMenuItem) -> Unit,
  menuDismissListener: (() -> Unit)? = null
) : FloatingListMenuController(context, items, itemClickListener, menuDismissListener) {

  override fun stack(moreItems: List<FloatingListMenu.FloatingListMenuItem>) {
    presentController(
      CatalogFloatingListMenuController(context, moreItems, itemClickListener, menuDismissListener)
    )
  }

  override fun setupListMenuGravity(menu: FloatingListMenu) {
    if (!AndroidUtils.isTablet()) {
      super.setupListMenuGravity(menu)
      return
    }

    (menu.layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
      params.verticalBias = 1f
      params.horizontalBias = 0f
    }
  }

}