package com.github.k1rakishou.chan.utils

import android.net.Uri
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.FileChooserCallback
import kotlinx.coroutines.suspendCancellableCoroutine

suspend fun FileChooser.openChooseFileDialogAsync(): ModularResult<Uri?> {
    return suspendCancellableCoroutine<ModularResult<Uri?>> { cancellableContinuation ->
        openChooseFileDialog(object : FileChooserCallback() {
            override fun onCancel(reason: String) {
                cancellableContinuation.resumeValueSafe(ModularResult.value(null))
            }

            override fun onResult(uri: Uri) {
                cancellableContinuation.resumeValueSafe(ModularResult.value(uri))
            }
        })
    }
}