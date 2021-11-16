package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import com.github.k1rakishou.persist_state.ReplyMode
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

open class LynxchanActions(
  private val lynxchanGetBoardsUseCaseLazy: Lazy<LynxchanGetBoardsUseCase>,
  site: LynxchanSite
) : CommonSite.CommonActions(site) {
  private val lynxchanGetBoardsUseCase: LynxchanGetBoardsUseCase
    get() = lynxchanGetBoardsUseCaseLazy.get()

  override suspend fun boards(): ModularResult<SiteBoards> {
    val getBoardsEndpoint = site.endpoints().boards()
    if (getBoardsEndpoint == null) {
      return ModularResult.error(NullPointerException("Site.boards() returned null"))
    }

    val params = LynxchanGetBoardsUseCase.Params(
      siteDescriptor = site.siteDescriptor(),
      getBoardsEndpoint = getBoardsEndpoint
    )

    return lynxchanGetBoardsUseCase.execute(params)
  }

  override suspend fun pages(board: ChanBoard): JsonReaderRequest.JsonReaderResponse<BoardPages>? {
    // TODO(KurobaEx-lynxchan):
    return null
  }

  override suspend fun post(replyChanDescriptor: ChanDescriptor, replyMode: ReplyMode): Flow<SiteActions.PostResult> {
    // TODO(KurobaEx-lynxchan):
    return flow { emit(SiteActions.PostResult.PostError(NotImplementedError())) }
  }

  override suspend fun delete(deleteRequest: DeleteRequest): SiteActions.DeleteResult {
    // TODO(KurobaEx-lynxchan):
    return SiteActions.DeleteResult.DeleteError(NotImplementedError())
  }

  override suspend fun <T : AbstractLoginRequest> login(loginRequest: T): SiteActions.LoginResult {
    // TODO(KurobaEx-lynxchan):
    return SiteActions.LoginResult.LoginError("Not implemented")
  }

  override fun postRequiresAuthentication(): Boolean {
    // TODO(KurobaEx-lynxchan):
    return true
  }

  override fun postAuthenticate(): SiteAuthentication {
    // TODO(KurobaEx-lynxchan):
    return SiteAuthentication.fromNone()
  }

  override fun logout() {
  }

  override fun isLoggedIn(): Boolean = false

  override fun loginDetails(): AbstractLoginRequest? = null
}