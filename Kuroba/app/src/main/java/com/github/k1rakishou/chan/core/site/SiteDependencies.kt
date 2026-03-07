package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.repository.BoardFlagInfoRepository
import com.github.k1rakishou.chan.core.site.http.HttpCallManager
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanGetBoardsUseCase
import com.github.k1rakishou.common.AppConstants
import com.google.gson.Gson
import com.squareup.moshi.Moshi

interface SiteDependencies {
  val gson: Gson
  val appConstants: AppConstants
  val boardManager: BoardManager
  val siteManager: SiteManager
  val proxiedOkHttpClient: ProxiedOkHttpClient
  val httpCallManager: HttpCallManager
  val moshi: Moshi
  val imageLoaderDeprecated: ImageLoaderDeprecated
  val archivesManager: ArchivesManager
  val postFilterManager: PostFilterManager
  val replyManager: ReplyManager
  val boardFlagInfoRepository: BoardFlagInfoRepository
  val chanThreadManager: ChanThreadManager
  val lynxchanGetBoardsUseCase: LynxchanGetBoardsUseCase
}