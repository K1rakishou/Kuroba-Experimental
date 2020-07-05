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

import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.chan.core.repository.BoardRepository
import com.github.adamantcheese.chan.core.site.http.*
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest
import kotlinx.coroutines.flow.Flow

interface SiteActions {
  suspend fun boards(): JsonReaderRequest.JsonReaderResponse<BoardRepository.SiteBoards>
  suspend fun pages(board: Board): JsonReaderRequest.JsonReaderResponse<Chan4PagesRequest.BoardPages>
  suspend fun post(reply: Reply): Flow<PostResult>
  suspend fun delete(deleteRequest: DeleteRequest): DeleteResult
  suspend fun login(loginRequest: LoginRequest): LoginResult
  fun postRequiresAuthentication(): Boolean

  /**
   * If [ReplyResponse.requireAuthentication] was `true`, or if
   * [.postRequiresAuthentication] is `true`, get the authentication
   * required to post.
   *
   *
   *
   * Some sites know beforehand if you need to authenticate, some sites only report it
   * after posting. That's why there are two methods.
   *
   * @return an [SiteAuthentication] model that describes the way to authenticate.
   */
  fun postAuthenticate(): SiteAuthentication
  fun logout()
  fun isLoggedIn(): Boolean
  fun loginDetails(): LoginRequest?

  sealed class PostResult {
    class PostComplete(val httpCall: HttpCall, val replyResponse: ReplyResponse) : PostResult()
    class UploadingProgress(val percent: Int) : PostResult()
    class PostError(val httpCall: HttpCall, val error: Throwable) : PostResult()
  }

  sealed class DeleteResult {
    class DeleteComplete(val httpCall: HttpCall, val deleteResponse: DeleteResponse) : DeleteResult()
    class DeleteError(val httpCall: HttpCall, val error: Throwable) : DeleteResult()
  }

  sealed class LoginResult {
    class LoginComplete(val httpCall: HttpCall, val loginResponse: LoginResponse) : LoginResult()
    class LoginError(val httpCall: HttpCall, val error: Throwable) : LoginResult()
  }
}