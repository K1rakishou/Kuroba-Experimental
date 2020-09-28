package com.github.k1rakishou.chan.utils

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.AbsListView
import android.widget.EdgeEffect
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import java.lang.reflect.Field


object ViewUtils {
  private const val TAG = "ViewUtils"

  fun EditText.setEditTextCursorColor(themeEngine: ThemeEngine) {
    try {
      if (AndroidUtils.isAndroid10()) {
        textCursorDrawable?.mutate()?.let { cursor ->
          DrawableCompat.setTint(cursor, themeEngine.chanTheme.accentColor)
          textCursorDrawable = cursor
        }

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
      DrawableCompat.setTint(drawable!!, themeEngine.chanTheme.accentColor)

      // Set the drawables
      if (Build.VERSION.SDK_INT >= 28) { //set differently in Android P (API 28)
        field = editor.javaClass.getDeclaredField("mDrawableForCursor")
        field.isAccessible = true
        field[editor] = drawable
      } else {
        val drawables = arrayOf<Drawable?>(drawable, drawable)
        field = editor.javaClass.getDeclaredField("mCursorDrawable")
        field.isAccessible = true
        field[editor] = drawables
      }

    } catch (error: Exception) {
      Logger.e(TAG, "setEditTextCursorColor() failure", error)
    }
  }

  fun TextView.setHandlesColors(themeEngine: ThemeEngine) {
    try {
      if (AndroidUtils.isAndroid10()) {
        textSelectHandle?.mutate()?.let { handle ->
          DrawableCompat.setTint(handle, themeEngine.chanTheme.accentColor)
          setTextSelectHandle(handle)
        }

        textSelectHandleLeft?.mutate()?.let { handle ->
          DrawableCompat.setTint(handle, themeEngine.chanTheme.accentColor)
          setTextSelectHandleLeft(handle)
        }

        textSelectHandleRight?.mutate()?.let { handle ->
          DrawableCompat.setTint(handle, themeEngine.chanTheme.accentColor)
          setTextSelectHandleRight(handle)
        }

        return
      }

      val editorField = TextView::class.java.getDeclaredField("mEditor")
      if (!editorField.isAccessible) {
        editorField.isAccessible = true
      }

      val editor = editorField[this]
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
          DrawableCompat.setTint(drawable, themeEngine.chanTheme.accentColor)

          handleField[editor] = drawable
        }
      }
    } catch (error: Exception) {
      Logger.e(TAG, "setHandlesColors() failure", error)
    }
  }

  @SuppressLint("UseCompatLoadingForDrawables")
  fun AbsListView.changeEdgeEffect(themeEngine: ThemeEngine) {
    if (AndroidUtils.isAndroid10()) {
      bottomEdgeEffectColor = themeEngine.chanTheme.accentColor
      topEdgeEffectColor = themeEngine.chanTheme.accentColor
      return
    }

    val edgeEffectTop = EdgeEffect(context)
    edgeEffectTop.color = themeEngine.chanTheme.accentColor
    val edgeEffectBottom = EdgeEffect(context)
    edgeEffectBottom.color = themeEngine.chanTheme.accentColor

    try {
      val f1: Field = AbsListView::class.java.getDeclaredField("mEdgeGlowTop")
      f1.isAccessible = true
      f1.set(this, edgeEffectTop)

      val f2: Field = AbsListView::class.java.getDeclaredField("mEdgeGlowBottom")
      f2.isAccessible = true
      f2.set(this, edgeEffectBottom)
    } catch (error: Exception) {
      Logger.e(TAG, "changeEdgeEffect() failure", error)
    }
  }

}