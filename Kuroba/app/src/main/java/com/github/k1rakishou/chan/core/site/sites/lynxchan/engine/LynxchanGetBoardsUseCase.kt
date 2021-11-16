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

    totalLynxchanBoards += lynxchanBoardsPage.boards
    Logger.d(TAG, "execute() site ${siteDescriptor.siteName} has ${lynxchanBoardsPage.pageCount} board pages")

    if (lynxchanBoardsPage.pageCount > 1) {
      val restOfBoards = loadRestOfBoards(
        boardsEndpoint = boardsEndpoint,
        pageCount = lynxchanBoardsPage.pageCount
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
        description = lynxchanBoardsData.boardDescription,
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

  @JsonClass(generateAdapter = true)
  data class LynxchanBoardsPage(
    @Json(name = "pageCount") val pageCount: Int,
    @Json(name = "boards") val boards: List<LynxchanBoardsData>
  )

  @JsonClass(generateAdapter = true)
  data class LynxchanBoardsData(
    @Json(name = "boardUri") val boardUri: String,
    @Json(name = "boardName") val boardName: String,
    @Json(name = "boardDescription") val boardDescription: String
  )

  companion object {
    private const val TAG = "LynxchanGetBoardsUseCase"
  }

}