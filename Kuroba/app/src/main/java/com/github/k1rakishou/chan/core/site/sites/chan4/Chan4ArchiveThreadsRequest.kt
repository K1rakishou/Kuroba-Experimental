package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser
import com.github.k1rakishou.chan.core.site.sites.archive.NativeArchivePost
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.suspendCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.charset.StandardCharsets


class Chan4ArchiveThreadsRequest(
  private val request: Request,
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val simpleCommentParser: SimpleCommentParser
) {

  suspend fun execute(): ModularResult<List<NativeArchivePost>> {
    return withContext(Dispatchers.IO) {
      return@withContext ModularResult.Try {
        val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)

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

  private fun readHtml(document: Document): List<NativeArchivePost> {
    val table = document.getElementById("arc-list")
    val tableBody = table.getElementsByTag("tbody").firstOrNull()
      ?: return emptyList()
    val trs = tableBody.getElementsByTag("tr")

    val items = mutableListWithCap<NativeArchivePost>(32)

    for (tr in trs) {
      val dataElements = tr.getElementsByTag("td")

      val threadNo = dataElements.getOrNull(0)?.text()?.toLongOrNull()
        ?: continue
      val comment = dataElements.getOrNull(1)?.text()
        ?: continue

      val postComment = simpleCommentParser.parseComment(comment)
      if (postComment == null) {
        continue
      }

      items.add(NativeArchivePost.Chan4NativeArchivePost(threadNo, postComment))
    }

    return items
  }

}