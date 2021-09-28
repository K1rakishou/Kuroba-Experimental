package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.misc.ConstraintLayoutBias
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

  private var itemSelected = false

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
      itemSelected = true

      popAll()
      itemClickListener.invoke(clickedItem)
    }

    val innerContainer = view.findViewById<FrameLayout>(R.id.inner_container)
    innerContainer.updateLayoutParams<ConstraintLayout.LayoutParams> {
      matchConstraintMaxWidth = if (isTablet()) {
        TABLET_WIDTH
      } else {
        NORMAL_WIDTH
      }

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

    if (!itemSelected) {
      menuDismissListener?.invoke()
    }

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