package com.github.k1rakishou.chan.core.site.parser

import com.github.k1rakishou.chan.core.site.parser.processor.AbstractChanReaderProcessor
import com.github.k1rakishou.chan.core.site.parser.processor.ChanReaderProcessor
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.FilterWatchCatalogInfoObject
import com.google.gson.stream.JsonReader
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

abstract class SiteApi {
  abstract suspend fun getParser(): PostParser?

  @Throws(Exception::class)
  abstract suspend fun loadThreadFresh(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: ChanReaderProcessor
  )

  @Throws(Exception::class)
  open suspend fun loadThreadIncremental(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    // For most sites it's the same as the loadThreadFresh. For now only 2ch.hk supports incremental
    // thread updates.
    loadThreadFresh(requestUrl, responseBodyStream, chanReaderProcessor)
  }

  @Throws(Exception::class)
  abstract suspend fun loadCatalog(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: AbstractChanReaderProcessor
  )

  abstract suspend fun readThreadBookmarkInfoObject(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    expectedCapacity: Int,
    requestUrl: String,
    responseBodyStream: InputStream,
  ): ModularResult<ThreadBookmarkInfoObject>

  abstract suspend fun readFilterWatchCatalogInfoObject(
    boardDescriptor: BoardDescriptor,
    requestUrl: String,
    responseBodyStream: InputStream,
  ): ModularResult<FilterWatchCatalogInfoObject>

  protected suspend fun readBodyJson(
    inputStream: InputStream,
    reader: suspend (JsonReader) -> Unit
  ) {
    JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { jsonReader ->
      reader(jsonReader)
    }
  }

  protected suspend fun readBodyHtml(
    requestUrl: String,
    responseBodyStream: InputStream,
    reader: suspend (Document) -> Unit
  ) {
    val htmlDocument = Jsoup.parse(
      responseBodyStream,
      StandardCharsets.UTF_8.name(),
      requestUrl
    )

    reader(htmlDocument)
  }

  companion object {
    const val DEFAULT_POST_LIST_CAPACITY = 16
  }
}