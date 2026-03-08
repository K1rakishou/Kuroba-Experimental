package com.github.k1rakishou.chan.core.site.sites.vichan.leftypol

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
) : LainchanActions(commonSite) {

  override suspend fun boards(): Flow<SiteBoards> {
    val requestBuilder = Request.Builder()
      .url(site.endpoints.boards().toString())

    site.requestModifier.modifyGenericRequest(site, requestBuilder)

    val siteBoards = LeftypolBoardsRequest(
      siteDescriptor = site.descriptor,
      boardManager = site.boardManager,
      proxiedOkHttpClient = site.dependencies.proxiedOkHttpClient,
      request = requestBuilder.build()
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