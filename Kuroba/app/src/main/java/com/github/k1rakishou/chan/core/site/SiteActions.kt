package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.DeleteResponse
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginResponse
import com.github.k1rakishou.chan.core.site.http.report.PostReportData
import com.github.k1rakishou.chan.core.site.http.report.PostReportResult
import com.github.k1rakishou.chan.core.site.limitations.PasscodePostingLimitationsInfo
import com.github.k1rakishou.chan.core.site.sites.archive.NativeArchivePostList
import com.github.k1rakishou.chan.core.site.sites.search.SearchError
import com.github.k1rakishou.chan.core.site.sites.search.SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import com.github.k1rakishou.persist_state.ReplyMode
import kotlinx.coroutines.flow.Flow

interface SiteActions {
  suspend fun boards(): Flow<SiteBoards>
  suspend fun pages(board: ChanBoard): JsonReaderRequest.JsonReaderResponse<BoardPages>?
  suspend fun post(replyChanDescriptor: ChanDescriptor, replyMode: ReplyMode): Flow<PostResult>
  suspend fun delete(deleteRequest: DeleteRequest): DeleteResult
  suspend fun <T : AbstractLoginRequest> login(loginRequest: T): LoginResult
  fun postAuthenticate(): SiteAuthentication
  fun logout()
  fun isLoggedIn(): Boolean
  fun loginDetails(): AbstractLoginRequest?

  suspend fun <T : SearchParams> search(searchParams: T): SearchResult =
    SearchResult.Failure(SearchError.NotImplemented)

  suspend fun archive(
    boardDescriptor: BoardDescriptor,
    page: Int?
  ): ModularResult<NativeArchivePostList> = ModularResult.value(NativeArchivePostList())

  suspend fun getOrRefreshPasscodeInfo(resetCached: Boolean): GetPasscodeInfoResult? = null

  suspend fun <T : PostReportData> reportPost(
    postReportData: T
  ): PostReportResult = PostReportResult.NotSupported

  suspend fun checkPostExists(
    chanDescriptor: ChanDescriptor,
    replyPostDescriptor: PostDescriptor
  ): ModularResult<Boolean> = ModularResult.value(true)

  fun clearPostingCookies() = Unit

  enum class LoginType {
    Passcode,
    TokenAndPass
  }

  sealed class PostResult {
    class PostComplete(val replyResponse: ReplyResponse) : PostResult()
    class UploadingProgress(val fileIndex: Int, val totalFiles: Int, val percent: Int) : PostResult()
    class PostError(val error: Throwable) : PostResult()
  }

  sealed class DeleteResult {
    class DeleteComplete(val deleteResponse: DeleteResponse) : DeleteResult()
    class DeleteError(val error: Throwable) : DeleteResult()
  }

  sealed class LoginResult {
    class LoginComplete(val loginResponse: AbstractLoginResponse) : LoginResult()
    class LoginError(val errorMessage: String) : LoginResult()
  }

  sealed class GetPasscodeInfoResult {
    data object NotLoggedIn : GetPasscodeInfoResult()
    data object NotAllowedToRefreshFromNetwork : GetPasscodeInfoResult()
    class Success(val postingLimitationsInfo: PasscodePostingLimitationsInfo) : GetPasscodeInfoResult()
    class Failure(val error: Throwable) : GetPasscodeInfoResult()
  }
}