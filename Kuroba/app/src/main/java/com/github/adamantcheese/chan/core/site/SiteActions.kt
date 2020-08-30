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
package com.github.adamantcheese.chan.core.site

import com.github.adamantcheese.chan.core.model.SiteBoards
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.chan.core.site.http.DeleteRequest
import com.github.adamantcheese.chan.core.site.http.DeleteResponse
import com.github.adamantcheese.chan.core.site.http.Reply
import com.github.adamantcheese.chan.core.site.http.ReplyResponse
import com.github.adamantcheese.chan.core.site.http.login.AbstractLoginRequest
import com.github.adamantcheese.chan.core.site.http.login.AbstractLoginResponse
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest
import com.github.adamantcheese.model.data.board.ChanBoard
import kotlinx.coroutines.flow.Flow

interface SiteActions {
  suspend fun boards(): JsonReaderRequest.JsonReaderResponse<SiteBoards>
  suspend fun pages(board: ChanBoard): JsonReaderRequest.JsonReaderResponse<Chan4PagesRequest.BoardPages>
  suspend fun post(reply: Reply): Flow<PostResult>
  suspend fun delete(deleteRequest: DeleteRequest): DeleteResult
  suspend fun <T : AbstractLoginRequest> login(loginRequest: T): LoginResult
  fun postRequiresAuthentication(): Boolean
  fun postAuthenticate(): SiteAuthentication
  fun logout()
  fun isLoggedIn(): Boolean
  fun loginDetails(): AbstractLoginRequest?

  enum class LoginType {
    Passcode,
    TokenAndPass
  }

  sealed class PostResult {
    class PostComplete(val replyResponse: ReplyResponse) : PostResult()
    class UploadingProgress(val percent: Int) : PostResult()
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
}