package com.github.k1rakishou.core_themes

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes

class AttributeCache {
  private val cache = mutableMapOf<Int, Int>()

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