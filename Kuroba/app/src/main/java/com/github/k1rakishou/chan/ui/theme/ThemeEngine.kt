package com.github.k1rakishou.chan.ui.theme

import android.app.Activity
import android.app.ActivityManager.TaskDescription
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.widget.IColorizableWidget
import com.github.k1rakishou.chan.utils.AndroidUtils

open class ThemeEngine() {
  private val listeners = hashSetOf<ThemeChangesListener>()
  private val attributeCache = AttributeCache()

  // TODO(KurobaEx):
  open var chanTheme: ChanTheme = MockChanTheme()

  fun addListener(listener: ThemeChangesListener) {
    listeners += listener
  }

  fun removeListener(listener: ThemeChangesListener) {
    listeners -= listener
  }

  fun updateTheme(newTheme: ChanTheme, rootView: ViewGroup) {
    if (chanTheme == newTheme) {
      return
    }

    chanTheme = newTheme
    updateViews(rootView)

    listeners.forEach { listener -> listener.onThemeChanged() }
  }

  fun updateViews(rootView: ViewGroup) {
    updateViewsInternal(rootView)
  }

  fun preloadAttributeResource(context: Context, @AttrRes attrId: Int) {
    attributeCache.preloadAttribute(context, attrId)
  }

  fun getAttributeResource(@AttrRes attrId: Int): Int {
    return attributeCache.getAttribute(attrId)
  }

  fun tintDrawable(drawable: Drawable): Drawable? {
    val drawableMutable = DrawableCompat.wrap(drawable).mutate()
    if (chanTheme.isLightTheme) {
      DrawableCompat.setTint(drawableMutable, LIGHT_THEME_DRAWABLE_TINT)
    } else {
      DrawableCompat.setTint(drawableMutable, DARK_THEME_DRAWABLE_TINT)
    }

    return drawableMutable
  }

  fun tintDrawable(context: Context, @DrawableRes drawableId: Int): Drawable? {
    val drawable = ContextCompat.getDrawable(context, drawableId)
      ?: throw IllegalArgumentException("Couldn't find drawable with drawableId: $drawableId")

    val drawableMutable = DrawableCompat.wrap(drawable).mutate()
    if (chanTheme.isLightTheme) {
      DrawableCompat.setTint(drawableMutable, LIGHT_THEME_DRAWABLE_TINT)
    } else {
      DrawableCompat.setTint(drawableMutable, DARK_THEME_DRAWABLE_TINT)
    }

    return drawableMutable
  }

  fun setupContext(context: Activity) {
    val taskDescription = if (AndroidUtils.isAndroidP()) {
      TaskDescription(
        null,
        R.drawable.ic_stat_notify,
        chanTheme.secondaryColor
      )
    } else {
      val taskDescriptionBitmap = BitmapFactory.decodeResource(
        context.resources,
        R.drawable.ic_stat_notify
      )

      TaskDescription(
        null,
        taskDescriptionBitmap,
        chanTheme.secondaryColor
      )
    }

    context.setTaskDescription(taskDescription)
  }

  private fun updateViewsInternal(view: View) {
    if (view is IColorizableWidget) {
      (view as IColorizableWidget).applyColors()
    }

    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        updateViewsInternal(view.getChildAt(i))
      }
    }
  }

  interface ThemeChangesListener {
    fun onThemeChanged()
  }

  companion object {
    private val LIGHT_THEME_DRAWABLE_TINT = Color.parseColor("#9E9E9E")
    private val DARK_THEME_DRAWABLE_TINT = Color.parseColor("#EEEEEE")
  }
}