package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.misc.ConstraintLayoutBias
import com.github.k1rakishou.chan.ui.view.ViewContainerWithMaxSize
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenu
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet

open class FloatingListMenuController @JvmOverloads constructor(
  context: Context,
  private val constraintLayoutBias: ConstraintLayoutBias,
  private val items: List<FloatingListMenuItem>,
  private val itemClickListener: (item: FloatingListMenuItem) -> Unit,
  private val menuDismissListener: (() -> Unit)? = null
) : BaseFloatingController(context) {
  private lateinit var floatingListMenu: FloatingListMenu
  private lateinit var clickableArea: ConstraintLayout

  override fun injectDependencies(component: ActivityComponent) {
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

    val viewContainerWithMaxSize = view.findViewById<ViewContainerWithMaxSize>(R.id.floating_list_menu_container)
    viewContainerWithMaxSize.desiredWidth = if (isTablet()) {
      TABLET_WIDTH
    } else {
      NORMAL_WIDTH
    }

    viewContainerWithMaxSize.updateLayoutParams<ConstraintLayout.LayoutParams> {
      horizontalBias = constraintLayoutBias.horizontalBias
      verticalBias = constraintLayoutBias.verticalBias
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
        constraintLayoutBias,
        moreItems,
        itemClickListener,
        menuDismissListener
      )
    )
  }

  override fun getLayoutId(): Int = R.layout.controller_floating_list_menu

  companion object {
    private val TABLET_WIDTH = dp(400f)
    private val NORMAL_WIDTH = dp(320f)
  }

}