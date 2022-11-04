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
package com.github.k1rakishou.chan.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.text.Spanned
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.ui.helper.picker.PickedFile
import com.github.k1rakishou.chan.ui.helper.picker.ShareFilePicker
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("ClickableViewAccessibility")
class ReplyInputEditText @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ColorizableEditText(context, attrs) {

  @Inject
  lateinit var imagePickHelper: ImagePickHelper

  private var listener: SelectionChangedListener? = null
  private var plainTextPaste = false
  private var showLoadingViewFunc: ((Int) -> Unit)? = null
  private var hideLoadingViewFunc: (() -> Unit)? = null

  private val kurobaScope = KurobaCoroutineScope()
  private var activeJob: Job? = null

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // Hopefully this will fix these crashes:
      // java.lang.RuntimeException:
      //    android.os.TransactionTooLargeException: data parcel size 296380 bytes
      importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    setOnTouchListener { view, event ->
      if (hasFocus()) {
        view.parent.requestDisallowInterceptTouchEvent(true)

        when (event.action and MotionEvent.ACTION_MASK) {
          MotionEvent.ACTION_SCROLL -> {
            view.parent.requestDisallowInterceptTouchEvent(false)
            return@setOnTouchListener true
          }
        }
      }

      return@setOnTouchListener false
    }
  }

  fun setSelectionChangedListener(listener: SelectionChangedListener?) {
    this.listener = listener
  }

  fun setPlainTextPaste(plainTextPaste: Boolean) {
    this.plainTextPaste = plainTextPaste
  }

  fun setShowLoadingViewFunc(showLoadingViewFunc: (Int) -> Unit) {
    this.showLoadingViewFunc = showLoadingViewFunc
  }

  fun setHideLoadingViewFunc(hideLoadingViewFunc: () -> Unit) {
    this.hideLoadingViewFunc = hideLoadingViewFunc
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    if (activeJob != null) {
      activeJob?.cancel()
      activeJob = null
    }
  }

  override fun isSuggestionsEnabled(): Boolean {
    // this is due to an issue where suggestions are not run synchronously, so the suggestion
    // system tries to generate a set of suggestions, but if you delete text while this occurs,
    // an index out of bounds exception will be thrown as obviously you are now out of the range it
    // was calculating suggestions for this is solved on Android 10 and above, but not below;
    // suggestions are disabled for these Android versions https://issuetracker.google.com/issues/140891676
    // autocorrect still functions, but is only on the keyboard, not through a popup window
    // (which is where the crash happens)

    if (AndroidUtils.isAndroid10()) {
      return super.isSuggestionsEnabled()
    }

    return false
  }

  override fun onSelectionChanged(selStart: Int, selEnd: Int) {
    super.onSelectionChanged(selStart, selEnd)
    listener?.onSelectionChanged()
  }

  override fun onTouchEvent(event: MotionEvent?): Boolean {
    return try {
      super.onTouchEvent(event)
    } catch (error: Throwable) {
//      java.lang.IndexOutOfBoundsException: setSpan (109 ... 480) ends beyond length 109
//        at android.text.SpannableStringBuilder.checkRange(SpannableStringBuilder.java:1326)
//        at android.text.SpannableStringBuilder.setSpan(SpannableStringBuilder.java:685)
//        at android.text.SpannableStringBuilder.setSpan(SpannableStringBuilder.java:677)
//        at androidx.emoji2.text.SpannableBuilder.setSpan(SpannableBuilder.java:4)
//        at android.widget.Editor$SuggestionsPopupWindow.updateSuggestions(Editor.java:4173)
//        at android.widget.Editor$SuggestionsPopupWindow.show(Editor.java:4039)
      Logger.e(TAG, "Caught Throwable in onTouchEvent() msg: ${error.errorMessageOrClassName()}")
      return false
    }
  }

  override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
