package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.usecase.ISuspendUseCase
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.processDataCollectionConcurrently
import com.github.k1rakishou.common.suspendConvertIntoJsonObjectWithAdapter
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import java.util.*

class LynxchanGetBoardsUseCase(
  private val appConstants: AppConstants,
  private val _moshi: Lazy<Moshi>,
  private val _proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>
) : ISuspendUseCase<LynxchanGetBoardsUseCase.Params, ModularResult<SiteBoards>> {

  private val moshi: Moshi
    get() = _moshi.get()
  private val proxiedOkHttpClient: RealProxiedOkHttpClient
    get() = _proxiedOkHttpClient.get()

  override suspend fun execute(parameter: Params): ModularResult<SiteBoards> {
    return ModularResult.Try {
      return@Try withContext(Dispatchers.IO) {
        return@withContext executeInternal(
          siteDescriptor = parameter.siteDescriptor,
          boardsEndpoint = parameter.getBoardsEndpoint
        )
      }
    }
  }

  private suspend fun executeInternal(
    siteDescriptor: SiteDescriptor,
    boardsEndpoint: HttpUrl
  ): SiteBoards {
    val request = Request.Builder()
      .url(boardsPageEndpoint(boardsEndpoint = boardsEndpoint, page = 1))
      .get()
      .build()

    val totalLynxchanBoards = mutableListOf<LynxchanBoardsData>()
    val lynxchanBoardsPageAdapter = moshi.adapter<LynxchanBoardsPage>(LynxchanBoardsPage::class.java)

    val lynxchanBoardsPage = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
      request = request,
      adapter = lynxchanBoardsPageAdapter
    ).unwrap()

    if (lynxchanBoardsPage == null) {
      Logger.d(TAG, "execute() failed to load the first page")
      return SiteBoards(siteDescriptor = siteDescriptor, boards = emptyList())
    }

    if (!lynxchanBoardsPage.isStatusOk) {
      throw GetBoardsError("Response status is not ok. Status=\'${lynxchanBoardsPage.status}\'")
    }

    val boards = lynxchanBoardsPage.boards
    val pageCount = lynxchanBoardsPage.pageCount

    if (boards == null) {
      Logger.d(TAG, "execute() \'boards\' not found")
      throw GetBoardsError("\'boards\' not found in server response")
    }

    if (pageCount == null) {
      Logger.d(TAG, "execute() \'pageCount\' not found")
      throw GetBoardsError("\'pageCount\' not found in server response")
    }

    totalLynxchanBoards += boards
    Logger.d(TAG, "execute() site ${siteDescriptor.siteName} has ${lynxchanBoardsPage.pageCount} board pages")

    if (pageCount > 1) {
      val restOfBoards = loadRestOfBoards(
        boardsEndpoint = boardsEndpoint,
        pageCount = pageCount
      )

      totalLynxchanBoards.addAll(restOfBoards)
    }

    Logger.d(TAG, "execute() loaded all boards")

    val chanBoards = totalLynxchanBoards.map { lynxchanBoardsData ->
      val boardDescriptor = BoardDescriptor.create(
        siteDescriptor = siteDescriptor,
        boardCode = lynxchanBoardsData.boardUri
      )

      return@map ChanBoard(
        boardDescriptor = boardDescriptor,
        name = lynxchanBoardsData.boardName,
        description = lynxchanBoardsData.boardDescription ?: "",
        isUnlimitedCatalog = true
      )
    }

    return SiteBoards(
      siteDescriptor = siteDescriptor,
      boards = chanBoards
    )
  }

  private suspend fun loadRestOfBoards(boardsEndpoint: HttpUrl, pageCount: Int): List<LynxchanBoardsData> {
    val pages = (2..pageCount).toList()
    val lynxchanBoardsPageAdapter = moshi.adapter<LynxchanBoardsPage>(LynxchanBoardsPage::class.java)

    return processDataCollectionConcurrently(
      dataList = pages,
      batchCount = appConstants.processorsCount.coerceAtLeast(4),
      dispatcher = Dispatchers.IO
    ) { page ->
      Logger.d(TAG, "loadRestOfBoards() Loading page ${page}...")

      val request = Request.Builder()
        .url(boardsPageEndpoint(boardsEndpoint = boardsEndpoint, page = page))
        .get()
        .build()

      val boards = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
        request = request,
        adapter = lynxchanBoardsPageAdapter
      )
        .unwrap()
        ?.boards

      if (boards == null) {
        throw IllegalStateException("Failed to parse board page: ${page}")
      }

      Logger.d(TAG, "loadRestOfBoards() Loading page ${page}...done")

      return@processDataCollectionConcurrently boards
    }.flatten()
  }

  private fun boardsPageEndpoint(boardsEndpoint: HttpUrl, page: Int): HttpUrl {
    return boardsEndpoint.newBuilder()
      .addEncodedQueryParameter("page", page.toString())
      .build()
  }

  data class Params(
    val siteDescriptor: SiteDescriptor,
    val getBoardsEndpoint: HttpUrl
  )

  class GetBoardsError(message: String) : Exception(message)

  @JsonClass(generateAdapter = true)
  data class LynxchanBoardsPage(
    @Json(name = "status") val status: String?,
    @Json(name = "data") val data: LynxchanBoardsPage?,
    @Json(name = "pageCount") val _pageCount: Int?,
    @Json(name = "boards") val _boards: List<LynxchanBoardsData>?
  ) {
    val isStatusOk: Boolean
      get() = status == null || status.equals("ok", ignoreCase = true)

    val pageCount: Int?
      get() {
        if (data != null) {
          return data.pageCount
        }

        return _pageCount
      }

    val boards: List<LynxchanBoardsData>?
      get() {
        if (data != null) {
          return data.boards
        }

        return _boards
      }
  }

  @JsonClass(generateAdapter = true)
  data class LynxchanBoardsData(
    @Json(name = "boardUri") val boardUri: String,
    @Json(name = "boardName") val boardName: String,
    @Json(name = "boardDescription") val boardDescription: String?
  )

  companion object {
    private const val TAG = "LynxchanGetBoardsUseCase"
  }

}