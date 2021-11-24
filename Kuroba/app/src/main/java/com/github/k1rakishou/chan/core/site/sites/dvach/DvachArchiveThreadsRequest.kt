package com.github.k1rakishou.chan.core.site.sites.dvach

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.site.sites.archive.NativeArchivePost
import com.github.k1rakishou.chan.core.site.sites.archive.NativeArchivePostList
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.getFirstElementByClassWithValue
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.suspendConvertIntoJsoupDocument
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

class DvachArchiveThreadsRequest(
  private val request: Request,
  private val proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>
) {

  suspend fun execute(): ModularResult<NativeArchivePostList> {
    return withContext(Dispatchers.IO) {
      return@withContext ModularResult.Try {
        val htmlDocument = proxiedOkHttpClient.get().okHttpClient()
          .suspendConvertIntoJsoupDocument(request)
          .unwrap()

        return@Try readHtml(htmlDocument)
      }
    }
  }

  private fun readHtml(document: Document): NativeArchivePostList {
    val archiveDiv = document.getFirstElementByClassWithValue("box-data")
      ?: return NativeArchivePostList()

    val links: List<Element> = archiveDiv.childNodes()
      .filter { childNode -> childNode is Element && childNode.nodeName() == "a" }
      as List<Element>

    val posts = links.mapNotNull { link ->
      // href="/mobi/arch/2020-02-05/res/11223344.html"
      val href = link.attr("href")

      val matcher = THREAD_NO_PATTERN.matcher(href)
      if (!matcher.find()) {
        return@mapNotNull null
      }

      val threadNo = matcher.groupOrNull(1)?.toLongOrNull()
        ?: return@mapNotNull null

      return@mapNotNull NativeArchivePost.DvachNativeArchivePost(
        threadNo = threadNo,
        comment =  link.text()
      )
    }

    val nextPage = extractNextPage(archiveDiv)

    return NativeArchivePostList(
      nextPage = nextPage,
      posts = posts
    )
  }

  private fun extractNextPage(archiveDiv: Element): Int? {
    val pager = archiveDiv.getFirstElementByClassWithValue("pager pager_arch")
      ?: return null

    val currentPage = pager.getElementsByTag("strong").firstOrNull()
      ?.text()
      ?.toIntOrNull()

    if (currentPage == null) {
      return null
    }

    return currentPage - 1
  }

  companion object {
    private val THREAD_NO_PATTERN = Pattern.compile("\\/(\\d+).html")
  }

}