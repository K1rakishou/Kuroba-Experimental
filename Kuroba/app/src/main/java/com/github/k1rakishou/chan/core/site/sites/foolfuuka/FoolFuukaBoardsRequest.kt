package com.github.k1rakishou.chan.core.site.sites.foolfuuka

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.net.AbstractRequest
import com.github.k1rakishou.common.ParsingException
import com.github.k1rakishou.common.useHtmlReader
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import dagger.Lazy
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

class FoolFuukaBoardsRequest(
  private val siteDescriptor: SiteDescriptor,
  private val boardManager: BoardManager,
  request: Request,
  proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>
) : AbstractRequest<SiteBoards>(request, proxiedOkHttpClient) {

  override suspend fun processBody(responseBody: ResponseBody): SiteBoards {
    return responseBody.useHtmlReader(request.url.toString()) { document -> readHtml(document) }
  }

  private fun readHtml(document: Document): SiteBoards {
    val elements = document.getElementsByAttributeValueContaining("class", "pull-left")

    val archivesRootElement = elements.firstOrNull { element ->
      return@firstOrNull element.getElementsByTag("h2").any { node ->
        return@any node.childNodes().any { childNode ->
          return@any childNode is TextNode && childNode.text().contains("Archives", ignoreCase = true)
        }
      }
    }

    if (archivesRootElement == null) {
      throw ParsingException("Failed to find archive boards")
    }

    val boards = archivesRootElement.getElementsByTag("a").mapNotNull { aTag ->
      val boardCodeNode = aTag.childNodes().firstOrNull { child ->
        if (child !is TextNode) {
          return@firstOrNull false
        }

        val text = child.text().trim()

        return@firstOrNull text.startsWith("/") && text.endsWith("/")
      } as TextNode?

      if (boardCodeNode == null) {
        return@mapNotNull null
      }

      val boardName = aTag.childNodes()
        .firstOrNull { child -> child is Element }
        ?.childNodes()
        ?.firstOrNull { child -> child is TextNode }
        ?.let { textNode -> (textNode as TextNode).text() }

      if (boardName.isNullOrEmpty()) {
        return@mapNotNull null
      }

      val boardCode = boardCodeNode.text()
        .trim()
        .removePrefix("/")
        .removeSuffix("/")

      val boardDescriptor = BoardDescriptor.create(siteDescriptor, boardCode)

      return@mapNotNull ChanBoard(
        boardDescriptor = boardDescriptor,
        name = boardName,
        perPage = 10,
        pages = Int.MAX_VALUE,
        isUnlimitedCatalog = true
      )
    }

    return SiteBoards(siteDescriptor, boards)
  }

}