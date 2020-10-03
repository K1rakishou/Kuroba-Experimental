package com.github.k1rakishou.chan.ui.theme

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import com.github.k1rakishou.common.mutableMapWithCap

class AttributeCache {
  private val cache = mutableMapWithCap<Int, Int>(128)

  fun preloadAttribute(context: Context, @AttrRes attrRes: Int) {
    val outValue = TypedValue()
    context.theme.resolveAttribute(
      attrRes,
      outValue,
      true
    )

    cache[attrRes] = outValue.resourceId
  }

  fun getAttribute(@AttrRes attrRes: Int): Int {
    return requireNotNull(cache[attrRes]) { "Attribute resource with id ($attrRes) not found in the cache!" }
  }

}