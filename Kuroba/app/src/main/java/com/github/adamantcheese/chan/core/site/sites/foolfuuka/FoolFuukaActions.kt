package com.github.adamantcheese.chan.core.site.sites.foolfuuka

import com.github.adamantcheese.chan.core.model.SiteBoards
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.chan.core.site.SiteActions
import com.github.adamantcheese.chan.core.site.SiteAuthentication
import com.github.adamantcheese.chan.core.site.common.CommonClientException
import com.github.adamantcheese.chan.core.site.common.CommonSite
import com.github.adamantcheese.chan.core.site.common.MultipartHttpCall
import com.github.adamantcheese.chan.core.site.http.DeleteRequest
import com.github.adamantcheese.chan.core.site.http.Reply
import com.github.adamantcheese.chan.core.site.http.ReplyResponse
import com.github.adamantcheese.chan.core.site.http.login.AbstractLoginRequest
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.data.board.ChanBoard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FoolFuukaActions(site: CommonSite) : CommonSite.CommonActions(site) {

  override suspend fun post(reply: Reply): Flow<SiteActions.PostResult> {
    return flow {
      val error = CommonClientException("Posting is not supported for site ${site.name()}")
      emit(SiteActions.PostResult.PostError(error))
    }
  }

  override fun setupPost(reply: Reply, call: MultipartHttpCall): ModularResult<Unit> {
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
    reply: Reply,
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

  override suspend fun pages(board: ChanBoard): JsonReaderRequest.JsonReaderResponse<Chan4PagesRequest.BoardPages> {
    val error = CommonClientException("Pages are not supported for site ${site.name()}")

    return JsonReaderRequest.JsonReaderResponse.UnknownServerError(error)
  }

  override suspend fun <T : AbstractLoginRequest> login(loginRequest: T): SiteActions.LoginResult {
    return SiteActions.LoginResult.LoginError(
      "Login is not supported for site ${site.name()}"
    )
  }
}