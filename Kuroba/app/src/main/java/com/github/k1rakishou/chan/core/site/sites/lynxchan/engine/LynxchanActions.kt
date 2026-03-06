package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.http.HttpCallManager
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import com.github.k1rakishou.persist_state.ReplyMode
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrl

open class LynxchanActions(
  private val replyManager: ReplyManager,
  private val moshi: Moshi,
  private val httpCallManager: HttpCallManager,
  private val lynxchanGetBoardsUseCase: LynxchanGetBoardsUseCase,
  site: BaseLynxchanSite
) : CommonSite.CommonActions(site) {
  private val lynxchanSite: BaseLynxchanSite
    get() = site as BaseLynxchanSite

  override suspend fun boards(): Flow<SiteBoards> {
    val getBoardsEndpoint = site.endpoints.boards()
    if (getBoardsEndpoint == null) {
      return flowOf(SiteBoards.Result.Error(NullPointerException("Site.boards() returned null")))
    }

    val params = LynxchanGetBoardsUseCase.Params(
      site = lynxchanSite,
      getBoardsEndpoint = getBoardsEndpoint
    )

    return lynxchanGetBoardsUseCase.execute(params)
  }

  override suspend fun pages(board: ChanBoard): JsonReaderRequest.JsonReaderResponse<BoardPages>? {
    return null
  }

  override fun requirePrepare(): Boolean = false

  override suspend fun post(replyChanDescriptor: ChanDescriptor, replyMode: ReplyMode): Flow<SiteActions.PostResult> {
    val replyCall = LynxchanReplyHttpCall(
      site = lynxchanSite,
      replyChanDescriptor = replyChanDescriptor,
      replyManager = replyManager,
      moshi = moshi,
    )

    return httpCallManager.makePostHttpCallWithProgress(replyCall, replyChanDescriptor)
      .map { replyCallResult ->
        when (replyCallResult) {
          is HttpCall.HttpCallWithProgressResult.Success -> {
            return@map SiteActions.PostResult.PostComplete(
              replyCallResult.httpCall.replyResponse
            )
          }
          is HttpCall.HttpCallWithProgressResult.Progress -> {
            return@map SiteActions.PostResult.UploadingProgress(
              replyCallResult.fileIndex,
              replyCallResult.totalFiles,
              replyCallResult.percent
            )
          }
          is HttpCall.HttpCallWithProgressResult.Fail -> {
            return@map SiteActions.PostResult.PostError(replyCallResult.error)
          }
        }
      }
  }

  override suspend fun delete(deleteRequest: DeleteRequest): SiteActions.DeleteResult {
    return SiteActions.DeleteResult.DeleteError(NotImplementedError())
  }

  override suspend fun <T : AbstractLoginRequest> login(loginRequest: T): SiteActions.LoginResult {
    return SiteActions.LoginResult.LoginError("Not implemented")
  }

  override fun postAuthenticate(): SiteAuthentication {
    val domain = lynxchanSite.domainString

    val customCaptcha = SiteAuthentication.CustomCaptcha.LynxchanCaptcha(
      captchaEndpoint = "${domain}/captcha.js".toHttpUrl(),
      solveCaptchaEndpoint = "${domain}/solveCaptcha.js".toHttpUrl(),
      bypassEndpoint = "${domain}/blockBypass.js?json=1".toHttpUrl(),
      renewBypassEndpoint = "${domain}/renewBypass.js?json=1".toHttpUrl(),
      validateBypassEndpoint = "${domain}/validateBypass.js?json=1".toHttpUrl(),
      hashCashValidationEndpoint = "${domain}/addon.js/hashcash/?action=get".toHttpUrl(),
    )

    return SiteAuthentication.customCaptcha(customCaptcha = customCaptcha)
  }

  override fun logout() {
  }

  override fun isLoggedIn(): Boolean = false

  override fun loginDetails(): AbstractLoginRequest? = null
}