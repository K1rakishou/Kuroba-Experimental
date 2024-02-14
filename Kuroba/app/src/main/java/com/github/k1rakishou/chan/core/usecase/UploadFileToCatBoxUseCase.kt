package com.github.k1rakishou.chan.core.usecase

import android.net.Uri
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.utils.Generators
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class UploadFileToCatBoxUseCase(
    private val proxiedOkHttpClient: ProxiedOkHttpClient,
    private val fileManager: FileManager
) {

    suspend fun await(fileUri: Uri, mimeType: String, extension: String): ModularResult<HttpUrl> {
        return withContext(Dispatchers.IO) {
            return@withContext ModularResult.Try {
                val formBuilder = MultipartBody.Builder()
                formBuilder.setType(MultipartBody.FORM)

                formBuilder.addFormDataPart(
                    name = "reqtype",
                    value = "fileupload"
                )

                val externalFile = fileManager.fromUri(fileUri)
                if (externalFile == null) {
                    throw ClientException("Failed to open '${fileUri}'")
                }

                val requestBody = fileManager.getInputStream(externalFile)?.let { inputStream ->
                    inputStream
                        .readBytes()
                        .toRequestBody(contentType = mimeType.toMediaType())
                }

                if (requestBody == null) {
                    throw ClientException("Failed to prepare request body")
                }

                formBuilder.addFormDataPart(
                    name = "fileToUpload",
                    filename = "${Generators.generateRandomHexString(symbolsCount = 16)}.${extension}",
                    body = requestBody
                )

                val request = Request.Builder()
                    .url("https://catbox.moe/user/api.php")
                    .post(formBuilder.build())
                    .build()

                val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
                if (!response.isSuccessful) {
                    throw BadStatusResponseException(response.code)
                }

                val body = response.body
                if (body == null) {
                    throw EmptyBodyResponseException()
                }

                val bodyAsString = body.string().trim()

                val urlMaybe = bodyAsString.toHttpUrlOrNull()
                if (urlMaybe == null) {
                    Logger.error(TAG) { "bodyAsString: ${bodyAsString.take(256)}" }
                    throw ClientException("Failed to convert catbox response to Url")
                }

                return@Try urlMaybe
            }
        }
    }

    companion object {
        private const val TAG = "UploadFileToCatBoxUseCase"
    }

}