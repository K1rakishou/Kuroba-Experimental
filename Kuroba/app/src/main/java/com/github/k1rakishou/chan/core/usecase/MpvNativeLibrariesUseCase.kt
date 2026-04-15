package com.github.k1rakishou.chan.core.usecase

import android.content.Context
import android.os.Build
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.mpv.MPVLib
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.network.ProgressResponseBody
import com.github.k1rakishou.common.network.asProgressResponseBody
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.common.suspendConvertIntoJsonObjectWithAdapter
import com.github.k1rakishou.core_logger.Logger
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.internal.closeQuietly
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class MpvNativeLibrariesUseCase(
  private val appContext: Context,
  private val appConstants: AppConstants,
  private val moshi: Moshi,
  private val proxiedOkHttpClient: ProxiedOkHttpClient
) {
  private val githubReleaseResponsesListType = Types.newParameterizedType(
    List::class.java,
    GithubReleaseResponse::class.java
  )

  suspend fun checkUpdate(): ModularResult<MpvVersionInfo> {
    return ModularResult.Try {
      return@Try withContext(Dispatchers.IO) {
        val mpvVersionInfo = versionToCheck()
        if (mpvVersionInfo.usingLatestVersion()) {
          return@withContext mpvVersionInfo
        }

        val releaseTag = "v${mpvVersionInfo.supported}"
        val abi = Build.SUPPORTED_ABIS.firstOrNull { abi -> abi.lowercase() in LIB_ABIS }
        Logger.d(TAG, "Supported abis: \'${Build.SUPPORTED_ABIS.joinToString()}\', selected abi: \'${abi}\'")

        if (abi == null) {
          throw MpvInstallLibsFromGithubException(
            "No suitable ABI found: " +
              "expected one of: '${LIB_ABIS.joinToString()}', " +
              "got: '${Build.SUPPORTED_ABIS.joinToString()}'"
          )
        }

        val releaseForThisApp = fetchReleaseResponse(releaseTag)
        if (releaseForThisApp == null) {
          return@withContext mpvVersionInfo
        }

        val releaseVersion = releaseForThisApp.version()
        if (releaseVersion == null) {
          throw MpvInstallLibsFromGithubException(
            "Unexpected version: ${releaseVersion}, tag: ${releaseForThisApp.tagName}"
          )
        }

        return@withContext mpvVersionInfo
      }
    }
  }

  suspend fun install(
    onProgress: (ProgressResponseBody.ProgressEvent) -> Unit
  ): ModularResult<Unit> {
    return ModularResult.Try {
      return@Try withContext(Dispatchers.IO) {
        val version = versionToCheck()
        if (version.usingLatestVersion()) {
          throw MpvInstallLibsFromGithubException(
            "Latest supported version ${version.supportedVersionFormatted()} is already installed"
          )
        }

        val releaseTag = "v${version.supported}"
        val abi = Build.SUPPORTED_ABIS.firstOrNull { abi -> abi.lowercase() in LIB_ABIS }
        Logger.d(TAG, "Supported abis: \'${Build.SUPPORTED_ABIS.joinToString()}\', selected abi: \'${abi}\'")

        if (abi == null) {
          throw MpvInstallLibsFromGithubException(
            "No suitable ABI found: " +
              "expected one of: '${LIB_ABIS.joinToString()}', " +
              "got: '${Build.SUPPORTED_ABIS.joinToString()}'"
          )
        }

        val releaseForThisApp = fetchReleaseResponse(releaseTag)
          ?: throw MpvInstallLibsFromGithubException("Failed to find libraries for \'v${version}\'")

        val githubAssetForThisApp = releaseForThisApp.assets.firstOrNull { githubAsset ->
          val archiveName = githubAsset.downloadUrl
            .removePrefix(KUROBAEX_MPV_LIBS_RELEASES_ENDPOINT)
            .removePrefix(releaseTag)

          if (archiveName.contains(abi, ignoreCase = true)) {
            return@firstOrNull true
          }

          return@firstOrNull false
        }

        if (githubAssetForThisApp == null) {
          val downloadUrls = releaseForThisApp.assets
            .joinToString(transform = { githubAsset -> githubAsset.downloadUrl })

          throw MpvInstallLibsFromGithubException(
            "Failed to find download url for ABI ${abi}, downloadUrls: \'${downloadUrls}\'"
          )
        }

        val downloadUrl = githubAssetForThisApp.downloadUrl
        Logger.d(TAG, "Downloading \'${downloadUrl}\'")

        val downloadRequest = Request.Builder()
          .get()
          .url(downloadUrl)
          .build()

        val response = proxiedOkHttpClient.okHttpClient().suspendCall(downloadRequest)
        if (!response.isSuccessful) {
          throw BadStatusResponseException(response.code)
        }

        val progressResponseBody = response.body.asProgressResponseBody()

        val mpvLibsZipArchiveFile = File(appContext.cacheDir, "mpv_libs.zip")
        Logger.d(TAG, "mpvLibsZipArchiveFile: \'${mpvLibsZipArchiveFile.absolutePath}\'")

        try {
          if (mpvLibsZipArchiveFile.exists()) {
            mpvLibsZipArchiveFile.delete()
          }

          if (!mpvLibsZipArchiveFile.createNewFile()) {
            throw MpvInstallLibsFromGithubException("Failed to create mpv libs output file on disk")
          }

          coroutineScope {
            val job = launch {
              progressResponseBody.progressFlow
                .collect { progressEvent -> onProgress(progressEvent) }
            }

            try {
              progressResponseBody.source().inputStream().use { inputStream ->
                mpvLibsZipArchiveFile.outputStream().use { outputStream ->
                  inputStream.copyTo(outputStream)
                }
              }
            } finally {
              job.cancel()
            }
          }

          Logger.d(TAG, "Done")
          Logger.d(TAG, "Deleting old lib files")

          appConstants.mpvNativeLibsDir.listFiles()
            ?.forEach { libFile ->
              Logger.d(TAG, "Deleting ${libFile.absolutePath}")
              libFile.delete()
            }

          Logger.d(TAG, "Done")

          Logger.d(TAG, "Extracting archived libs into \'${appConstants.mpvNativeLibsDir}\'")
          extractArchiveAndMoveToLibsDirectory(mpvLibsZipArchiveFile, appConstants.mpvNativeLibsDir)
          Logger.d(TAG, "Done")
        } finally {
          mpvLibsZipArchiveFile.delete()
        }

        Logger.d(TAG, "All done")
      }
    }
  }

  private fun extractArchiveAndMoveToLibsDirectory(mpvLibsFile: File, mpvNativeLibsDir: File) {
    mpvLibsFile.inputStream().use { inputStream ->
      val zipInputStream = ZipInputStream(inputStream)
      var zipEntry: ZipEntry? = null
      var zipMalformed = true

      try {
        while (true) {
          zipEntry = zipInputStream.nextEntry
            ?: break

          val fileName = zipEntry.name
          Logger.d(TAG, "Read zipEntry.name: \'${fileName}\'")

          if (!zipEntry.isDirectory && fileName.endsWith(".so")) {
            val libName = fileName.split(delimiters = arrayOf("/", "\\")).lastOrNull()
            if (libName.isNotNullNorBlank()) {
              val outputMpvLibFile = File(mpvNativeLibsDir, libName)

              Logger.d(TAG, "Moving \'${fileName}\' from archive to \'${outputMpvLibFile.absolutePath}\' file")

              outputMpvLibFile.outputStream().use { outputStream ->
                zipInputStream.copyTo(outputStream)
              }
            } else {
              Logger.d(TAG, "Invalid name: \'$libName\'")
            }

            Logger.d(TAG, "Done")
          }

          zipInputStream.closeEntry()
          zipMalformed = false
        }
      } finally {
        zipInputStream.closeQuietly()
      }

      if (zipMalformed) {
        throw IOException("Failed to open mpv libs zip archive: '${mpvLibsFile.absolutePath}'")
      }
    }
  }

  private suspend fun fetchReleaseResponse(
    releaseTag: String
  ): GithubReleaseResponse? {
    val request = Request.Builder()
      .get()
      .url(KUROBAEX_MPV_LIBS_RELEASES_ENDPOINT)
      .build()

    val adapter = moshi.adapter<List<GithubReleaseResponse>>(githubReleaseResponsesListType)
    val githubReleases = proxiedOkHttpClient.okHttpClient()
      .suspendConvertIntoJsonObjectWithAdapter(request, adapter)
      .unwrap()

    if (githubReleases == null) {
      throw MpvInstallLibsFromGithubException("Failed to convert json to GithubReleaseResponse")
    }

    return githubReleases
      .firstOrNull { githubReleaseResponse -> githubReleaseResponse.tagName.equals(releaseTag, ignoreCase = true) }
  }

  private fun versionToCheck(): MpvVersionInfo {
    if (!MPVLib.checkLibrariesInstalled(appContext, appConstants.mpvNativeLibsDir)) {
      Logger.debug(TAG) { "MPV libraries are not installed" }
      return MpvVersionInfo(
        supported = MPVLib.SUPPORTED_MPV_PLAYER_VERSION,
        current = -1
      )
    }

    MPVLib.tryLoadLibraries(appConstants.mpvNativeLibsDir)

    val lastError = MPVLib.getLastError()
    if (lastError != null) {
      Logger.error(TAG, lastError) { "Failed to load MPV libraries" }
      return MpvVersionInfo(
        supported = MPVLib.SUPPORTED_MPV_PLAYER_VERSION,
        current = -1
      )
    }

    val playerVersion = try {
      MPVLib.mpvPlayerVersion() ?: -1
    } catch (error: Throwable) {
      Logger.error(TAG, error) { "Failed to get MPV player version" }
      -1
    }

    if (playerVersion == -1) {
      Logger.debug(TAG) { "Bad version: ${playerVersion}" }
      return MpvVersionInfo(
        supported = MPVLib.SUPPORTED_MPV_PLAYER_VERSION,
        current = -1
      )
    }

    if (playerVersion == MPVLib.SUPPORTED_MPV_PLAYER_VERSION) {
      Logger.debug(TAG) { "Already using the latest supported version ${playerVersion}" }
      return MpvVersionInfo(
        supported = MPVLib.SUPPORTED_MPV_PLAYER_VERSION,
        current = playerVersion
      )
    }

    return MpvVersionInfo(
      supported = MPVLib.SUPPORTED_MPV_PLAYER_VERSION,
      current = playerVersion
    )
  }

  data class MpvVersionInfo(
    val supported: Int,
    val current: Int
  ) {
    fun usingLatestVersion(): Boolean = current == supported

    fun supportedVersionFormatted(): String {
      return supported.toString()
    }

    fun currentVersionFormatted(appResources: AppResources): String {
      if (current == -1) {
        return appResources.string(R.string.settings_plugins_update_status_current_version_is_bad)
      }

      return current.toString()
    }
  }

  class MpvInstallLibsFromGithubException(message: String) : Exception(message)

  @JsonClass(generateAdapter = true)
  data class GithubReleaseResponse(
    @field:Json(name = "tag_name")
    val tagName: String,
    @field:Json(name = "assets")
    val assets: List<GithubAsset>
  ) {
    fun version(): Int? {
      return tagName.removePrefix("v").toIntOrNull()
    }
  }

  @JsonClass(generateAdapter = true)
  data class GithubAsset(
    @field:Json(name = "name")
    val name: String,
    @field:Json(name = "browser_download_url")
    val downloadUrl: String
  )

  companion object {
    private const val TAG = "InstallMpvNativeLibrariesFromGithubUseCase"
    private const val KUROBAEX_MPV_LIBS_RELEASES_ENDPOINT =
      "https://api.github.com/repos/K1rakishou/KurobaEx-mpv-libs/releases"

    private val LIB_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
      .map { it.lowercase() }
  }
}