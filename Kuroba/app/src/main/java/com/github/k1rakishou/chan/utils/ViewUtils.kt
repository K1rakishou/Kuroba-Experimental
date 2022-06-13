package com.github.k1rakishou.chan.utils

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.AbsListView
import android.widget.EdgeEffect
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_themes.ChanTheme
import com.google.android.material.tabs.TabLayout
import java.lang.reflect.Field


object ViewUtils {
  private const val TAG = "ViewUtils"

  @JvmStatic
  fun TextView.setEditTextCursorColor(theme: ChanTheme) {
    if (!ChanSettings.colorizeTextSelectionCursors.get()) {
      return
    }

    val accentColorWithAlpha = ColorUtils.setAlphaComponent(
      theme.accentColor,
      0xb0
    )

    try {
      if (AndroidUtils.isAndroid10()) {
        textCursorDrawable?.mutate()?.let { cursor ->
          DrawableCompat.setTint(cursor, accentColorWithAlpha)
          textCursorDrawable = cursor
        }

        highlightColor = accentColorWithAlpha
        return
      }

      // Get the cursor resource id
      var field = TextView::class.java.getDeclaredField("mCursorDrawableRes")
      field.isAccessible = true
      val drawableResId = field.getInt(this)

      // Get the editor
      field = TextView::class.java.getDeclaredField("mEditor")
      field.isAccessible = true
      val editor = field[this]

      // Get the drawable and set a color filter
      val drawable = ContextCompat.getDrawable(context, drawableResId)
      DrawableCompat.setTint(drawable!!, accentColorWithAlpha)

      // Set the drawables
      if (Build.VERSION.SDK_INT >= 28) {
        //set differently in Android P (API 28)
        field = editor.javaClass.getDeclaredField("mDrawableForCursor")
        field.isAccessible = true
        field[editor] = drawable
      } else {
        val drawables = arrayOf<Drawable?>(drawable, drawable)
        field = editor.javaClass.getDeclaredField("mCursorDrawable")
        field.isAccessible = true
        field[editor] = drawables
      }

    } catch (ignored: Exception) {
    }
  }

  @JvmStatic
  fun TextView.setHandlesColors(theme: ChanTheme) {
    if (!ChanSettings.colorizeTextSelectionCursors.get()) {
      return
    }

    val accentColorWithAlpha = ColorUtils.setAlphaComponent(
      theme.accentColor,
      0xb0
    )

    try {
      if (AndroidUtils.isAndroid10()) {
        textSelectHandle?.mutate()?.let { handle ->
          DrawableCompat.setTint(handle, accentColorWithAlpha)
          setTextSelectHandle(handle)
        }

        textSelectHandleLeft?.mutate()?.let { handle ->
          DrawableCompat.setTint(handle, accentColorWithAlpha)
          setTextSelectHandleLeft(handle)
        }

        textSelectHandleRight?.mutate()?.let { handle ->
          DrawableCompat.setTint(handle, accentColorWithAlpha)
          setTextSelectHandleRight(handle)
        }

        return
      }

      val editorField = TextView::class.java.getDeclaredField("mEditor")
      if (!editorField.isAccessible) {
        editorField.isAccessible = true
      }

      val editor = editorField.get(this)
      val editorClass: Class<*> = editor.javaClass
      val handleNames = arrayOf("mSelectHandleLeft", "mSelectHandleRight", "mSelectHandleCenter")
      val resNames = arrayOf("mTextSelectHandleLeftRes", "mTextSelectHandleRightRes", "mTextSelectHandleRes")

      for (i in handleNames.indices) {
        val handleField = editorClass.getDeclaredField(handleNames[i])
        if (!handleField.isAccessible) {
          handleField.isAccessible = true
        }

        var handleDrawable: Drawable? = handleField[editor] as? Drawable
        if (handleDrawable == null) {
          val resField = TextView::class.java.getDeclaredField(resNames[i])
          if (!resField.isAccessible) {
            resField.isAccessible = true
          }

          val resId = resField.getInt(this)
          handleDrawable = ContextCompat.getDrawable(context, resId)
        }

        if (handleDrawable != null) {
          val drawable = handleDrawable.mutate()
          DrawableCompat.setTint(drawable, accentColorWithAlpha)

          handleField[editor] = drawable
        }
      }
    } catch (ignored: Exception) {
    }
  }

