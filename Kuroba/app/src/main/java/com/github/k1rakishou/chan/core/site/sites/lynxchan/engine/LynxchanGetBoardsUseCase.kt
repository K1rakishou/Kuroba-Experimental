package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.usecase.ISuspendUseCase
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.parallelForEach
import com.github.k1rakishou.common.suspendConvertIntoJsonObjectWithAdapter
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch

class LynxchanGetBoardsUseCase(
  private val appConstants: AppConstants,
  private val moshiLazy: Lazy<Moshi>,
  private val proxiedOkHttpClientLazy: Lazy<ProxiedOkHttpClient>
) : ISuspendUseCase<LynxchanGetBoardsUseCase.Params, Flow<SiteBoards>> {

  private val moshi: Moshi
    get() = moshiLazy.get()
  private val proxiedOkHttpClient: ProxiedOkHttpClient
    get() = proxiedOkHttpClientLazy.get()

  override suspend fun execute(parameter: Params): Flow<SiteBoards> {
    return channelFlow {
      withContext(Dispatchers.IO) {
        try {
          executeInternal(
            site = parameter.site,
            boardsEndpoint = parameter.getBoardsEndpoint
          )
        } catch (error: Throwable) {
          send(SiteBoards.Result.Error(error))
        }
      }
    }
  }

  private suspend fun ProducerScope<SiteBoards>.executeInternal(
    site: LynxchanSite,
    boardsEndpoint: HttpUrl
  ) {
    val siteDescriptor = site.descriptor

    val request = Request.Builder()
      .url(boardsPageEndpoint(boardsEndpoint = boardsEndpoint, page = 1))
      .get()
      .also { builder -> site.requestModifier.modifyGenericRequest(site, builder) }
      .build()

    val totalLynxchanBoards = mutableListOf<LynxchanBoardsData>()
    val lynxchanBoardsPageAdapter = moshi.adapter<LynxchanBoardsPage>(LynxchanBoardsPage::class.java)

    val lynxchanBoardsPage = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
      request = request,
      adapter = lynxchanBoardsPageAdapter
    ).unwrap()

    if (lynxchanBoardsPage == null) {
      Logger.d(TAG, "execute() failed to load the first page")
      send(SiteBoards.Result.Success(siteDescriptor = siteDescriptor, boards = emptyList()))
      return
    }

    if (!lynxchanBoardsPage.isStatusOk) {
      throw GetBoardsError("Response status is not ok. Status=\'${lynxchanBoardsPage.status}\'")
    }

    val boards = lynxchanBoardsPage.boardsActual
    val pageCount = lynxchanBoardsPage.pageCountActual ?: 1

    if (boards == null) {
      Logger.d(TAG, "execute() \'boards\' not found")
      throw GetBoardsError("\'boards\' not found in server response")
    }

    send(SiteBoards.Progress(1, pageCount))
    totalLynxchanBoards += boards
    Logger.d(TAG, "execute() site ${siteDescriptor.siteName} has ${lynxchanBoardsPage.pageCountActual} board pages")

    if (pageCount > 1) {
      val restOfBoards = loadRestOfBoards(
        boardsEndpoint = boardsEndpoint,
        totalPagesCount = pageCount
      )

      totalLynxchanBoards.addAll(restOfBoards)
      send(SiteBoards.Progress(pageCount, pageCount))
    }

    Logger.d(TAG, "execute() loaded all boards")

    val chanBoards = totalLynxchanBoards.map { lynxchanBoardsData ->
      val boardDescriptor = BoardDescriptor.create(
        siteDescriptor = siteDescriptor,
        boardCode = lynxchanBoardsData.boardUri
      )

      val workSafe = when {
        lynxchanBoardsData.hasSfwTag -> true
        lynxchanBoardsData.hasNsfwTag -> false
        else -> null
      }

      return@map ChanBoard(
        boardDescriptor = boardDescriptor,
        name = lynxchanBoardsData.boardName,
        description = lynxchanBoardsData.boardDescription ?: "",
        workSafe = workSafe,
        isUnlimitedCatalog = true
      )
    }

    val siteBoards = SiteBoards.Result.Success(
      siteDescriptor = siteDescriptor,
      boards = chanBoards
    )

    send(siteBoards)
  }

  private suspend fun ProducerScope<SiteBoards>.loadRestOfBoards(
    boardsEndpoint: HttpUrl,
    totalPagesCount: Int
  ): List<LynxchanBoardsData> {
    val pages = (2..totalPagesCount).toList()
    val lynxchanBoardsPageAdapter = moshi.adapter<LynxchanBoardsPage>(LynxchanBoardsPage::class.java)
    val pageCounter = AtomicInt(2)

    return parallelForEach(
      dataList = pages,
      parallelization = appConstants.processorsCount.coerceAtLeast(4),
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
        ?.boardsActual

      if (boards == null) {
        error("Failed to parse board page: ${page}")
      }

      Logger.d(TAG, "loadRestOfBoards() Loading page ${page}...done")
      send(SiteBoards.Progress(pageCounter.incrementAndFetch().coerceAtMost(totalPagesCount), totalPagesCount))

      return@parallelForEach boards
    }.flatten()
  }

  private fun boardsPageEndpoint(boardsEndpoint: HttpUrl, page: Int): HttpUrl {
    return boardsEndpoint.newBuilder()
      .addEncodedQueryParameter("page", page.toString())
      .build()
  }

  data class Params(
    val site: LynxchanSite,
    val getBoardsEndpoint: HttpUrl
  )

  class GetBoardsError(message: String) : Exception(message)

  @JsonClass(generateAdapter = true)
  data class LynxchanBoardsPage(
    @field:Json(name = "status") val status: String?,
    @field:Json(name = "data") val data: LynxchanBoardsPage?,
    @field:Json(name = "pageCount") val pageCount: Int?,
    @field:Json(name = "boards") val boards: List<LynxchanBoardsData>?
  ) {
    val isStatusOk: Boolean
      get() = status == null || status.equals("ok", ignoreCase = true)

    val pageCountActual: Int?
      get() {
        if (data != null) {
          return data.pageCount
        }

        return pageCount
      }

    val boardsActual: List<LynxchanBoardsData>?
      get() {
        if (data != null) {
          return data.boards
        }

        return boards
      }
  }

  @JsonClass(generateAdapter = true)
  data class LynxchanBoardsData(
    @field:Json(name = "boardUri") val boardUri: String,
    @field:Json(name = "boardName") val boardName: String,
    @field:Json(name = "boardDescription") val boardDescription: String?,
    @field:Json(name = "tags") val tags: List<String>?,
    @field:Json(name = "specialSettings") val specialSettings: List<String>?,
  ) {
    val hasSfwTag: Boolean
      get() = hasTagOrSpecialSetting(value = "sfw")

    val hasNsfwTag: Boolean
      get() = hasTagOrSpecialSetting(value = "nsfw")

    private fun hasTagOrSpecialSetting(value: String): Boolean {
      if (tags?.any { tag -> tag.equals(value, ignoreCase = true) } == true) {
        return true
      }

      if (specialSettings?.any { tag -> tag.equals(value, ignoreCase = true) } == true) {
        return true
      }

      return false
    }
  }

  companion object {
    private const val TAG = "LynxchanGetBoardsUseCase"
  }

}