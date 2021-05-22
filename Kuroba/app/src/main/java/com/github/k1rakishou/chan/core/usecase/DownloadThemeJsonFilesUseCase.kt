package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.common.JsonConversionResult
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.processDataCollectionConcurrentlyIndexed
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.common.suspendConvertIntoJsonObjectWithType
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeParser
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import okhttp3.Request
import java.util.*

class DownloadThemeJsonFilesUseCase(
  private val proxiedOkHttpClient: RealProxiedOkHttpClient,
  private val gson: Gson,
  private val themeEngine: ThemeEngine
) : ISuspendUseCase<Unit, List<ChanTheme>> {

  override suspend fun execute(parameter: Unit): List<ChanTheme> {
    return ModularResult.Try { downloadThemeJsonFilesInternal() }.mapErrorToValue { emptyList() }
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  private suspend fun downloadThemeJsonFilesInternal(): List<ChanTheme> {
    val request = Request.Builder()
      .url(GET_THEMES_LIST_ENDPOINT)
      .get()
      .build()

    val repoFileTreeResult = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithType<List<RepoFile>>(
      request = request,
      gson = gson,
      type = listOfThemeFilesType
    )

    val repoFileTree = when (repoFileTreeResult) {
      is JsonConversionResult.HttpError -> {
        Logger.e(TAG, "GetThemesList HttpError: ${repoFileTreeResult.status}")
        return emptyList()
      }
      is JsonConversionResult.UnknownError -> {
        Logger.e(TAG, "GetThemesList UnknownError", repoFileTreeResult.error)
        return emptyList()
      }
      is JsonConversionResult.Success -> {
        repoFileTreeResult.obj
      }
    }

    return processDataCollectionConcurrentlyIndexed<RepoFile, ChanTheme?>(
      dataList = repoFileTree,
      batchCount = 8,
      dispatcher = Dispatchers.Default
    ) { _, repoFile ->
      return@processDataCollectionConcurrentlyIndexed ModularResult
        .Try { downloadThemeFileAndConvertToChanTheme(repoFile) }
        .mapErrorToValue { error ->
          Logger.e(TAG, "downloadThemeFileAndConvertToChanTheme($repoFile) error", error)
          return@mapErrorToValue null
        }
    }.filterNotNull()
  }

  @Suppress("MoveVariableDeclarationIntoWhen", "BlockingMethodInNonBlockingContext")
  private suspend fun downloadThemeFileAndConvertToChanTheme(repoFile: RepoFile): ChanTheme? {
    if (!repoFile.name.endsWith(".json")) {
      Logger.e(TAG, "downloadThemeFileAndConvertToChanTheme() Unexpected file name: \'${repoFile.name}\'")
      return null
    }

    if (repoFile.size == 0 || repoFile.size > MAX_THEME_FILE_SIZE) {
      Logger.e(TAG, "downloadThemeFileAndConvertToChanTheme() Bad file size: ${repoFile.size}")
      return null
    }

    val request = Request.Builder()
      .url(repoFile.downloadUrl)
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

  data class RepoFile(
    @SerializedName("name")
    val name: String,
    @SerializedName("size")
    val size: Int,
    @SerializedName("download_url")
    val downloadUrl: String
  )

  companion object {
    private const val TAG = "DownloadThemeJsonFilesUseCase"
    private const val GET_THEMES_LIST_ENDPOINT = "https://api.github.com/repos/K1rakishou/KurobaEx-themes/contents/themes"
    private const val MAX_THEME_FILE_SIZE = 1024 * 128 // 128KB

    private val listOfThemeFilesType = object : TypeToken<ArrayList<RepoFile>>() {}.type
  }
}