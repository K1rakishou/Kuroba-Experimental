package com.github.k1rakishou.chan.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.module.activity.ActivityModule
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.ReportManager
import com.github.k1rakishou.chan.core.repository.ImportExportRepository
import com.github.k1rakishou.chan.features.settings.screens.delegate.ExportBackupOptions
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.verticalScrollbar
import com.github.k1rakishou.chan.ui.compose.InsetsAwareBox
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCollapsableContent
import com.github.k1rakishou.chan.ui.compose.KurobaComposeDivider
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextButton
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.controller.LogsController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openLink
import com.github.k1rakishou.chan.utils.FullScreenUtils.setupEdgeToEdge
import com.github.k1rakishou.chan.utils.FullScreenUtils.setupStatusAndNavBarColors
import com.github.k1rakishou.common.AndroidUtils.setClipboardContent
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.callback.FSAFActivityCallbacks
import com.github.k1rakishou.fsaf.callback.FileChooserCallback
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import java.io.IOException
import javax.inject.Inject

class CrashReportActivity : AppCompatActivity(), FSAFActivityCallbacks {
  @Inject
  lateinit var themeEngineLazy: Lazy<ThemeEngine>
  @Inject
  lateinit var globalWindowInsetsManagerLazy: Lazy<GlobalWindowInsetsManager>
  @Inject
  lateinit var appRestarterLazy: Lazy<AppRestarter>
  @Inject
  lateinit var reportManagerLazy: Lazy<ReportManager>
  @Inject
  lateinit var importExportRepositoryLazy: Lazy<ImportExportRepository>
  @Inject
  lateinit var fileChooserLazy: Lazy<FileChooser>
  @Inject
  lateinit var fileManagerLazy: Lazy<FileManager>

  private val themeEngine: ThemeEngine
    get() = themeEngineLazy.get()
  private val globalWindowInsetsManager: GlobalWindowInsetsManager
    get() = globalWindowInsetsManagerLazy.get()
  private val appRestarter: AppRestarter
    get() = appRestarterLazy.get()
  private val reportManager: ReportManager
    get() = reportManagerLazy.get()
  private val importExportRepository: ImportExportRepository
    get() = importExportRepositoryLazy.get()
  private val fileChooser: FileChooser
    get() = fileChooserLazy.get()
  private val fileManager: FileManager
    get() = fileManagerLazy.get()

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

    fileChooser.setCallbacks(this)
    globalWindowInsetsManager.listenForWindowInsetsChanges(window, null)
    appRestarter.attachActivity(this)

    Logger.e(TAG, "Got new exception: ${className}")

    window.setupEdgeToEdge()
    window.setupStatusAndNavBarColors(themeEngine.chanTheme)

