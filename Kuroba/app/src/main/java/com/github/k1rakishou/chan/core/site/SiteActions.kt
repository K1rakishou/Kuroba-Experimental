/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
import com.github.k1rakishou.model.data.site.SiteBoards
import com.github.k1rakishou.persist_state.ReplyMode
import kotlinx.coroutines.flow.Flow

interface SiteActions {
  suspend fun boards(): ModularResult<SiteBoards>
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
    object NotLoggedIn : GetPasscodeInfoResult()
    object NotAllowedToRefreshFromNetwork : GetPasscodeInfoResult()
    class Success(val postingLimitationsInfo: PasscodePostingLimitationsInfo) : GetPasscodeInfoResult()
    class Failure(val error: Throwable) : GetPasscodeInfoResult()
  }
}