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
package com.github.adamantcheese.chan.core.site.sites.chan420

import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.chan.core.repository.BoardRepository
import com.github.adamantcheese.chan.core.site.ChunkDownloaderSiteProperties
import com.github.adamantcheese.chan.core.site.Site
import com.github.adamantcheese.chan.core.site.SiteIcon.fromFavicon
import com.github.adamantcheese.chan.core.site.common.CommonSite
import com.github.adamantcheese.chan.core.site.common.taimaba.TaimabaActions
import com.github.adamantcheese.chan.core.site.common.taimaba.TaimabaApi
import com.github.adamantcheese.chan.core.site.common.taimaba.TaimabaCommentParser
import com.github.adamantcheese.chan.core.site.common.taimaba.TaimabaEndpoints
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.*

class Chan420 : CommonSite() {
  private val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
    siteSendsCorrectFileSizeInBytes = false,
    canFileHashBeTrusted = false
  )
  
  override fun setup() {
    setName("420Chan")
    setIcon(fromFavicon(imageLoaderV2, "https://420chan.org/favicon.ico".toHttpUrl()))
    setBoardsType(Site.BoardsType.DYNAMIC)
    setResolvable(URL_HANDLER)
    
    setConfig(object : CommonConfig() {
      override fun siteFeature(siteFeature: Site.SiteFeature): Boolean {
        // 420chan doesn't support file hashes
        return (super.siteFeature(siteFeature) && siteFeature !== Site.SiteFeature.IMAGE_FILE_HASH
          || siteFeature === Site.SiteFeature.POSTING || siteFeature === Site.SiteFeature.POST_REPORT)
      }
    })
    
    setEndpoints(TaimabaEndpoints(this, "https://api.420chan.org", "https://boards.420chan.org"))
    setActions(object : TaimabaActions(this@Chan420) {
      override suspend fun boards(): JsonReaderRequest.JsonReaderResponse<BoardRepository.SiteBoards> {
        return genericBoardsRequestResponseHandler(
          requestProvider = {
            val request = Request.Builder()
              .url(site.endpoints().boards().toString())
              .get()
              .build()
            
            Chan420BoardsRequest(
              request,
              okHttpClient
            )
          },
          defaultBoardsProvider = {
            ArrayList<Board>().apply {
              add(Board.fromSiteNameCode(this@Chan420, "Cannabis Discussion", "weed"))
              add(Board.fromSiteNameCode(this@Chan420, "Alcohol Discussion", "hooch"))
              add(Board.fromSiteNameCode(this@Chan420, "Dream Discussion", "dr"))
              add(Board.fromSiteNameCode(this@Chan420, "Detoxing & Rehabilitation", "detox"))
            }.shuffled()
          }
        )
      }
    })
    setApi(TaimabaApi(this))
    setParser(TaimabaCommentParser())
  }
  
  override fun getChunkDownloaderSiteProperties(): ChunkDownloaderSiteProperties {
    return chunkDownloaderSiteProperties
  }
  
  companion object {
    private const val TAG = "420Chan"

    @JvmStatic
    val URL_HANDLER: CommonSiteUrlHandler = object : CommonSiteUrlHandler() {
      
      override val mediaHosts = arrayOf("boards.420chan.org")
      
      override fun getSiteClass(): Class<out Site?> {
        return Chan420::class.java
      }
      
      override val url: HttpUrl
        get() = "https://420chan.org/".toHttpUrl()
      
      override val names: Array<String>
        get() = arrayOf("420chan", "420")
      
      override fun desktopUrl(loadable: Loadable, postNo: Long?): String {
        return if (loadable.isCatalogMode) {
          if (postNo != null) {
            "https://boards.420chan.org/" + loadable.boardCode + "/thread/" + postNo
          } else {
            "https://boards.420chan.org/" + loadable.boardCode + "/"
          }
        } else if (loadable.isThreadMode) {
          var url = "https://boards.420chan.org/" + loadable.boardCode + "/thread/" + loadable.no
          if (postNo != null && loadable.no.toLong() != postNo) {
            url += "#${postNo}"
          }

          url
        } else {
          "https://boards.420chan.org/" + loadable.boardCode + "/"
        }
      }
    }
  }
  
}