    setContent {
      ProvideChanTheme(themeEngine, globalWindowInsetsManager) {
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

    if (::globalWindowInsetsManagerLazy.isInitialized) {
      globalWindowInsetsManager.stopListeningForWindowInsetsChanges(window)
    }

    if (::appRestarterLazy.isInitialized) {
      appRestarter.detachActivity(this)
    }

    if (::fileChooserLazy.isInitialized) {
      fileChooser.removeCallbacks()
    }
  }

  override fun fsafStartActivityForResult(intent: Intent, requestCode: Int) {
    startActivityForResult(intent, requestCode)
  }

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (fileChooser.onActivityResult(requestCode, resultCode, data)) {
      return
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

    var blockButtons by rememberSaveable { mutableStateOf(false) }

    var crashMessageSectionCollapsed by rememberSaveable { mutableStateOf(false) }
    var stacktraceSectionCollapsed by rememberSaveable { mutableStateOf(true) }
    var crashLogsSectionCollapsed by rememberSaveable { mutableStateOf(true) }
    var additionalInfoSectionCollapsed by rememberSaveable { mutableStateOf(true) }

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

        KurobaComposeCollapsableContent(
          title = stringResource(id = R.string.crash_report_activity_crash_message_section),
          collapsed = crashMessageSectionCollapsed,
          onCollapsedStateChanged = { nowCollapsed -> crashMessageSectionCollapsed = nowCollapsed }
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

        KurobaComposeCollapsableContent(
          title = stringResource(id = R.string.crash_report_activity_crash_stacktrace_section),
          collapsed = stacktraceSectionCollapsed,
          onCollapsedStateChanged = { nowCollapsed -> stacktraceSectionCollapsed = nowCollapsed }
        ) {
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

        KurobaComposeCollapsableContent(
          title = stringResource(id = R.string.crash_report_activity_crash_logs_section),
          collapsed = crashLogsSectionCollapsed,
          onCollapsedStateChanged = { nowCollapsed -> crashLogsSectionCollapsed = nowCollapsed }
        ) {
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

        KurobaComposeCollapsableContent(
          title = stringResource(id = R.string.crash_report_activity_additional_info_section),
          collapsed = additionalInfoSectionCollapsed,
          onCollapsedStateChanged = { nowCollapsed -> additionalInfoSectionCollapsed = nowCollapsed }
        ) {
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
            enabled = !blockButtons,
            text = stringResource(id = R.string.crash_report_activity_import_backup),
            onClick = {
              coroutineScope.launch {
                blockButtons = true
                try {
                  importFromBackup(context)
                } finally {
                  blockButtons = false
                }
              }
            }
          )

          Spacer(modifier = Modifier.height(16.dp))

          KurobaComposeTextButton(
            modifier = Modifier.wrapContentWidth(),
            enabled = !blockButtons,
            text = stringResource(id = R.string.crash_report_activity_export_backup),
            onClick = {
              coroutineScope.launch {
                blockButtons = true
                try {
                  exportToBackup(context)
                } finally {
                  blockButtons = false
                }
              }
            }
          )

          Spacer(modifier = Modifier.height(8.dp))

          KurobaComposeDivider(
            modifier = Modifier
              .weight(1f)
              .height(1.dp)
          )

          Spacer(modifier = Modifier.height(8.dp))

          KurobaComposeTextButton(
            modifier = Modifier.wrapContentWidth(),
            enabled = !blockButtons,
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
            enabled = !blockButtons,
            text = stringResource(id = R.string.crash_report_activity_open_issue_tracker),
            onClick = {
              openLink(ISSUES_LINK)
            }
          )

          Spacer(modifier = Modifier.height(16.dp))

          KurobaComposeTextButton(
            modifier = Modifier.wrapContentWidth(),
            enabled = !blockButtons,
            text = stringResource(id = R.string.crash_report_activity_restart_the_app),
            onClick = { appRestarter.restart() }
          )
        }

        Spacer(modifier = Modifier.height(4.dp))
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

  private suspend fun importFromBackup(context: Context) {
    val result = suspendCancellableCoroutine<Result<Uri>> { cancellableContinuation ->
      fileChooser.openChooseFileDialog(
        object : FileChooserCallback() {
          override fun onResult(uri: Uri) {
            cancellableContinuation.resumeValueSafe(Result.success(uri))
          }

          override fun onCancel(reason: String) {
            cancellableContinuation.resumeValueSafe(Result.failure(IOException(reason)))
          }
        }
      )
    }

    if (result.isFailure) {
      result.exceptionOrNull()?.let { error ->
        AppModuleAndroidUtils.showToast(context, error.errorMessageOrClassName(), Toast.LENGTH_LONG)
      }

      return
    }

    val externalFile = fileManager.fromUri(result.getOrThrow())
    if (externalFile == null) {
      AppModuleAndroidUtils.showToast(context, "Failed to convert url to external file", Toast.LENGTH_LONG)
      return
    }

    importExportRepository
      .importFrom(externalFile)
      .peekError { error -> AppModuleAndroidUtils.showToast(context, error.errorMessageOrClassName(), Toast.LENGTH_LONG) }
      .peekValue { AppModuleAndroidUtils.showToast(context, getString(R.string.done), Toast.LENGTH_LONG) }
      .ignore()
  }

  private suspend fun exportToBackup(context: Context) {
    val dateString = BACKUP_DATE_FORMAT.print(DateTime.now())
    val exportFileName = "KurobaEx_v${BuildConfig.VERSION_CODE}_($dateString)_backup.zip"

    val result = suspendCancellableCoroutine<Result<Uri>> { cancellableContinuation ->
      fileChooser.openCreateFileDialog(
        exportFileName,
        object : FileCreateCallback() {
          override fun onResult(uri: Uri) {
            cancellableContinuation.resumeValueSafe(Result.success(uri))
          }

          override fun onCancel(reason: String) {
            cancellableContinuation.resumeValueSafe(Result.failure(IOException(reason)))
          }
        }
      )
    }

    if (result.isFailure) {
      result.exceptionOrNull()?.let { error ->
        AppModuleAndroidUtils.showToast(context, error.errorMessageOrClassName(), Toast.LENGTH_LONG)
      }

      return
    }

    val externalFile = fileManager.fromUri(result.getOrThrow())
    if (externalFile == null) {
      AppModuleAndroidUtils.showToast(context, "Failed to convert url to external file", Toast.LENGTH_LONG)
      return
    }

    importExportRepository
      .exportTo(externalFile, ExportBackupOptions(exportDownloadedThreadsMedia = false))
      .peekError { error -> AppModuleAndroidUtils.showToast(context, error.errorMessageOrClassName(), Toast.LENGTH_LONG) }
      .peekValue { AppModuleAndroidUtils.showToast(context, getString(R.string.done), Toast.LENGTH_LONG) }
      .ignore()
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

    private val BACKUP_DATE_FORMAT = DateTimeFormatterBuilder()
      .append(ISODateTimeFormat.date())
      .toFormatter()
  }

}