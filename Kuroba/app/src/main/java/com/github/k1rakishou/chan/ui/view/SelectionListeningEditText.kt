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

import android.content.Context
import android.os.Bundle
import android.text.Spanned
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.ui.helper.picker.ShareFilePicker
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class SelectionListeningEditText @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ColorizableEditText(context, attrs) {

  @Inject
  lateinit var imagePickHelper: ImagePickHelper

  private var listener: SelectionChangedListener? = null
  private var plainTextPaste = false

  private val kurobaScope = KurobaCoroutineScope()
  private var activeJob: Job? = null

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)
  }

  fun setSelectionChangedListener(listener: SelectionChangedListener?) {
    this.listener = listener
  }

  fun setPlainTextPaste(plainTextPaste: Boolean) {
    this.plainTextPaste = plainTextPaste
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
    if (listener != null) {
      listener!!.onSelectionChanged()
    }
  }

  override fun onTextContextMenuItem(id: Int): Boolean {
    if (text == null) {
      return false
    }

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
      text!!.length
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
              getText()!!.replace(min, max, paste)
              didFirst = true
            } else {
              getText()!!.insert(selectionEnd, "\n")
              getText()!!.insert(selectionEnd, paste)
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
            return false
          }

          if (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION == 0) {
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
            return false
          }

          activeJob = kurobaScope.launch {
            try {
              AppModuleAndroidUtils.showToast(context, getString(R.string.share_success_start))

              val shareFilePickerInput = ShareFilePicker.ShareFilePickerInput(
                dataUri = null,
                clipData = null,
                inputContentInfo = inputContentInfo
              )

              imagePickHelper.pickFilesFromIntent(shareFilePickerInput)
                .unwrap()

              AppModuleAndroidUtils.showToast(context, getString(R.string.share_success_message, 1))
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

    return InputConnectionCompat.createWrapper(ic, editorInfo, callback)
  }

  interface SelectionChangedListener {
    fun onSelectionChanged()
  }

  companion object {
    private const val TAG = "SelectionListeningEditText"
  }
}