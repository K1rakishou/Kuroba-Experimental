package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.processDataCollectionConcurrentlyIndexed
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.common.suspendConvertIntoJsonObjectWithAdapter
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeParser
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.Request
import java.util.*

class DownloadThemeJsonFilesUseCase(
  private val proxiedOkHttpClient: RealProxiedOkHttpClient,
  private val moshi: Moshi,
  private val themeEngine: ThemeEngine
) : ISuspendUseCase<Unit, List<ChanTheme>> {

  override suspend fun execute(parameter: Unit): List<ChanTheme> {
    return ModularResult.Try { downloadThemeJsonFilesInternal() }
      .peekError { error -> Logger.e(TAG, "downloadThemeJsonFilesInternal() error: ${error}") }
      .mapErrorToValue { emptyList() }
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  private suspend fun downloadThemeJsonFilesInternal(): List<ChanTheme> {
    val request = Request.Builder()
      .url(GET_THEMES_LIST_ENDPOINT)
      .get()
      .build()


    val repoThemeFiles = Types.newParameterizedType(
      List::class.java,
      RepoThemeFile::class.java
    )

    val adapter = moshi.adapter<List<RepoThemeFile>>(repoThemeFiles)

    val repoFileTree = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
      request = request,
      adapter = adapter
    ).unwrap()

    if (repoFileTree == null) {
      Logger.e(TAG, "downloadThemeFileAndConvertToChanTheme() repoFileTree is null")
      return emptyList()
    }

    return processDataCollectionConcurrentlyIndexed<RepoThemeFile, ChanTheme?>(repoFileTree) { _, repoFile ->
      return@processDataCollectionConcurrentlyIndexed ModularResult
        .Try { downloadThemeFileAndConvertToChanTheme(repoFile) }
        .mapErrorToValue { error ->
          Logger.e(TAG, "downloadThemeFileAndConvertToChanTheme($repoFile) error", error)
          return@mapErrorToValue null
        }
    }.filterNotNull()
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  private suspend fun downloadThemeFileAndConvertToChanTheme(repoThemeFile: RepoThemeFile): ChanTheme? {
    if (!repoThemeFile.name.endsWith(".json")) {
      Logger.e(TAG, "downloadThemeFileAndConvertToChanTheme() Unexpected file name: \'${repoThemeFile.name}\'")
      return null
    }

    if (repoThemeFile.size == 0 || repoThemeFile.size > MAX_THEME_FILE_SIZE) {
      Logger.e(TAG, "downloadThemeFileAndConvertToChanTheme() Bad file size: ${repoThemeFile.size}")
      return null
    }

    val request = Request.Builder()
      .url(repoThemeFile.downloadUrl)
      .get()
      .build()

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)

    if (!response.isSuccessful) {
      Logger.e(TAG, "downloadThemeFileAndConvertToChanTheme() HttpError: ${response.code}")
      return null
    }

    val themeFileJson = response.body?.string()
    if (themeFileJson == null) {
      Logger.e(TAG, "downloadThemeFileAndConvertToChanTheme() body is null")
      return null
    }

    val themeParseResult = themeEngine.themeParser.parseTheme(themeFileJson)

    when (themeParseResult) {
      is ThemeParser.ThemeParseResult.AttemptToImportWrongTheme -> {
        // This shouldn't happen here
        return null
      }
      is ThemeParser.ThemeParseResult.BadName -> {
        Logger.e(TAG, "downloadThemeFileAndConvertToChanTheme::parseTheme() " +
          "BadName: \'${themeParseResult.name}\'")
        return null
      }
      is ThemeParser.ThemeParseResult.FailedToParseSomeFields -> {
        Logger.e(TAG, "downloadThemeFileAndConvertToChanTheme::parseTheme() " +
          "FailedToParseSomeFields: \'${themeParseResult.unparsedFields}\'")
        return null
      }
      is ThemeParser.ThemeParseResult.Error -> {
        Logger.e(TAG, "downloadThemeFileAndConvertToChanTheme::parseTheme() " +
          "UnknownError", themeParseResult.error)
        return null
      }
      is ThemeParser.ThemeParseResult.Success -> {
        Logger.d(TAG, "downloadThemeFileAndConvertToChanTheme::parseTheme() " +
          "Success, parsed theme with name: \'${themeParseResult.chanTheme.name}\'")
        return themeParseResult.chanTheme
      }
    }
  }

  @JsonClass(generateAdapter = true)
  data class RepoThemeFile(
    @Json(name = "name")
    val name: String,
    @Json(name = "size")
    val size: Int,
    @Json(name = "download_url")
    val downloadUrl: String
  )

  companion object {
    private const val TAG = "DownloadThemeJsonFilesUseCase"
    private const val GET_THEMES_LIST_ENDPOINT = "https://api.github.com/repos/K1rakishou/KurobaEx-themes/contents/themes"
    private const val MAX_THEME_FILE_SIZE = 1024 * 128 // 128KB
  }
}