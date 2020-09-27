package com.github.k1rakishou.chan.ui.layout.crashlogs

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import javax.inject.Inject

@SuppressLint("ViewConstructor")
class ViewFullCrashLogLayout(context: Context, private val crashLog: CrashLog) : FrameLayout(context) {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private var callbacks: ViewFullCrashLogLayoutCallbacks? = null

  private val crashLogText: AppCompatEditText
  private val save: AppCompatButton

  init {
    Chan.inject(this)

    inflate(context, R.layout.layout_view_full_crashlog, this).apply {
      crashLogText = findViewById(R.id.view_full_crashlog_text)
      save = findViewById(R.id.view_full_crashlog_save)

      save.setTextColor(themeEngine.chanTheme.textPrimaryColor)

      crashLogText.setText(crashLog.file.readText())
      crashLogText.setTextColor(themeEngine.chanTheme.textPrimaryColor)

      save.setOnClickListener {
        val text = crashLogText.text.toString()

        if (text.isNotEmpty()) {
          crashLog.file.writeText(text)
        }

        callbacks?.onFinished()
      }
    }
  }

  fun onCreate(callbacks: ViewFullCrashLogLayoutCallbacks) {
    this.callbacks = callbacks
  }

  fun onDestroy() {
    this.callbacks = null
  }

  interface ViewFullCrashLogLayoutCallbacks {
    fun onFinished()
  }
}