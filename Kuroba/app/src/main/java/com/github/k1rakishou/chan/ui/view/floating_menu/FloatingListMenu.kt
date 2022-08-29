package com.github.k1rakishou.chan.ui.view.floating_menu

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.epoxy.EpoxyDividerView
import com.github.k1rakishou.chan.ui.epoxy.epoxyDividerView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.ui.view.floating_menu.epoxy.epoxyCheckableFloatingListMenuRow
import com.github.k1rakishou.chan.ui.view.floating_menu.epoxy.epoxyFloatingListMenuRow
import com.github.k1rakishou.chan.ui.view.floating_menu.epoxy.epoxyGroupableFloatingListMenuRow
import com.github.k1rakishou.chan.ui.view.floating_menu.epoxy.epoxyHeaderListMenuRow
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp

class FloatingListMenu @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val epoxyRecyclerView: ColorizableEpoxyRecyclerView
  private val menuItems = mutableListOf<FloatingListMenuItem>()

  private var itemClickListener: ((item: FloatingListMenuItem) -> Unit)? = null
  private var stackCallback: ((moreItems: List<FloatingListMenuItem>) -> Unit)? = null

  init {
    inflate(context, R.layout.floating_list_menu, this)

    epoxyRecyclerView = findViewById(R.id.floating_list_menu_recycler)
  }

  fun setClickListener(listener: ((item: FloatingListMenuItem) -> Unit)?) {
    this.itemClickListener = listener
  }

  fun setStackCallback(callback: ((moreItems: List<FloatingListMenuItem>) -> Unit)?) {
    this.stackCallback = callback
  }

  fun setItems(newItems: List<FloatingListMenuItem>) {
    require(newItems.isNotEmpty()) { "Items cannot be empty!" }
    checkItemHeaders(newItems)

    this.menuItems.clear()
    this.menuItems.addAll(newItems)

    rebuild(menuItems)
  }

  fun onDestroy() {
    this.itemClickListener = null
    this.stackCallback = null
    epoxyRecyclerView.clear()
  }

  private fun rebuild(items: List<FloatingListMenuItem>) {
    epoxyRecyclerView.withModels {
      items.forEachIndexed { index, item ->
        if (item.visible) {
          when (item) {
            is HeaderFloatingListMenuItem -> {
              epoxyHeaderListMenuRow {
                id("epoxy_header_floating_list_menu_item_row_${item.key.hashCode()}")
                title(item.name)
              }
            }
            is CheckableFloatingListMenuItem -> {
              if (item.groupId == null) {
                epoxyCheckableFloatingListMenuRow {
                  id("epoxy_checkable_floating_list_menu_row_${item.key.hashCode()}")
                  title(item.name)
                  settingEnabled(item.enabled)
                  checked(item.isCurrentlySelected)

                  callback {
                    itemClickListener?.invoke(item)
                  }
                }
              } else {
                epoxyGroupableFloatingListMenuRow {
                  id("epoxy_groupable_floating_list_menu_row_${item.key.hashCode()}")
                  title(item.name)
                  settingEnabled(item.enabled)
                  checked(item.isCurrentlySelected)

                  callback {
                    itemClickListener?.invoke(item)
                  }
                }
              }
            }
            is FloatingListMenuItem -> {
              epoxyFloatingListMenuRow {
                id("epoxy_floating_list_menu_row_${item.key.hashCode()}")
                title(item.name)
                settingEnabled(item.enabled)
                hasMoreItems(item.more.isNotEmpty())

                callback {
                  if (item.more.isNotEmpty()) {
                    stackCallback?.invoke(item.more)
                  } else {
                    itemClickListener?.invoke(item)
                  }
                }
              }
            }
          }
        }

        if (index != items.lastIndex) {
          epoxyDividerView {
            id("epoxy_divider_${index}")
            updateMargins(MARGINS)
          }
        }
      }
    }
  }

  private fun checkItemHeaders(newItems: List<FloatingListMenuItem>) {
    newItems.forEachIndexed { index, item ->
      if (item is HeaderFloatingListMenuItem && index != 0) {
        throw IllegalArgumentException("HeaderFloatingListMenuItem can only be the first item of the list!")
      }

      if (item.more.isNotEmpty()) {
        checkItemHeaders(item.more)
      }
    }
  }

  companion object {
    private val MARGINS = EpoxyDividerView.Margins(
      left = dp(8f),
      right = dp(8f)
    )
  }

}