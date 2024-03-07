package com.github.k1rakishou.chan.core.site.sites.leftypol

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.MultipartHttpCall
import com.github.k1rakishou.chan.core.site.sites.lainchan.LainchanActions
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import dagger.Lazy
import okhttp3.Request

class LeftypolActions(
        commonSite: CommonSite,
        proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>,
        siteManager: SiteManager,
        replyManager: Lazy<ReplyManager>
) : LainchanActions(commonSite, proxiedOkHttpClient, siteManager, replyManager) {

    override suspend fun boards(): ModularResult<SiteBoards> {
        val requestBuilder = Request.Builder()
                .url(site.endpoints().boards().toString())

        site.requestModifier().modifyBoardsGetRequest(requestBuilder)

        return LeftypolBoardsRequest(
                siteDescriptor = site.siteDescriptor(),
                boardManager = site.boardManager,
                request = requestBuilder.build(),
                proxiedOkHttpClient = proxiedOkHttpClient
        ).execute()
    }

    override fun setupPost(replyChanDescriptor: ChanDescriptor, call: MultipartHttpCall): ModularResult<Unit> {
        call.parameter("simple_spam", "4")
        return super.setupPost(replyChanDescriptor, call)
    }
}