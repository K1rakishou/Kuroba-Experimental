package com.github.k1rakishou.chan.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.di.module.activity.ActivityModule
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.ui.helper.picker.PickedFile
import com.github.k1rakishou.chan.ui.helper.picker.ShareFilePicker
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SharingActivity : AppCompatActivity() {

  @Inject
  lateinit var imagePickHelper: ImagePickHelper
  @Inject
  lateinit var replyManager: Lazy<ReplyManager>
  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var appRestarter: AppRestarter

  private val mainScope = KurobaCoroutineScope()

  private lateinit var activityComponent: ActivityComponent

  fun getActivityComponent(): ActivityComponent {
    return activityComponent
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    activityComponent = Chan.getComponent()
      .activityComponentBuilder()
      .activity(this)
      .activityModule(ActivityModule())
      .build()
      .also { component -> component.inject(this) }

    appRestarter.attachActivity(this)
    imagePickHelper.onActivityCreated(this)

    mainScope.launch(Dispatchers.Main.immediate) { handleNewIntent(intent) }
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)

    mainScope.launch(Dispatchers.Main.immediate) { handleNewIntent(intent) }
  }

  override fun onDestroy() {
    super.onDestroy()

    if (::imagePickHelper.isInitialized) {
      imagePickHelper.onActivityDestroyed(this)
    }

    appRestarter.detachActivity(this)
    AppModuleAndroidUtils.cancelLastToast()
    mainScope.cancelChildren()
  }

  private suspend fun handleNewIntent(intent: Intent?) {
    if (intent == null) {
      return
    }

    val action = intent.action

    if (action != null && isKnownAction(action)) {
      Logger.d(TAG, "onNewIntentInternal called, action=${action}")

      if (action == Intent.ACTION_SEND) {
        if (onShareIntentReceived(intent)) {
          setResult(Activity.RESULT_OK)
        } else {
          setResult(Activity.RESULT_CANCELED)
        }
      }
    }

    finishAndRemoveTask()
  }

  private suspend fun onShareIntentReceived(intent: Intent): Boolean {
    showToast(this, getString(R.string.share_success_start), Toast.LENGTH_SHORT)

    val reloadResult = withContext(Dispatchers.IO) {
      replyManager.get().reloadFilesFromDisk(appConstants)
        .safeUnwrap { error ->
          Logger.e(TAG, "replyManager.reloadFilesFromDisk() -> MR.Error", error)
          return@withContext false
        }

      return@withContext true
    }

    if (!reloadResult) {
      showToast(
        this,
        R.string.share_error_message,
        Toast.LENGTH_LONG
      )
      return false
    }

    val shareResult = imagePickHelper.pickFilesFromIncomingShare(
      ShareFilePicker.ShareFilePickerInput(
        notifyListeners = true,
        dataUri = intent.data,
        clipData = intent.clipData,
        inputContentInfo = null
      )
    )

    when (shareResult) {
      is ModularResult.Value -> {
        when (val pickedFile = shareResult.value) {
          is PickedFile.Result -> {
            val sharedFilesCount = pickedFile.replyFiles.size

            if (sharedFilesCount > 0) {
              showToast(
                this,
                getString(R.string.share_success_message, sharedFilesCount),
                Toast.LENGTH_LONG
              )
            } else {
              showToast(
                this,
                R.string.share_error_message,
                Toast.LENGTH_LONG
              )
            }

            Logger.d(
              TAG, "imagePickHelper.pickFilesFromIntent() -> PickedFile.Result, " +
                "sharedFilesCount=$sharedFilesCount"
            )

            return true
          }
          is PickedFile.Failure -> {
            Logger.e(
              TAG, "imagePickHelper.pickFilesFromIntent() -> PickedFile.Failure",
              pickedFile.reason
            )

            if (!pickedFile.reason.isCanceled()) {
              showToast(
                this,
                R.string.share_error_message,
                Toast.LENGTH_LONG
              )
            }

            return false
          }
        }
      }
      is ModularResult.Error -> {
        Logger.e(TAG, "imagePickHelper.pickFilesFromIntent() -> MR.Error", shareResult.error)

        showToast(
          this,
          R.string.share_error_message,
          Toast.LENGTH_LONG
        )
        return false
      }
    }
  }

  private suspend fun showToast(context: Context, message: String, duration: Int) {
    withContext(Dispatchers.Main) {
      Toast.makeText(context.applicationContext, message, duration).show()
    }
  }

  private suspend fun showToast(context: Context, resId: Int, duration: Int) {
    withContext(Dispatchers.Main) {
      showToast(context, getString(resId), duration)
    }
  }

  private fun isKnownAction(action: String): Boolean {
    return when (action) {
      Intent.ACTION_SEND -> true
      else -> false
    }
  }

  companion object {
    private const val TAG = "SharingActivity"
  }
}