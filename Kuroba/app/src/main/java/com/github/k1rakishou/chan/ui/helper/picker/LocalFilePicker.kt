package com.github.k1rakishou.chan.ui.helper.picker

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.OpenableColumns
import com.github.k1rakishou.PersistableChanState
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.StartActivity
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.IOUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AndroidUtils.isAndroidL_MR1
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.ModularResult.Companion.error
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference


class LocalFilePicker(
  private val appScope: CoroutineScope,
  private val appConstants: AppConstants,
  private val replyManager: ReplyManager,
  private val fileManager: FileManager
) : IFilePicker<LocalFilePicker.LocalFilePickerInput> {
  private val activeRequests = ConcurrentHashMap<Int, EnqueuedRequest>()
  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)
  private val requestCodeCounter = AtomicInteger(0)
  private val activityRef = AtomicReference<StartActivity>(null)

  private val selectedFilePickerBroadcastReceiver = SelectedFilePickerBroadcastReceiver()

  fun onActivityCreated(activity: StartActivity) {
    BackgroundUtils.ensureMainThread()
    activityRef.set(activity)
  }

  fun onActivityDestroyed() {
    BackgroundUtils.ensureMainThread()
    serializedCoroutineExecutor.cancelChildren()

    activeRequests.values.forEach { enqueuedRequest -> enqueuedRequest.completableDeferred.cancel() }
    activeRequests.clear()

    activityRef.set(null)
  }

  override suspend fun pickFile(filePickerInput: LocalFilePickerInput): ModularResult<PickedFile> {
    BackgroundUtils.ensureMainThread()

    if (filePickerInput.clearLastRememberedFilePicker) {
      PersistableChanState.lastRememberedFilePicker.set("")
    }

    val attachedActivity = activityRef.get()
    if (attachedActivity == null) {
      return error(IFilePicker.FilePickerError.ActivityIsNotSet())
    }

    val intents = collectIntents()
    if (intents.isEmpty()) {
      return error(IFilePicker.FilePickerError.NoFilePickersFound())
    }

    val completableDeferred = CompletableDeferred<PickedFile>()

    val newRequestCode = requestCodeCounter.getAndIncrement()
    check(!activeRequests.containsKey(newRequestCode)) { "Already contains newRequestCode=$newRequestCode" }

    val newRequest = EnqueuedRequest(
      newRequestCode,
      filePickerInput.replyChanDescriptor,
      onGotSuccessResultFromActivity = filePickerInput.onGotSuccessResultFromActivity,
      completableDeferred
    )
    activeRequests[newRequestCode] = newRequest

    check(intents.isNotEmpty()) { "intents is empty!" }
    runIntentChooser(attachedActivity, intents, newRequest.requestCode)

    return Try { completableDeferred.await() }
      .finally { activeRequests.remove(newRequest.requestCode) }
      .mapError { error ->
        if (error is CancellationException) {
          return@mapError IFilePicker.FilePickerError.Canceled()
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
          finishWithError(requestCode, IFilePicker.FilePickerError.UnknownError(error))
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

    val attachedActivity = activityRef.get()
    if (attachedActivity == null) {
      return finishWithError(requestCode, IFilePicker.FilePickerError.ActivityIsNotSet())
    }

    if (resultCode != Activity.RESULT_OK) {
      finishWithError(requestCode, IFilePicker.FilePickerError.BadResultCode(resultCode))
      return
    }

    if (data == null) {
      finishWithError(requestCode, IFilePicker.FilePickerError.NoDataReturned())
      return
    }

    val uri = getUriOrNull(data)
    if (uri == null) {
      finishWithError(requestCode, IFilePicker.FilePickerError.FailedToExtractUri())
      return
    }

    enqueuedRequest.onGotSuccessResultFromActivity.invoke()

    val fileName = tryExtractFileNameOrDefault(uri, attachedActivity)

    val copyResult = copyExternalFileToReplyFileStorage(
      attachedActivity,
      uri,
      fileName,
      enqueuedRequest.replyChanDescriptor
    )

    if (copyResult is ModularResult.Error) {
      finishWithError(requestCode, IFilePicker.FilePickerError.UnknownError(copyResult.error))
      return
    }

    when (val pickedFile = (copyResult as ModularResult.Value).value) {
      is PickedFile.Result -> finishWithResult(requestCode, pickedFile)
      is PickedFile.Failure -> finishWithError(requestCode, pickedFile.reason)
    }
  }

  private fun copyExternalFileToReplyFileStorage(
    attachedActivity: StartActivity,
    uri: Uri,
    originalFileName: String,
    replyChanDescriptor: ChanDescriptor
  ): ModularResult<PickedFile> {
    return Try {
      val reply = replyManager.getReplyOrNull(replyChanDescriptor)
      if (reply == null) {
        return@Try PickedFile.Failure(IFilePicker.FilePickerError.NoReplyFound(replyChanDescriptor))
      }

      val uniqueFileName = replyManager.generateUniqueFileName(appConstants)

      val replyFile = replyManager.createNewEmptyAttachFile(uniqueFileName, originalFileName)
      if (replyFile == null) {
        return@Try PickedFile.Failure(IFilePicker.FilePickerError.FailedToGetAttachFile())
      }

      val fileUuid = replyFile.getReplyFileMeta().valueOrNull()?.fileUuid
      if (fileUuid == null) {
        return@Try PickedFile.Failure(IFilePicker.FilePickerError.FailedToCreateFileMeta())
      }

      copyExternalFileIntoReplyFile(attachedActivity, uri, replyFile)

      return@Try PickedFile.Result(replyFile)
    }
  }

  private fun copyExternalFileIntoReplyFile(
    attachedActivity: StartActivity,
    uri: Uri,
    replyFile: ReplyFile
  ) {
    val cacheFile = fileManager.fromRawFile(replyFile.fileOnDisk)
    val contentResolver = attachedActivity.contentResolver

    contentResolver.openFileDescriptor(uri, "r").use { fileDescriptor ->
      if (fileDescriptor == null) {
        throw IOException("Couldn't open file descriptor for uri = $uri")
      }

      FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
        val os = fileManager.getOutputStream(cacheFile)
        if (os == null) {
          throw IOException("Failed to get output stream (filePath='${cacheFile.getFullPath()}')")
        }

        os.use { outputStream ->
          if (!IOUtils.copy(inputStream, outputStream, MAX_FILE_SIZE)) {
            throw IOException(
              "Failed to copy external file (uri='$uri') into reply file " +
                "(filePath='${cacheFile.getFullPath()}')"
            )
          }
        }
      }
    }
  }

  private fun finishWithResult(requestCode: Int, value: PickedFile.Result) {
    Logger.d(TAG, "finishWithResult success, requestCode=$requestCode, " +
      "value=${value.replyFile.shortState()}")
    activeRequests[requestCode]?.completableDeferred?.complete(value)
  }

  private fun finishWithError(requestCode: Int, error: IFilePicker.FilePickerError) {
    Logger.d(TAG, "finishWithError success, requestCode=$requestCode, " +
        "error=${error.errorMessageOrClassName()}")
    activeRequests[requestCode]?.completableDeferred?.complete(PickedFile.Failure(error))
  }

  private fun getUriOrNull(intent: Intent): Uri? {
    if (intent.data != null) {
      return intent.data
    }

    val clipData = intent.clipData
    if (clipData != null && clipData.itemCount > 0) {
      return clipData.getItemAt(0).uri
    }

    return null
  }

  private fun tryExtractFileNameOrDefault(uri: Uri, activity: StartActivity): String {
    var fileName = activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (nameIndex > -1 && cursor.moveToFirst()) {
        return@use cursor.getString(nameIndex)
      }

      return@use null
    }

    if (fileName == null) {
      // As per the comment on OpenableColumns.DISPLAY_NAME:
      // If this is not provided then the name should default to the last segment
      // of the file's URI.
      fileName = uri.lastPathSegment ?: DEFAULT_FILE_NAME
    }

    return fileName
  }

  private fun collectIntents(): List<Intent> {
    val pm = AndroidUtils.getAppContext().packageManager
    val intent = Intent(Intent.ACTION_GET_CONTENT)
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.type = "*/*"

    val resolveInfos = pm.queryIntentActivities(intent, 0)
    val intents: MutableList<Intent> = ArrayList(resolveInfos.size)

    val lastRememberedFilePicker = PersistableChanState.lastRememberedFilePicker.get()
    if (lastRememberedFilePicker.isNotEmpty()) {
      val lastRememberedFilePickerInfo = resolveInfos.firstOrNull { resolveInfo ->
        resolveInfo.activityInfo.packageName == lastRememberedFilePicker
      }

      if (lastRememberedFilePickerInfo != null) {
        val newIntent = Intent(Intent.ACTION_GET_CONTENT)
        newIntent.addCategory(Intent.CATEGORY_OPENABLE)
        newIntent.setPackage(lastRememberedFilePickerInfo.activityInfo.packageName)
        newIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        newIntent.type = "*/*"

        return listOf(newIntent)
      }
    }

    for (info in resolveInfos) {
      val newIntent = Intent(Intent.ACTION_GET_CONTENT)
      newIntent.addCategory(Intent.CATEGORY_OPENABLE)
      newIntent.setPackage(info.activityInfo.packageName)
      newIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      newIntent.type = "*/*"

      intents.add(newIntent)
    }

    return intents
  }

  private fun runIntentChooser(activity: StartActivity, intents: List<Intent>, requestCode: Int) {
    check(intents.isNotEmpty()) { "intents is empty!" }

    if (intents.size == 1) {
      activity.startActivityForResult(intents[0], requestCode)
      return
    }

    val chooser = if (isAndroidL_MR1()) {
      val receiverIntent = Intent(
        activity,
        SelectedFilePickerBroadcastReceiver::class.java
      )

      val pendingIntent = PendingIntent.getBroadcast(
        activity,
        0,
        receiverIntent,
        PendingIntent.FLAG_UPDATE_CURRENT
      )

      activity.registerReceiver(
        selectedFilePickerBroadcastReceiver,
        IntentFilter(Intent.ACTION_GET_CONTENT)
      )

      Intent.createChooser(
        intents.last(),
        getString(R.string.image_pick_delegate_select_file_picker),
        pendingIntent.intentSender
      )
    } else {
      Intent.createChooser(
        intents.last(),
        getString(R.string.image_pick_delegate_select_file_picker)
      )
    }

    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toTypedArray())
    activity.startActivityForResult(chooser, requestCode)
  }

  class SelectedFilePickerBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (context == null || intent == null) {
        return
      }

      if (!isAndroidL_MR1()) {
        return
      }

      val component = intent.getParcelableExtra<ComponentName>(Intent.EXTRA_CHOSEN_COMPONENT)
        ?: return

      Logger.d(TAG, "Setting lastRememberedFilePicker to " +
        "(packageName=${component.packageName}, className=${component.className})")

      PersistableChanState.lastRememberedFilePicker.set(component.packageName)
    }
  }

  data class LocalFilePickerInput(
    val replyChanDescriptor: ChanDescriptor,
    val clearLastRememberedFilePicker: Boolean = false,
    val onGotSuccessResultFromActivity: suspend () -> Unit
  )

  private data class EnqueuedRequest(
    val requestCode: Int,
    val replyChanDescriptor: ChanDescriptor,
    val onGotSuccessResultFromActivity: suspend () -> Unit,
    val completableDeferred: CompletableDeferred<PickedFile>
  )

  companion object {
    private const val TAG = "LocalFilePicker"

    private const val DEFAULT_FILE_NAME = "attach_file"
    private const val MAX_FILE_SIZE = 50 * 1024 * 1024.toLong()
  }

}