package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuOverflowItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarOverflowMenuBuilder
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.lazylist.scrollbar
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalWindowInsets
import com.github.k1rakishou.chan.ui.controller.base.Controller
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

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.Id(R.string.settings_logs_screen)
      ),
      menuBuilder = {
        withOverflowMenu {
          withOverflowMenuItem(
            id = ACTION_LOGS_COPY,
            stringId = R.string.settings_logs_copy,
            onClick = { item -> copyLogsClicked(item) }
          )

          addLogLevels()
        }
      }
    )

    view = ComposeView(context)
      .also { composeView ->
        composeView.setContent {
          ComposeEntrypoint {
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

    val toolbarHeight by globalUiStateHolder.toolbar.toolbarHeight.collectAsState()
    val bottomPanelHeight by globalUiStateHolder.bottomPanel.bottomPanelHeight.collectAsState()

    val contentPadding = remember(insets, toolbarHeight, bottomPanelHeight) {
      insets.asPaddingValues()

      return@remember PaddingValues(
        start = insets.left,
        end = insets.right,
        top = maxOf(insets.top, toolbarHeight),
        bottom = maxOf(insets.bottom, bottomPanelHeight)
      )
    }

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
        .scrollbar(
          contentPadding = contentPadding,
          scrollState = scrollState
        )
        .padding(contentPadding)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(horizontal = 4.dp, vertical = 8.dp)
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
              fontSize = 12.ktu
            )
          }
        }
      }
    }
  }

  private fun copyLogsClicked(item: ToolbarMenuOverflowItem) {
    if (logsToCopy == null) {
      return
    }

    AndroidUtils.setClipboardContent("Logs", logsToCopy)
    showToast(R.string.settings_logs_copied_to_clipboard)
  }

  private fun ToolbarOverflowMenuBuilder.addLogLevels() {
    LogStorage.LogLevel.entries.forEach { logLevel ->
      val id = when (logLevel) {
        LogStorage.LogLevel.Dependencies -> ACTION_SHOW_DEPENDENCY_LOGS
        LogStorage.LogLevel.Verbose -> ACTION_SHOW_VERBOSE_LOGS
        LogStorage.LogLevel.Debug -> ACTION_SHOW_DEBUG_LOGS
        LogStorage.LogLevel.Warning -> ACTION_SHOW_WARNING_LOGS
        LogStorage.LogLevel.Error -> ACTION_SHOW_ERROR_LOGS
      }

      val isChecked = checkStates[id] ?: false

      withCheckableOverflowMenuItem(
        id = id,
        text = appResources.string(R.string.settings_logs_show_for_level, logLevel.logLevelName),
        visible = true,
        checked = isChecked,
        onClick = { clickedSubItem ->
          when (clickedSubItem.id) {
            ACTION_SHOW_DEPENDENCY_LOGS -> checkStates[id] = (checkStates[id] ?: false).not()
            ACTION_SHOW_VERBOSE_LOGS -> checkStates[id] = (checkStates[id] ?: false).not()
            ACTION_SHOW_DEBUG_LOGS -> checkStates[id] = (checkStates[id] ?: false).not()
            ACTION_SHOW_WARNING_LOGS -> checkStates[id] = (checkStates[id] ?: false).not()
            ACTION_SHOW_ERROR_LOGS -> checkStates[id] = (checkStates[id] ?: false).not()
            else -> return@withCheckableOverflowMenuItem
          }

          toolbarState.findCheckableOverflowItem(clickedSubItem.id)?.let { subItem ->
            subItem.updateChecked(checkStates[subItem.id] ?: false)
            forceLogReloadState.intValue += 1
          }
        }
      )
    }
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
