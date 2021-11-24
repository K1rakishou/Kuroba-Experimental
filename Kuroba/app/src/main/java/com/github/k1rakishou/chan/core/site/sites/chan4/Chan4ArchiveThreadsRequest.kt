package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.site.sites.archive.NativeArchivePost
import com.github.k1rakishou.chan.core.site.sites.archive.NativeArchivePostList
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.suspendCall
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.charset.StandardCharsets


class Chan4ArchiveThreadsRequest(
  private val request: Request,
  private val proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>
) {

  suspend fun execute(): ModularResult<NativeArchivePostList> {
    return withContext(Dispatchers.IO) {
      return@withContext ModularResult.Try {
        val response = proxiedOkHttpClient.get().okHttpClient().suspendCall(request)

        if (!response.isSuccessful) {
          throw BadStatusResponseException(response.code)
        }

        if (response.body == null) {
          throw EmptyBodyResponseException()
        }

        return@Try response.body!!.use { body ->
          return@use body.byteStream().use { inputStream ->
            val url = request.url.toString()

            val htmlDocument = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), url)
            return@use readHtml(htmlDocument)
          }
        }
      }
    }
  }

  private fun readHtml(document: Document): NativeArchivePostList {
    val table = document.getElementById("arc-list")
      ?: return NativeArchivePostList()
    val tableBody = table.getElementsByTag("tbody").firstOrNull()
      ?: return NativeArchivePostList()
    val trs = tableBody.getElementsByTag("tr")

    val posts = mutableListWithCap<NativeArchivePost>(32)

    for (tr in trs) {
      val dataElements = tr.getElementsByTag("td")

      val threadNo = dataElements.getOrNull(0)?.text()?.toLongOrNull()
        ?: continue
      val comment = dataElements.getOrNull(1)?.text()
        ?: continue

      posts.add(NativeArchivePost.Chan4NativeArchivePost(threadNo, comment))
    }

    return NativeArchivePostList(
      nextPage = null,
      posts = posts
    )
  }

}