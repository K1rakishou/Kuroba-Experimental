package com.github.k1rakishou.chan.core.site.sites.fuuka

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.net.HtmlReaderRequest
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.common.CommonClientException
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.MultipartHttpCall
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.chan.core.site.sites.search.FuukaSearchParams
import com.github.k1rakishou.chan.core.site.sites.search.SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import com.github.k1rakishou.persist_state.ReplyMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.HttpUrl
import okhttp3.Request

class FuukaActions(site: CommonSite) : CommonSite.CommonActions(site) {

  override suspend fun post(replyChanDescriptor: ChanDescriptor, replyMode: ReplyMode): Flow<SiteActions.PostResult> {
    return flow {
      val error = CommonClientException("Posting is not supported for site ${site.name()}")
      emit(SiteActions.PostResult.PostError(error))
    }
  }

  override fun setupPost(replyChanDescriptor: ChanDescriptor, call: MultipartHttpCall): ModularResult<Unit> {
    val error = CommonClientException("Posting is not supported for site ${site.name()}")

    return ModularResult.error(error)
  }

  override fun postRequiresAuthentication(): Boolean {
    return false
  }

  override fun postAuthenticate(): SiteAuthentication {
    return SiteAuthentication.fromNone()
  }

  override fun requirePrepare(): Boolean {
    return false
  }

  override suspend fun prepare(
    call: MultipartHttpCall,
    replyChanDescriptor: ChanDescriptor,
    replyResponse: ReplyResponse
  ): ModularResult<Unit> {
    val error = CommonClientException("Posting is not supported for site ${site.name()}")

    return ModularResult.error(error)
  }

  override suspend fun delete(deleteRequest: DeleteRequest): SiteActions.DeleteResult {
    val error = CommonClientException("Post deletion is not supported for site ${site.name()}")

    return SiteActions.DeleteResult.DeleteError(error)
  }

  override suspend fun boards(): JsonReaderRequest.JsonReaderResponse<SiteBoards> {
    val error = CommonClientException("Catalog is not supported for site ${site.name()}")

    return JsonReaderRequest.JsonReaderResponse.UnknownServerError(error)
  }

  override suspend fun pages(board: ChanBoard): JsonReaderRequest.JsonReaderResponse<BoardPages> {
    val error = CommonClientException("Pages are not supported for site ${site.name()}")

    return JsonReaderRequest.JsonReaderResponse.UnknownServerError(error)
  }

  override suspend fun <T : AbstractLoginRequest> login(loginRequest: T): SiteActions.LoginResult {
    return SiteActions.LoginResult.LoginError(
      "Login is not supported for site ${site.name()}"
    )
  }

  override suspend fun <T : SearchParams> search(
    searchParams: T
  ): HtmlReaderRequest.HtmlReaderResponse<SearchResult> {
    searchParams as FuukaSearchParams

    // https://warosu.org/g/?offset=0&ghost=no&task=search&search_text=test&search_subject=test123
    val searchUrl = requireNotNull(site.endpoints().search())
      .newBuilder()
      .addEncodedPathSegment(searchParams.boardDescriptor.boardCode)
      .addQueryParameter("offset", (searchParams.getCurrentPage() * FUUKA_SEARCH_ENTRIES_PER_PAGE).toString())
      // TODO(KurobaEx / @GhostPosts): ghost posts are not supported yet
      .addQueryParameter("ghost", "no")
      .addQueryParameter("task", "search")
      .tryAddSearchParam("search_text", searchParams.query)
      .tryAddSearchParam("search_subject", searchParams.subject)
      .build()

    val requestBuilder = Request.Builder()
      .url(searchUrl)
      .get()

    site.requestModifier().modifySearchGetRequest(site, requestBuilder)

    return FuukaSearchRequest(
      ChanSettings.verboseLogs.get(),
      searchParams,
      requestBuilder.build(),
      site.proxiedOkHttpClient
    ).execute()
  }

  private fun HttpUrl.Builder.tryAddSearchParam(paramName: String, paramValue: String): HttpUrl.Builder {
    if (paramValue.isEmpty()) {
      return this
    }

    return this.addQueryParameter(paramName, paramValue)
  }

  companion object {
    private const val FUUKA_SEARCH_ENTRIES_PER_PAGE = 24
  }

}