//      java.lang.IndexOutOfBoundsException: setSpan (109 ... 480) ends beyond length 109
//        at android.text.SpannableStringBuilder.checkRange(SpannableStringBuilder.java:1326)
//        at android.text.SpannableStringBuilder.setSpan(SpannableStringBuilder.java:685)
//        at android.text.SpannableStringBuilder.setSpan(SpannableStringBuilder.java:677)
//        at androidx.emoji2.text.SpannableBuilder.setSpan(SpannableBuilder.java:4)
//        at android.widget.Editor$SuggestionsPopupWindow.updateSuggestions(Editor.java:4173)
//        at android.widget.Editor$SuggestionsPopupWindow.show(Editor.java:4039)
    return super.postDelayed(SafeRunnableWrapper(action), delayMillis)
  }

  override fun performClick(): Boolean {
    try {
      return super.performClick()
    } catch (error: RemoteException) {
      Logger.e(TAG, "Caught RemoteException in performClick() msg: ${error.errorMessageOrClassName()}")
      return false
    }
  }

  override fun onTextContextMenuItem(id: Int): Boolean {
    val currentText = text
      ?: return false
    val start = selectionStart
    val end = selectionEnd

    val min = if (isFocused) {
      Math.max(0, Math.min(start, end))
    } else {
      0
    }

    val max = if (isFocused) {
      Math.max(0, Math.max(start, end))
    } else {
      currentText.length
    }

    if (id == android.R.id.paste && plainTextPaste) {
      // this code is basically a duplicate of the plain text paste functionality for later API versions
      val clip = AndroidUtils.getClipboardManager().primaryClip
      if (clip != null) {
        var didFirst = false
        for (i in 0 until clip.itemCount) {
          // Get an item as text and remove all spans by toString().
          val text = clip.getItemAt(i).coerceToText(context)
          val paste = (text as? Spanned)?.toString() ?: text

          if (paste != null) {
            if (!didFirst) {
              setSelection(max)
              currentText.replace(min, max, paste)
              didFirst = true
            } else {
              currentText.insert(selectionEnd, "\n")
              currentText.insert(selectionEnd, paste)
            }
          }
        }
      }

      return true
    }

    return super.onTextContextMenuItem(id)
  }

  override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection? {
    val ic = super.onCreateInputConnection(editorInfo)
      ?: return null

    EditorInfoCompat.setContentMimeTypes(editorInfo, arrayOf("image/*"))

    val callback: InputConnectionCompat.OnCommitContentListener =
      object : InputConnectionCompat.OnCommitContentListener {
        override fun onCommitContent(
          inputContentInfo: InputContentInfoCompat,
          flags: Int,
          opts: Bundle?
        ): Boolean {
          if (!AndroidUtils.isAndroidNMR1()) {
            AppModuleAndroidUtils.showToast(
              context,
              "Unsupported Android version (Must be >= N_MR1)"
            )
            return false
          }

          if (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION == 0) {
            AppModuleAndroidUtils.showToast(
              context, "No INPUT_CONTENT_GRANT_READ_URI_PERMISSION " +
                "flag present"
            )
            return false
          }

          if (activeJob != null) {
            AppModuleAndroidUtils.showToast(context, getString(R.string.share_is_in_progress))
            return false
          }

          try {
            inputContentInfo.requestPermission()
          } catch (e: Exception) {
            Logger.e(TAG, "inputContentInfo.requestPermission() error", e)
            AppModuleAndroidUtils.showToast(context, "requestPermission() failed")
            return false
          }

          activeJob = kurobaScope.launch {
            try {
              AppModuleAndroidUtils.showToast(context, getString(R.string.share_success_start))

              val shareFilePickerInput = ShareFilePicker.ShareFilePickerInput(
                notifyListeners = true,
                dataUri = null,
                clipData = null,
                inputContentInfo = inputContentInfo,
                showLoadingViewFunc = showLoadingViewFunc,
                hideLoadingViewFunc = hideLoadingViewFunc
              )

              val pickedFile = imagePickHelper.pickFilesFromIncomingShare(shareFilePickerInput)
                .unwrap()

              if (pickedFile is PickedFile.Failure) {
                if (pickedFile.reason.isCanceled()) {
                  return@launch
                }

                throw pickedFile.reason
              }

              val replyFiles = (pickedFile as PickedFile.Result).replyFiles

              AppModuleAndroidUtils.showToast(
                context,
                getString(R.string.share_success_message, replyFiles.size)
              )
            } catch (error: Throwable) {
              Logger.e(TAG, "imagePickHelper.pickFilesFromIntent() failure", error)
              AppModuleAndroidUtils.showToast(context, R.string.share_error_message)
            } finally {
              try {
                inputContentInfo.releasePermission()
              } catch (e: Exception) {
                Logger.e(TAG, "inputContentInfo.releasePermission() error", e)
              }

              activeJob = null
            }
          }

          return true
        }
      }

    val connectionWrapper = InputConnectionCompat.createWrapper(ic, editorInfo, callback)

    return KurobaInputConnectionWrapper(connectionWrapper)
  }

  fun cleanup() {
    if (activeJob != null) {
      activeJob?.cancel()
      activeJob = null
    }

    this.listener = null
    this.showLoadingViewFunc = null
    this.hideLoadingViewFunc = null
  }

  private class KurobaInputConnectionWrapper(
    inputConnection: InputConnection
  ) : InputConnectionWrapper(inputConnection, false) {

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
      return try {
        super.deleteSurroundingText(beforeLength, afterLength)
      } catch (error: Throwable) {
        // java.lang.IndexOutOfBoundsException: replace (0 ... -1) has end before start in
        // androidx.emoji2.viewsintegration.EmojiInputConnection
        Logger.e(TAG, "Caught Throwable in deleteSurroundingText() msg: ${error.errorMessageOrClassName()}")
        return false
      }
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
      try {
        return super.commitText(text, newCursorPosition)
      } catch (error: Throwable) {
        // java.lang.RuntimeException: android.os.TransactionTooLargeException: data parcel size 591468 bytes
        // 	  at android.view.autofill.AutofillManager.updateSessionLocked(AutofillManager.java:2184)
        // 	  at android.view.autofill.AutofillManager.notifyValueChanged(AutofillManager.java:1583)
        // 	  at android.widget.TextView.notifyListeningManagersAfterTextChanged(TextView.java:11935)
        // 	  at android.widget.TextView.sendAfterTextChanged(TextView.java:11915)
        // 	  at android.widget.TextView$ChangeWatcher.afterTextChanged(TextView.java:15283)
        // 	  at android.text.SpannableStringBuilder.sendAfterTextChanged(SpannableStringBuilder.java:1291)
        // 	  at android.text.SpannableStringBuilder.replace(SpannableStringBuilder.java:591)
        // 	  at androidx.emoji2.text.SpannableBuilder.replace(SpannableBuilder.java:11)
        // 	  at android.text.SpannableStringBuilder.replace(SpannableStringBuilder.java:521)
        // 	  at androidx.emoji2.text.SpannableBuilder.replace(SpannableBuilder.java:2)
        // 	  at android.view.inputmethod.BaseInputConnection.replaceText(BaseInputConnection.java:945)
        // 	  at android.view.inputmethod.BaseInputConnection.commitText(BaseInputConnection.java:219)
        // 	  at com.android.internal.inputmethod.EditableInputConnection.commitText(EditableInputConnection.java:201)
        // 	  at android.view.inputmethod.InputConnectionWrapper.commitText(InputConnectionWrapper.java:200)
        // 	  at android.view.inputmethod.InputConnectionWrapper.commitText(InputConnectionWrapper.java:200)
        // 	  at android.view.inputmethod.InputConnectionWrapper.commitText(InputConnectionWrapper.java:200)
        // 	  at com.android.internal.inputmethod.RemoteInputConnectionImpl.lambda$commitText$16$com-android-internal-inputmethod-RemoteInputConnectionImpl(RemoteInputConnectionImpl.java:569)
        // 	  at com.android.internal.inputmethod.RemoteInputConnectionImpl$$ExternalSyntheticLambda34.run(Unknown Source:8)
        //
        // android.os.DeadSystemRuntimeException: android.os.DeadSystemException
        //	  at android.view.autofill.AutofillManager.updateSessionLocked(AutofillManager.java:2184)
        //	  at android.view.autofill.AutofillManager.notifyValueChanged(AutofillManager.java:1583)
        //	  at android.widget.TextView.notifyListeningManagersAfterTextChanged(TextView.java:11935)
        //	  at android.widget.TextView.sendAfterTextChanged(TextView.java:11915)
        //	  at android.widget.TextView$ChangeWatcher.afterTextChanged(TextView.java:15283)
        //	  at android.text.SpannableStringBuilder.sendAfterTextChanged(SpannableStringBuilder.java:1291)
        //	  at android.text.SpannableStringBuilder.replace(SpannableStringBuilder.java:591)
        //	  at androidx.emoji2.text.SpannableBuilder.replace(SpannableBuilder.java:11)
        //	  at android.text.SpannableStringBuilder.replace(SpannableStringBuilder.java:521)
        //	  at androidx.emoji2.text.SpannableBuilder.replace(SpannableBuilder.java:2)
        //	  at android.view.inputmethod.BaseInputConnection.replaceText(BaseInputConnection.java:945)
        //	  at android.view.inputmethod.BaseInputConnection.commitText(BaseInputConnection.java:219)
        //	  at com.android.internal.inputmethod.EditableInputConnection.commitText(EditableInputConnection.java:201)
        //	  at android.view.inputmethod.InputConnectionWrapper.commitText(InputConnectionWrapper.java:200)
        //	  at android.view.inputmethod.InputConnectionWrapper.commitText(InputConnectionWrapper.java:200)
        //	  at android.view.inputmethod.InputConnectionWrapper.commitText(InputConnectionWrapper.java:200)
        //	  at com.android.internal.inputmethod.RemoteInputConnectionImpl.lambda$commitText$16$com-android-internal-inputmethod-RemoteInputConnectionImpl(RemoteInputConnectionImpl.java:569)
        //	  at com.android.internal.inputmethod.RemoteInputConnectionImpl$$ExternalSyntheticLambda34.run(Unknown Source:8)
        Logger.e(TAG, "Caught Throwable in commitText() msg: ${error.errorMessageOrClassName()}")
        return false
      }
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
      try {
        return super.setComposingText(text, newCursorPosition)
      } catch (error: RemoteException) {
//        android.os.DeadSystemRuntimeException: android.os.DeadSystemException
//          at android.view.autofill.AutofillManager.updateSessionLocked(AutofillManager.java:2184)
//          at android.view.autofill.AutofillManager.notifyValueChanged(AutofillManager.java:1583)
//          at android.widget.TextView.notifyListeningManagersAfterTextChanged(TextView.java:11935)
//          at android.widget.TextView.sendAfterTextChanged(TextView.java:11915)
//          at android.widget.TextView$ChangeWatcher.afterTextChanged(TextView.java:15283)
//          at android.text.SpannableStringBuilder.sendAfterTextChanged(SpannableStringBuilder.java:1291)
//          at android.text.SpannableStringBuilder.replace(SpannableStringBuilder.java:591)
//          at androidx.emoji2.text.SpannableBuilder.replace(SpannableBuilder.java:11)
//          at android.text.SpannableStringBuilder.replace(SpannableStringBuilder.java:521)
//          at androidx.emoji2.text.SpannableBuilder.replace(SpannableBuilder.java:2)
//          at android.view.inputmethod.BaseInputConnection.replaceText(BaseInputConnection.java:945)
//          at android.view.inputmethod.BaseInputConnection.setComposingText(BaseInputConnection.java:712)
//          at android.view.inputmethod.InputConnectionWrapper.setComposingText(InputConnectionWrapper.java:154)
//          at android.view.inputmethod.InputConnectionWrapper.setComposingText(InputConnectionWrapper.java:154)
//          at android.view.inputmethod.InputConnectionWrapper.setComposingText(InputConnectionWrapper.java:154)
//          at com.android.internal.inputmethod.RemoteInputConnectionImpl.lambda$setComposingText$25$com-android-internal-inputmethod-RemoteInputConnectionImpl(RemoteInputConnectionImpl.java:724)
//          at com.android.internal.inputmethod.RemoteInputConnectionImpl$$ExternalSyntheticLambda19.run(Unknown Source:8)
        Logger.e(TAG, "Caught RemoteException in setComposingText() msg: ${error.errorMessageOrClassName()}")
        return false
      }
    }
  }

  private class SafeRunnableWrapper(
    private val runnable: Runnable
  ) : Runnable {

    override fun run() {
      try {
        runnable.run()
      } catch (error: Throwable) {
        Logger.e(TAG, "runnable.run() crashed with error ${error.errorMessageOrClassName()}")
      }
    }

    override fun equals(other: Any?): Boolean {
      if (other is SafeRunnableWrapper) {
        return other.runnable === runnable
      } else if (other is Runnable) {
        return other === runnable
      }

      return false
    }

    override fun hashCode(): Int {
      return runnable.hashCode()
    }

  }

  interface SelectionChangedListener {
    fun onSelectionChanged()
  }

  companion object {
    private const val TAG = "SelectionListeningEditText"
  }
}