package com.github.adamantcheese.chan.ui.controller

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.ui.view.floating_menu.FloatingListMenu

class FloatingListMenuController @JvmOverloads constructor(
  context: Context,
  private val items: List<FloatingListMenu.FloatingListMenuItem>,
  private val itemClickListener: (item: FloatingListMenu.FloatingListMenuItem) -> Unit,
  private val menuDismissListener: (() -> Unit)? = null
) : BaseFloatingController(context) {
  private lateinit var floatingListMenu: FloatingListMenu
  private lateinit var clickableArea: ConstraintLayout

  override fun onCreate() {
    super.onCreate()

    floatingListMenu = view.findViewById(R.id.floating_list_menu)
    clickableArea = view.findViewById(R.id.clickable_area)

    clickableArea.setOnClickListener { stopPresenting(true) }
    floatingListMenu.setStackCallback { moreItems -> stack(moreItems) }

    floatingListMenu.setItems(items)
    floatingListMenu.setClickListener { clickedItem ->
      popAll()
      itemClickListener.invoke(clickedItem)
    }
  }

  private fun popAll() {
    stopPresenting(false)

    var parent = presentedByController

    while (parent is FloatingListMenuController && parent.alive) {
      parent.stopPresenting()
      parent = parent.presentedByController
    }
  }

  private fun stack(moreItems: List<FloatingListMenu.FloatingListMenuItem>) {
    presentController(
      FloatingListMenuController(context, moreItems, itemClickListener, menuDismissListener)
    )
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

  override fun getLayoutId(): Int = R.layout.controller_floating_list_menu
}