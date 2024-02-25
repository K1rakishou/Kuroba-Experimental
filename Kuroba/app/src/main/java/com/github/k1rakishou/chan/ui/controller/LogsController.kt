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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalWindowInsets
import com.github.k1rakishou.chan.ui.compose.providers.ProvideEverythingForCompose
import com.github.k1rakishou.chan.ui.compose.verticalScrollbar
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.LogStorage
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.joda.time.Duration
import javax.inject.Inject

class LogsController(context: Context) : Controller(context) {
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private var logsToCopy: String? = null
  private var addInitialDelay = true

  private val forceLogReloadState = mutableIntStateOf(0)

  private val checkStates = mutableMapOf<Int, Boolean>(
    ACTION_SHOW_DEPENDENCY_LOGS to false,
    ACTION_SHOW_VERBOSE_LOGS to false,
    ACTION_SHOW_DEBUG_LOGS to true,
    ACTION_SHOW_WARNING_LOGS to true,
    ACTION_SHOW_ERROR_LOGS to true,
  )

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
        R.string.settings_logs_copy, ToolbarMenuSubItem.ClickCallback { item -> copyLogsClicked(item) }
      )
      .addLogLevelFilters()
      .build()
      .build()

    view = ComposeView(context)
      .also { composeView ->
        composeView.setContent {
          ProvideEverythingForCompose {
            ControllerContent()
          }
        }
      }
  }

  @Composable
  private fun ControllerContent() {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current

    var logsMut by rememberSaveable { mutableStateOf<AnnotatedString?>(null) }
    val logs = logsMut

    val scrollState = rememberScrollState()
    val forceLogReload by forceLogReloadState

    LaunchedEffect(
      key1 = forceLogReload,
      block = {
        if (addInitialDelay) {
          delay(1000)
        }

        logsMut = null
        logsToCopy = null

        val loadedLogs = withContext(Dispatchers.IO) {
          val logLevels = checkStates
            .filter { (_, checked) -> checked }
            .mapNotNull { (checkedLogLevelId, _) ->
              when (checkedLogLevelId) {
                ACTION_SHOW_DEPENDENCY_LOGS -> LogStorage.LogLevel.Dependencies
                ACTION_SHOW_VERBOSE_LOGS -> LogStorage.LogLevel.Verbose
                ACTION_SHOW_DEBUG_LOGS -> LogStorage.LogLevel.Debug
                ACTION_SHOW_WARNING_LOGS -> LogStorage.LogLevel.Warning
                ACTION_SHOW_ERROR_LOGS -> LogStorage.LogLevel.Error
                else -> return@mapNotNull null
              }
            }
            .toTypedArray()

          if (logLevels.isEmpty()) {
            return@withContext null
          }

          val hasOnlyWarningsOrErrors = logLevels.none { logLevel ->
            when (logLevel) {
              LogStorage.LogLevel.Dependencies -> true
              LogStorage.LogLevel.Verbose -> true
              LogStorage.LogLevel.Debug -> true
              LogStorage.LogLevel.Warning -> false
              LogStorage.LogLevel.Error -> false
            }
          }

          val duration = if (hasOnlyWarningsOrErrors) {
            Duration.standardMinutes(10)
          } else {
            Duration.standardMinutes(3)
          }

          return@withContext Logger.selectLogs<AnnotatedString>(
            duration = duration,
            logLevels = logLevels,
            logSortOrder = LogStorage.LogSortOrder.Ascending,
            logFormatter = LogStorage.composeFormatter()
          )
        }

        logsMut = loadedLogs
        logsToCopy = loadedLogs?.text
        addInitialDelay = false

        delay(250)

        scrollState.scrollTo(scrollState.maxValue - 1)
      }
    )


    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .verticalScrollbar(
          thumbColor = chanTheme.accentColorCompose,
          contentPadding = remember(insets) { insets.asPaddingValues() },
          scrollState = scrollState
        )
        .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
      ) {
        if (logs.isNullOrBlank()) {
          val text = if (logs == null) {
            stringResource(id = R.string.crash_report_activity_loading_logs)
          } else {
            stringResource(id = R.string.crash_report_activity_no_logs)
          }

          KurobaComposeText(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp),
            color = chanTheme.textColorSecondaryCompose,
            text = text,
            textAlign = TextAlign.Center
          )
        } else {
          SelectionContainer {
            KurobaComposeText(
              modifier = Modifier
                .fillMaxSize(),
              color = chanTheme.textColorSecondaryCompose,
              text = logs,
              fontSize = 12.sp
            )
          }
        }
      }
    }
  }

  private fun NavigationItem.MenuOverflowBuilder.addLogLevelFilters(): NavigationItem.MenuOverflowBuilder {
    LogStorage.LogLevel.entries.forEach { logLevel ->
      val id = when (logLevel) {
        LogStorage.LogLevel.Dependencies -> ACTION_SHOW_DEPENDENCY_LOGS
        LogStorage.LogLevel.Verbose -> ACTION_SHOW_VERBOSE_LOGS
        LogStorage.LogLevel.Debug -> ACTION_SHOW_DEBUG_LOGS
        LogStorage.LogLevel.Warning -> ACTION_SHOW_WARNING_LOGS
        LogStorage.LogLevel.Error -> ACTION_SHOW_ERROR_LOGS
      }

      val isChecked = checkStates[id] ?: false

      withCheckableSubItem(id, "Show '${logLevel.logLevelName}' logs", true, isChecked) { clickedSubItem ->
        when (clickedSubItem.id) {
          ACTION_SHOW_DEPENDENCY_LOGS -> checkStates[id] = (checkStates[id] ?: false).not()
          ACTION_SHOW_VERBOSE_LOGS -> checkStates[id] = (checkStates[id] ?: false).not()
          ACTION_SHOW_DEBUG_LOGS -> checkStates[id] = (checkStates[id] ?: false).not()
          ACTION_SHOW_WARNING_LOGS -> checkStates[id] = (checkStates[id] ?: false).not()
          ACTION_SHOW_ERROR_LOGS -> checkStates[id] = (checkStates[id] ?: false).not()
          else -> return@withCheckableSubItem
        }

        navigation.findCheckableSubItem(clickedSubItem.id)?.let { subItem ->
          subItem.isChecked = checkStates[subItem.id] ?: false
          forceLogReloadState.intValue += 1
        }
      }
    }

    return this
  }

  private fun copyLogsClicked(item: ToolbarMenuSubItem) {
    if (logsToCopy == null) {
      return
    }

    AndroidUtils.setClipboardContent("Logs", logsToCopy)
    showToast(R.string.settings_logs_copied_to_clipboard)
  }

  companion object {
    private const val TAG = "LogsController"
    private const val ACTION_LOGS_COPY = 1

    private const val ACTION_SHOW_DEPENDENCY_LOGS = 100
    private const val ACTION_SHOW_VERBOSE_LOGS = 101
    private const val ACTION_SHOW_DEBUG_LOGS = 102
    private const val ACTION_SHOW_WARNING_LOGS = 103
    private const val ACTION_SHOW_ERROR_LOGS = 104
  }
}
