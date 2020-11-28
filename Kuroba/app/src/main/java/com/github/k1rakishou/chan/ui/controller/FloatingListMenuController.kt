package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.StartActivityComponent
import com.github.k1rakishou.chan.ui.misc.ConstraintLayoutBiasPair
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenu
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem

open class FloatingListMenuController @JvmOverloads constructor(
  context: Context,
  private val constraintLayoutBiasPair: ConstraintLayoutBiasPair,
  private val items: List<FloatingListMenuItem>,
  private val itemClickListener: (item: FloatingListMenuItem) -> Unit,
  private val menuDismissListener: (() -> Unit)? = null
) : BaseFloatingController(context) {
  private lateinit var floatingListMenu: FloatingListMenu
  private lateinit var clickableArea: ConstraintLayout

  override fun injectDependencies(component: StartActivityComponent) {
    component.inject(this)
  }

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

    floatingListMenu.updateLayoutParams<ConstraintLayout.LayoutParams> {
      horizontalBias = constraintLayoutBiasPair.horizontalBias
      verticalBias = constraintLayoutBiasPair.verticalBias
    }

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
    floatingListMenu.onDestroy()

    floatingListMenu.setClickListener(null)
    floatingListMenu.setStackCallback(null)
  }

  open fun stack(moreItems: List<FloatingListMenuItem>) {
    presentController(
      FloatingListMenuController(
        context,
        constraintLayoutBiasPair,
        moreItems,
        itemClickListener,
        menuDismissListener
      )
    )
  }

  override fun getLayoutId(): Int = R.layout.controller_floating_list_menu

}