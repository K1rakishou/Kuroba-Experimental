package com.github.adamantcheese.chan.ui.view.floating_menu

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.ui.epoxy.epoxyDividerView

class FloatingListMenu @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val recycler: EpoxyRecyclerView
  private val menuItems = mutableListOf<FloatingListMenuItem>()

  private var itemClickListener: ((item: FloatingListMenuItem) -> Unit)? = null

  init {
    inflate(context, R.layout.floating_list_menu, this)

    recycler = findViewById(R.id.floating_list_menu_recycler)
  }

  fun setClickListener(listener: ((item: FloatingListMenuItem) -> Unit)?) {
    this.itemClickListener = listener
  }

  fun setItems(newItems: List<FloatingListMenuItem>) {
    require(newItems.isNotEmpty()) { "Items cannot be empty!" }

    this.menuItems.clear()
    this.menuItems.addAll(newItems)

    rebuild(menuItems)
  }

  private fun rebuild(items: List<FloatingListMenuItem>) {
    recycler.withModels {
      items.forEachIndexed { index, item ->
        val itemName = if (item.isCurrentlySelected) {
          item.name + "  " + CHECKMARK_SYMBOL
        } else {
          item.name
        }

        epoxyFloatingListMenuRow {
          id("epoxy_floating_list_menu_row_${item.key.hashCode()}")
          title(itemName)

          if (item.enabled) {
            settingEnabled(true)

            callback {
              if (item.more.isNotEmpty()) {
                rebuild(item.more)
              } else {
                itemClickListener?.invoke(item)
              }
            }
          } else {
            settingEnabled(false)
            callback(null)
          }
        }

        if (index != items.lastIndex) {
          epoxyDividerView {
            id("epoxy_divider_${index}")
          }
        }
      }
    }
  }

  data class FloatingListMenuItem @JvmOverloads constructor(
    val key: Any,
    val name: String,
    val value: Any? = null,
    val enabled: Boolean = true,
    val isCurrentlySelected: Boolean = false,
    val more: MutableList<FloatingListMenuItem> = mutableListOf()
  )

  companion object {
    private const val CHECKMARK_SYMBOL = "✓"
  }
}