package com.github.k1rakishou.chan.ui.helper.picker

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.concurrency.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.receiver.SelectedFilePickerBroadcastReceiver
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.RequestCodes
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.ModularResult.Companion.error
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.v2.KurobaSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger


class LocalFilePicker(
  kurobaSettings: KurobaSettings,
  appConstants: AppConstants,
  fileManager: FileManager,
  replyManager: ReplyManager,
  private val appScope: CoroutineScope
) : AbstractFilePicker<LocalFilePicker.LocalFilePickerInput>(
  kurobaSettings = kurobaSettings,
  appConstants = appConstants,
  replyManager = replyManager,
  fileManager = fileManager
) {
  private val activeRequests = ConcurrentHashMap<Int, EnqueuedRequest>()
  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)
  private val requestCodeCounter = AtomicInteger(0)
  private val activities = CopyOnWriteArraySet<AppCompatActivity>()

  private val selectedFilePickerBroadcastReceiver = SelectedFilePickerBroadcastReceiver()

  fun onActivityCreated(activity: AppCompatActivity) {
    BackgroundUtils.ensureMainThread()
    activities.add(activity)
  }

  fun onActivityDestroyed(activity: AppCompatActivity) {
    BackgroundUtils.ensureMainThread()
    serializedCoroutineExecutor.cancelChildren()

    activeRequests.values.forEach { enqueuedRequest -> enqueuedRequest.completableDeferred.cancel() }
    activeRequests.clear()

    activities.remove(activity)
  }

  override suspend fun pickFile(filePickerInput: LocalFilePickerInput): ModularResult<PickedFile> {
    BackgroundUtils.ensureMainThread()

    val attachedActivity = activities.firstOrNull()
    if (attachedActivity == null) {
      return error(FilePickerError.ActivityIsNotSet())
    }

    val intents = collectIntents()
    if (intents.isEmpty()) {
      return error(FilePickerError.NoFilePickersFound())
    }

    val completableDeferred = CompletableDeferred<PickedFile>()

    val newRequestCode = requestCodeCounter.getAndIncrement()
    check(!activeRequests.containsKey(newRequestCode)) { "Already contains newRequestCode=$newRequestCode" }

    val newRequest = EnqueuedRequest(
      newRequestCode,
      filePickerInput.replyChanDescriptor,
      completableDeferred
    )
    activeRequests[newRequestCode] = newRequest

    check(intents.isNotEmpty()) { "intents is empty!" }
    runIntentChooser(attachedActivity, intents, newRequest.requestCode)

    return Try { completableDeferred.await() }
      .finally { activeRequests.remove(newRequest.requestCode) }
      .mapError { error ->
        if (error is CancellationException) {
          return@mapError FilePickerError.Canceled()
        }

        return@mapError error
      }
  }

  fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    BackgroundUtils.ensureMainThread()

    serializedCoroutineExecutor.post {
      withContext(Dispatchers.IO) {
        try {
          onActivityResultInternal(requestCode, resultCode, data)
        } catch (error: Throwable) {
          finishWithError(requestCode, FilePickerError.UnknownError(error))
        }
      }
    }
  }

  private suspend fun onActivityResultInternal(requestCode: Int, resultCode: Int, data: Intent?) {
    BackgroundUtils.ensureBackgroundThread()

    val enqueuedRequest = activeRequests[requestCode]
    if (enqueuedRequest == null) {
      return
    }

    val attachedActivity = activities.firstOrNull()
    if (attachedActivity == null) {
      return finishWithError(requestCode, FilePickerError.ActivityIsNotSet())
    }

    if (resultCode != Activity.RESULT_OK) {
      if (resultCode == Activity.RESULT_CANCELED) {
        finishWithError(requestCode, FilePickerError.Canceled())
        return
      }

      finishWithError(requestCode, FilePickerError.BadResultCode(resultCode))
      return
    }

    if (data == null) {
      finishWithError(requestCode, FilePickerError.NoDataReturned())
      return
    }

    val uris = extractUris(data)
    if (uris.isEmpty()) {
      finishWithError(requestCode, FilePickerError.FailedToExtractUri())
      return
    }

    val copyResults = uris.map { uri ->
      copyExternalFileToReplyFileStorage(
        appContext = attachedActivity,
        externalFileUri = uri,
        addedOn = System.currentTimeMillis()
      )
    }

    val allFailed = copyResults.all { result -> result is ModularResult.Error }
    if (allFailed) {
      val firstErrorResult = copyResults.first { result -> result is ModularResult.Error }
      finishWithError(requestCode, FilePickerError.UnknownError(firstErrorResult.unwrapError()))
      return
    }

    val pickedFiles = copyResults
      .mapNotNull { result -> result.valueOrNull() }

    finishWithResult(requestCode, PickedFile.Result(pickedFiles))
  }

  private fun finishWithResult(requestCode: Int, value: PickedFile.Result) {
    Logger.d(TAG, "finishWithResult success, requestCode=$requestCode, " +
      "value=${value.replyFiles.first().shortState()}")
    activeRequests[requestCode]?.completableDeferred?.complete(value)
  }

  private fun finishWithError(requestCode: Int, error: FilePickerError) {
    Logger.d(TAG, "finishWithError success, requestCode=$requestCode, " +
        "error=${error.errorMessageOrClassName()}")
    activeRequests[requestCode]?.completableDeferred?.complete(PickedFile.Failure(error))
  }

  private fun extractUris(intent: Intent): List<Uri> {
    if (intent.data != null) {
      return listOf(intent.data!!)
    }

    val clipData = intent.clipData
    if (clipData != null && clipData.itemCount > 0) {
      return (0 until clipData.itemCount)
        .map { index -> clipData.getItemAt(index).uri }
    }

    return emptyList()
  }

  private suspend fun collectIntents(): List<Intent> {
    val pm = AndroidUtils.appContext.packageManager
    val intent = Intent(Intent.ACTION_GET_CONTENT)
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.type = "*/*"

    val resolveInfos = pm.queryIntentActivities(intent, 0)
    val intents: MutableList<Intent> = ArrayList(resolveInfos.size)

    val lastRememberedFilePicker = kurobaSettings.internal.lastRememberedFilePicker.read()
    if (lastRememberedFilePicker.isNotEmpty()) {
      val lastRememberedFilePickerInfo = resolveInfos.firstOrNull { resolveInfo ->
        resolveInfo.activityInfo.packageName == lastRememberedFilePicker
      }

      if (lastRememberedFilePickerInfo != null) {
        val newIntent = Intent(Intent.ACTION_GET_CONTENT)
        newIntent.addCategory(Intent.CATEGORY_OPENABLE)
        newIntent.setPackage(lastRememberedFilePickerInfo.activityInfo.packageName)
        newIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        newIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        newIntent.type = "*/*"

        return listOf(newIntent)
      }
    }

    for (info in resolveInfos) {
      val newIntent = Intent(Intent.ACTION_GET_CONTENT)
      newIntent.addCategory(Intent.CATEGORY_OPENABLE)
      newIntent.setPackage(info.activityInfo.packageName)
      newIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      newIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
      newIntent.type = "*/*"

      intents.add(newIntent)
    }

    return intents
  }

  private fun runIntentChooser(activity: AppCompatActivity, intents: List<Intent>, requestCode: Int) {
    check(intents.isNotEmpty()) { "intents is empty!" }

    if (intents.size == 1) {
      activity.startActivityForResult(intents[0], requestCode)
      return
    }

    val chooser = run {
      val receiverIntent = Intent(
        activity,
        SelectedFilePickerBroadcastReceiver::class.java
      )

      val pendingIntent = PendingIntent.getBroadcast(
        activity,
        RequestCodes.LOCAL_FILE_PICKER_LAST_SELECTION_REQUEST_CODE,
        receiverIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
      )

      ContextCompat.registerReceiver(
        activity,
        selectedFilePickerBroadcastReceiver,
        IntentFilter(Intent.ACTION_GET_CONTENT),
        ContextCompat.RECEIVER_NOT_EXPORTED
      )

      Intent.createChooser(
        intents.last(),
        getString(R.string.image_pick_delegate_select_file_picker),
        pendingIntent.intentSender
      )
    }

    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toTypedArray())
    activity.startActivityForResult(chooser, requestCode)
  }

  data class LocalFilePickerInput(
    val notifyListeners: Boolean,
    val replyChanDescriptor: ChanDescriptor
  )

  private data class EnqueuedRequest(
    val requestCode: Int,
    val replyChanDescriptor: ChanDescriptor,
    val completableDeferred: CompletableDeferred<PickedFile>
  )

  companion object {
    private const val TAG = "LocalFilePicker"
  }

}