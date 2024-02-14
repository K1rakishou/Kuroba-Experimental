package com.github.k1rakishou.chan.ui.helper

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.github.k1rakishou.common.ModularResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class FileHelper(
    private val appContext: Context
) {

    suspend fun getFileMimeType(uri: Uri): ModularResult<FileMimeType?> {
        return ModularResult.Try {
            return@Try withContext(Dispatchers.IO) {
                val contentResolver = appContext.contentResolver
                val mimeType = contentResolver.getType(uri)
                    ?: return@withContext null

                return@withContext FileMimeType(mimeType)
            }
        }
    }

    suspend fun getFileName(uri: Uri): ModularResult<String?> {
        return ModularResult.Try {
            return@Try withContext(Dispatchers.IO) {
                var fileName: String? = null

                if (uri.scheme.equals("content")) {
                    appContext.contentResolver.query(uri, null, null, null, null).use { cursor ->
                        if (cursor != null && cursor.moveToFirst()) {
                            val nameIndex: Int = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) {
                                fileName = cursor.getString(nameIndex)
                            }
                        }
                    }
                } else if (uri.scheme.equals("file")) {
                    fileName = uri.lastPathSegment
                }

                return@withContext fileName
            }
        }
    }

}

data class FileMimeType(
    val mimeType: String
) {
    fun isAudio(): Boolean {
        return mimeType.startsWith("audio/")
    }
}