  @SuppressLint("UseCompatLoadingForDrawables")
  fun AbsListView.changeEdgeEffect(theme: ChanTheme) {
    val color = theme.accentColor

    if (AndroidUtils.isAndroid10()) {
      bottomEdgeEffectColor = color
      topEdgeEffectColor = color
      return
    }

    val edgeEffectTop = EdgeEffect(context)
    edgeEffectTop.color = color
    val edgeEffectBottom = EdgeEffect(context)
    edgeEffectBottom.color = color

    try {
      val f1: Field = AbsListView::class.java.getDeclaredField("mEdgeGlowTop")
      f1.isAccessible = true
      f1.set(this, edgeEffectTop)

      val f2: Field = AbsListView::class.java.getDeclaredField("mEdgeGlowBottom")
      f2.isAccessible = true
      f2.set(this, edgeEffectBottom)
    } catch (ignored: Exception) {
    }
  }

  fun ScrollView.changeEdgeEffect(theme: ChanTheme) {
    val color = theme.accentColor

    if (AndroidUtils.isAndroid10()) {
      setEdgeEffectColor(theme.accentColor)
      return
    }

    val edgeEffectTop = EdgeEffect(context)
    edgeEffectTop.color = color
    val edgeEffectBottom = EdgeEffect(context)
    edgeEffectBottom.color = color

    try {
      val f1: Field = ScrollView::class.java.getDeclaredField("mEdgeGlowTop")
      f1.isAccessible = true
      f1.set(this, edgeEffectTop)

      val f2: Field = ScrollView::class.java.getDeclaredField("mEdgeGlowBottom")
      f2.isAccessible = true
      f2.set(this, edgeEffectBottom)
    } catch (ignored: Exception) {
    }
  }

  @JvmStatic
  fun ViewPager.changeEdgeEffect(theme: ChanTheme) {
    val color = theme.accentColor

    val leftEdge = EdgeEffect(context)
    leftEdge.color = color
    val rightEdge = EdgeEffect(context)
    rightEdge.color = color

    try {
      val f1: Field = ViewPager::class.java.getDeclaredField("mLeftEdge")
      f1.isAccessible = true
      f1.set(this, leftEdge)

      val f2: Field = ViewPager::class.java.getDeclaredField("mRightEdge")
      f2.isAccessible = true
      f2.set(this, rightEdge)
    } catch (ignored: Exception) {
    }
  }

  fun ProgressDialog.changeProgressColor(theme: ChanTheme) {
    try {
      val f1: Field = ProgressDialog::class.java.getDeclaredField("mProgressNumber")
      f1.isAccessible = true
      (f1.get(this) as? TextView)?.let { progressNumber -> progressNumber.setTextColor(theme.textColorSecondary) }

      val f2: Field = ProgressDialog::class.java.getDeclaredField("mProgressPercent")
      f2.isAccessible = true
      (f2.get(this) as? TextView)?.let { progressPercent -> progressPercent.setTextColor(theme.textColorSecondary) }
    } catch (ignored: Exception) {
    }
  }

  fun TabLayout.updateTabLayoutFontSize(textSizeInPixel: Int) {
    try {
      val tabTextSizeField = TabLayout::class.java.getDeclaredField("tabTextSize")
      tabTextSizeField.isAccessible = true
      tabTextSizeField.set(this, textSizeInPixel)
    } catch (ignored: Exception) {
    }
  }

  fun RecyclerView.hackMaxFlingVelocity() {
    try {
      val field: Field = this.javaClass.getDeclaredField("mMaxFlingVelocity")
      field.isAccessible = true
      field[this] = ViewConfiguration.getMaximumFlingVelocity() * 4
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  fun View.emulateMotionEvent(downTime: Long, action: Int, x: Float, y: Float) {
    val motionEvent = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
    onTouchEvent(motionEvent)
    motionEvent.recycle()
  }

}