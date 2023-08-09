package com.github.k1rakishou.chan.activity

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.module.activity.ActivityModule
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.ReportManager
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.verticalScrollbar
import com.github.k1rakishou.chan.ui.compose.InsetsAwareBox
import com.github.k1rakishou.chan.ui.compose.KurobaComposeDivider
import com.github.k1rakishou.chan.ui.compose.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextButton
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.ui.controller.LogsController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openLink
import com.github.k1rakishou.chan.utils.FullScreenUtils.setupEdgeToEdge
import com.github.k1rakishou.chan.utils.FullScreenUtils.setupStatusAndNavBarColors
import com.github.k1rakishou.common.AndroidUtils.setClipboardContent
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CrashReportActivity : AppCompatActivity() {
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var appRestarter: AppRestarter
  @Inject
  lateinit var reportManager: ReportManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    Logger.d(TAG, "CrashReportActivity launched")

    val bundle = intent.getBundleExtra(EXCEPTION_BUNDLE_KEY)
    if (bundle == null) {
      Logger.e(TAG, "Bundle is null")

      finish()
      return
    }

    val className = bundle.getString(EXCEPTION_CLASS_NAME_KEY)
    val message = bundle.getString(EXCEPTION_MESSAGE_KEY)
    val stacktrace = bundle.getString(EXCEPTION_STACKTRACE_KEY)
    val userAgent = bundle.getString(USER_AGENT_KEY) ?: "No user-agent"
    val appLifetime = bundle.getString(APP_LIFE_TIME_KEY) ?: "-1"

    if (className == null || message == null || stacktrace == null) {
      Logger.e(TAG,
        "Bad bundle params. " +
          "className is null (${className == null}), " +
          "message is null (${message == null}), " +
          "stacktrace is null (${stacktrace == null})"
      )

      finish()
      return
    }

    Chan.getComponent()
      .activityComponentBuilder()
      .activity(this)
      .activityModule(ActivityModule())
      .build()
      .inject(this)

    globalWindowInsetsManager.listenForWindowInsetsChanges(window, null)
    appRestarter.attachActivity(this)

    Logger.e(TAG, "Got new exception: ${className}")

    window.setupEdgeToEdge()
    window.setupStatusAndNavBarColors(themeEngine.chanTheme)

    setContent {
      ProvideChanTheme(themeEngine) {
        val chanTheme = LocalChanTheme.current

        val textSelectionColors = remember(key1 = chanTheme.accentColorCompose) {
          TextSelectionColors(
            handleColor = chanTheme.accentColorCompose,
            backgroundColor = chanTheme.accentColorCompose.copy(alpha = 0.4f)
          )
        }

        CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
          Content(
            className = className,
            message = message,
            stacktrace = stacktrace,
            userAgent = userAgent,
            appLifetime = appLifetime
          )
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    if (::appRestarter.isInitialized) {
      appRestarter.detachActivity(this)
    }
  }

  @Composable
  private fun Content(
    className: String,
    message: String,
    stacktrace: String,
    userAgent: String,
    appLifetime: String
  ) {
    val chanTheme = LocalChanTheme.current
    val context = LocalContext.current
    val insets by globalWindowInsetsManager.currentInsetsCompose

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var logsMut by rememberSaveable { mutableStateOf<String?>(null) }
    val logs = logsMut

    InsetsAwareBox(
      modifier = Modifier
        .fillMaxSize()
        .drawBehind { drawRect(chanTheme.backColorCompose) }
        .padding(horizontal = 4.dp, vertical = 8.dp)
        .verticalScrollbar(
          thumbColor = chanTheme.accentColorCompose,
          contentPadding = insets,
          scrollState = scrollState
        ),
      insets = insets
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentSize(), contentAlignment = Alignment.Center
        ) {
          KurobaComposeText(
            text = stringResource(id = R.string.crash_report_activity_title),
            color = chanTheme.accentColorCompose,
            fontSize = 18.sp
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Collapsable(
          title = stringResource(id = R.string.crash_report_activity_crash_message_section),
          collapsedByDefault = false
        ) {
          val errorMessage = remember(key1 = className, key2 = message) {
            return@remember "Exception: ${className}\nMessage: ${message}"
          }

          SelectionContainer {
            KurobaComposeText(
              modifier = Modifier.fillMaxSize(),
              color = chanTheme.textColorSecondaryCompose,
              text = errorMessage,
              fontSize = 14.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Collapsable(title = stringResource(id = R.string.crash_report_activity_crash_stacktrace_section)) {
          SelectionContainer {
            KurobaComposeText(
              modifier = Modifier.fillMaxSize(),
              color = chanTheme.textColorSecondaryCompose,
              text = stacktrace,
              fontSize = 12.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Collapsable(title = stringResource(id = R.string.crash_report_activity_crash_logs_section)) {
          LaunchedEffect(
            key1 = Unit,
            block = {
              logsMut = withContext(Dispatchers.IO) {
                LogsController.loadLogs()
              }
            }
          )

          if (logs == null) {
            KurobaComposeText(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
              color = chanTheme.textColorSecondaryCompose,
              text = stringResource(id = R.string.crash_report_activity_loading_logs)
            )
          } else {
            SelectionContainer {
              KurobaComposeText(
                modifier = Modifier.fillMaxSize(),
                color = chanTheme.textColorSecondaryCompose,
                text = logs,
                fontSize = 12.sp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Collapsable(title = stringResource(id = R.string.crash_report_activity_additional_info_section)) {
          val footer = remember {
            reportManager.getReportFooter(
              context = this@CrashReportActivity,
              appRunningTime = appLifetime,
              userAgent = userAgent
            )
          }

          SelectionContainer {
            KurobaComposeText(
              modifier = Modifier.fillMaxSize(),
              color = chanTheme.textColorSecondaryCompose,
              text = footer,
              fontSize = 12.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Spacer(modifier = Modifier.height(16.dp))

          KurobaComposeTextButton(
            modifier = Modifier.wrapContentWidth(),
            text = stringResource(id = R.string.crash_report_activity_copy_for_github),
            onClick = {
              coroutineScope.launch {
                copyLogsFormattedToClipboard(
                  context = context,
                  className = className,
                  message = message,
                  stacktrace = stacktrace
                )
              }
            }
          )

          Spacer(modifier = Modifier.height(16.dp))

          KurobaComposeTextButton(
            modifier = Modifier.wrapContentWidth(),
            text = stringResource(id = R.string.crash_report_activity_open_issue_tracker),
            onClick = {
              openLink(ISSUES_LINK)
            }
          )

          Spacer(modifier = Modifier.height(16.dp))

          KurobaComposeTextButton(
            modifier = Modifier.wrapContentWidth(),
            text = stringResource(id = R.string.crash_report_activity_restart_the_app),
            onClick = { appRestarter.restart() }
          )
        }

        Spacer(modifier = Modifier.height(4.dp))
      }
    }
  }

  @Composable
  private fun Collapsable(
    title: String,
    collapsedByDefault: Boolean = true,
    content: @Composable () -> Unit
  ) {
    var collapsed by rememberSaveable { mutableStateOf(collapsedByDefault) }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .animateContentSize()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .kurobaClickable(bounded = true, onClick = { collapsed = !collapsed }),
        verticalAlignment = Alignment.CenterVertically
      ) {
        KurobaComposeIcon(
          modifier = Modifier
            .graphicsLayer { rotationZ = if (collapsed) 0f else 90f },
          drawableId = R.drawable.ic_baseline_arrow_right_24
        )

        Spacer(modifier = Modifier.width(4.dp))

        KurobaComposeText(text = title)

        Spacer(modifier = Modifier.width(4.dp))

        KurobaComposeDivider(
          modifier = Modifier
            .weight(1f)
            .height(1.dp)
        )
      }

      if (!collapsed) {
        Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
          content()
        }
      }
    }
  }

  private suspend fun copyLogsFormattedToClipboard(
    context: Context,
    className: String,
    message: String,
    stacktrace: String
  ) {
    val logs = withContext(Dispatchers.IO) { LogsController.loadLogs() }
    val reportFooter = reportManager.getReportFooter(context)

    val resultString = buildString(65535) {
      appendLine("Exception: ${className}")
      appendLine("Message: ${message}")
      appendLine()

      appendLine("Stacktrace")
      appendLine("```")
      appendLine(stacktrace)
      appendLine("```")
      appendLine()

      if (logs.isNotNullNorEmpty()) {
        appendLine("Logs")
        appendLine("```")
        appendLine(logs)
        appendLine("```")
      }

      appendLine("Additional information")
      appendLine("```")
      appendLine(reportFooter)
      appendLine("```")
    }

    setClipboardContent("Crash report", resultString)

    Toast.makeText(
      this,
      resources.getString(R.string.crash_report_activity_copied_to_clipboard),
      Toast.LENGTH_SHORT
    ).show()
  }

  companion object {
    private const val TAG = "CrashReportActivity"

    const val EXCEPTION_BUNDLE_KEY = "exception_bundle"
    const val EXCEPTION_CLASS_NAME_KEY = "exception_class_name"
    const val EXCEPTION_MESSAGE_KEY = "exception_message"
    const val EXCEPTION_STACKTRACE_KEY = "exception_stacktrace"
    const val USER_AGENT_KEY = "user_agent"
    const val APP_LIFE_TIME_KEY = "app_life_time"

    private const val ISSUES_LINK = "https://github.com/K1rakishou/Kuroba-Experimental/issues"
  }

}