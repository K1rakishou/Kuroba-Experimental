/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.ReportManager
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.IOUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class LogsController(context: Context) : Controller(context) {
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var reportManager: ReportManager

  private lateinit var logTextView: ColorizableTextView
  private lateinit var logText: String

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    navigation.setTitle(R.string.settings_logs_screen)
    navigation
      .buildMenu(context)
      .withOverflow(navigationController)
      .withSubItem(
        ACTION_LOGS_COPY,
        R.string.settings_logs_copy, ToolbarMenuSubItem.ClickCallback { item -> copyLogsClicked(item) })
      .build()
      .build()

    val container = ScrollView(context)
    container.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
    container.isVerticalScrollBarEnabled = true
    container.setBackgroundColor(themeEngine.chanTheme.backColor)
    logTextView = ColorizableTextView(context)

    container.addView(
      logTextView,
      ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    )

    view = container

    mainScope.launch {
      val loadingController = LoadingViewController(context, true, getString(R.string.settings_logs_loading_logs))
      presentController(loadingController)

      try {
        val logs = withContext(Dispatchers.IO) {
          buildString(capacity = 65535) {
            val logs = loadLogs()
            if (logs == null) {
              return@buildString
            }

            appendLine(logs)
            appendLine(reportManager.getReportFooter(context))
          }
        }

        if (logs.isNotNullNorBlank()) {
          logText = logs
          logTextView.text = logText
          logTextView.setTextIsSelectable(true)

          container.post {
            container.fullScroll(View.FOCUS_DOWN)
          }
        } else {
          showToast(getString(R.string.settings_logs_loading_logs_error))
        }
      } finally {
        loadingController.stopPresenting()
      }
    }
  }

  private fun copyLogsClicked(item: ToolbarMenuSubItem) {
    AndroidUtils.setClipboardContent("Logs", logText)
    showToast(R.string.settings_logs_copied_to_clipboard)
  }

  companion object {
    private const val TAG = "LogsController"
    private const val DEFAULT_LINES_COUNT = 1500
    private const val ACTION_LOGS_COPY = 1

    fun loadLogs(): String? {
      val logMpv = ChanSettings.showMpvInternalLogs.get()

      val process = try {
        ProcessBuilder().command(
          "logcat",
          "-v",
          "tag",
          "-t",
          DEFAULT_LINES_COUNT.toString(),
          "StrictMode:S"
        ).start()
      } catch (e: IOException) {
        Logger.e(TAG, "Error starting logcat", e)
        return null
      }

      val outputStream = process.inputStream

      // This filters our log output to just stuff we care about in-app
      // (and if a crash happens, the uncaught handler gets it and this will still allow it through)
      val fullLogsString = StringBuilder(65535)
      val lineTag = "${AndroidUtils.getApplicationLabel()} | "

      for (line in IOUtils.readString(outputStream).split("\n").toTypedArray()) {
        if (line.contains(lineTag, ignoreCase = true)) {
          fullLogsString.appendLine(line)
        } else if (logMpv && line.contains("mpv", ignoreCase = true)) {
          fullLogsString.appendLine(line)
        }
      }

      return fullLogsString.toString()
    }
  }
}