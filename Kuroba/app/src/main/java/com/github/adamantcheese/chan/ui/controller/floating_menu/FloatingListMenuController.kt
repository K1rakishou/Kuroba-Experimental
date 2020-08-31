package com.github.adamantcheese.chan.ui.controller.floating_menu

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.ui.controller.BaseFloatingController
import com.github.adamantcheese.chan.ui.view.floating_menu.FloatingListMenu

open class FloatingListMenuController @JvmOverloads constructor(
  context: Context,
  protected val items: List<FloatingListMenu.FloatingListMenuItem>,
  protected val itemClickListener: (item: FloatingListMenu.FloatingListMenuItem) -> Unit,
  protected val menuDismissListener: (() -> Unit)? = null
) : BaseFloatingController(context) {
  private lateinit var floatingListMenu: FloatingListMenu
  private lateinit var clickableArea: ConstraintLayout

  override fun onCreate() {
    super.onCreate()

    clickableArea = view.findViewById(R.id.clickable_area)
    clickableArea.setOnClickListener { stopPresenting(true) }

    floatingListMenu = view.findViewById(R.id.floating_list_menu)
    floatingListMenu.setItems(items)
    floatingListMenu.setClickListener { clickedItem ->
      popAll()
      itemClickListener.invoke(clickedItem)
    }

    setupListMenuGravity(floatingListMenu)
    floatingListMenu.setStackCallback { moreItems -> stack(moreItems) }
  }

  private fun popAll() {
    stopPresenting(false)

    var parent = presentedByController

    while (parent is FloatingListMenuController && parent.alive) {
      parent.stopPresenting()
      parent = parent.presentedByController
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    menuDismissListener?.invoke()
    floatingListMenu.setClickListener(null)
    floatingListMenu.setStackCallback(null)
  }

  override fun onBack(): Boolean {
    stopPresenting(true)
    return true
  }

  open fun stack(moreItems: List<FloatingListMenu.FloatingListMenuItem>) {
    presentController(
      FloatingListMenuController(context, moreItems, itemClickListener, menuDismissListener)
    )
  }

  open fun setupListMenuGravity(menu: FloatingListMenu) {
    (menu.layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
      params.verticalBias = 1f
    }
  }

  override fun getLayoutId(): Int = R.layout.controller_floating_list_menu

  companion object {
    fun create(
      context: Context,
      isThreadMode: Boolean,
      items: List<FloatingListMenu.FloatingListMenuItem>,
      itemClickListener: (item: FloatingListMenu.FloatingListMenuItem) -> Unit
    ): FloatingListMenuController {
      return if (isThreadMode) {
        ThreadFloatingListMenuController(
          context,
          items,
          itemClickListener
        )
      } else {
        CatalogFloatingListMenuController(
          context,
          items,
          itemClickListener
        )
      }
    }
  }
}