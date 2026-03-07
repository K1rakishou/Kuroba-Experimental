package com.github.k1rakishou.chan.core.site.sites.vichan.leftypol

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.MultipartHttpCall
import com.github.k1rakishou.chan.core.site.sites.vichan.lainchan.LainchanActions
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.Request

class LeftypolActions(
  commonSite: CommonSite,
  proxiedOkHttpClient: ProxiedOkHttpClient,
  siteManager: SiteManager,
  replyManager: ReplyManager
) : LainchanActions(commonSite, proxiedOkHttpClient, siteManager, replyManager) {

  override suspend fun boards(): Flow<SiteBoards> {
    val requestBuilder = Request.Builder()
      .url(site.endpoints.boards().toString())

    site.requestModifier.modifyGenericRequest(site, requestBuilder)

    val siteBoards = LeftypolBoardsRequest(
      siteDescriptor = site.descriptor,
      boardManager = site.boardManager,
      request = requestBuilder.build(),
      proxiedOkHttpClient = proxiedOkHttpClient
    )
      .execute()
      .mapErrorToValue { error -> SiteBoards.Result.Error(error) }

    return flowOf(siteBoards)
  }

  override fun setupPost(replyChanDescriptor: ChanDescriptor, call: MultipartHttpCall): ModularResult<Unit> {
    call.parameter("simple_spam", "4")
    return super.setupPost(replyChanDescriptor, call)
  }
}