package com.github.k1rakishou.chan.core.site.sites.yukila

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.site.common.CommonClientException
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.parser.processor.ChanReaderProcessor
import com.github.k1rakishou.chan.core.site.parser.processor.IChanReaderProcessor
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import com.github.k1rakishou.model.data.archive.ArchivePost
import com.github.k1rakishou.model.data.archive.ArchivePostMedia
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.FilterWatchCatalogInfoObject
import com.github.k1rakishou.model.mapper.ArchiveThreadMapper
import java.io.InputStream

class YukilaApi(
  site: CommonSite
) : CommonSite.CommonApi(site) {

  private val verboseLogs by lazy { ChanSettings.verboseLogs.get() }

  private val threadParseCommandBuffer = YukilaApiThreadPostParseCommandBufferBuilder(verboseLogs)
    .getBuilder()
    .build()

  override suspend fun loadThreadFresh(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    readBodyHtml(requestUrl, responseBodyStream) { document ->
      require(chanReaderProcessor.chanDescriptor is ChanDescriptor.ThreadDescriptor) {
        "Cannot load catalogs here!"
      }

      val threadDescriptor = chanReaderProcessor.chanDescriptor
      val collector = ArchiveThreadPostCollector(threadDescriptor)
      val parserCommandExecutor = KurobaHtmlParserCommandExecutor<ArchiveThreadPostCollector>()

      try {
        parserCommandExecutor.executeCommands(
          document,
          threadParseCommandBuffer,
          collector,
          url = requestUrl
        )
      } catch (error: Throwable) {
        Logger.e(TAG, "parserCommandExecutor.executeCommands() error", error)
        return@readBodyHtml
      }

      val postBuilders = collector.archivePosts.map { archivePost ->
        return@map ArchiveThreadMapper.fromPost(threadDescriptor.boardDescriptor, archivePost)
      }

      val originalPost = postBuilders.firstOrNull()
      if (originalPost == null || !originalPost.op) {
        Logger.e(TAG, "Failed to parse original post or first post is not original post for some reason")
        return@readBodyHtml
      }

      chanReaderProcessor.setOp(originalPost)
      postBuilders.forEach { chanPostBuilder -> chanReaderProcessor.addPost(chanPostBuilder) }
    }

    chanReaderProcessor.applyChanReadOptions()
  }

  override suspend fun loadCatalog(
    requestUrl: String,
    responseBodyStream: InputStream,
    chanReaderProcessor: IChanReaderProcessor
  ) {
    throw CommonClientException("Catalog is not supported for site ${site.name()}")
  }

  override suspend fun readThreadBookmarkInfoObject(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    expectedCapacity: Int,
    requestUrl: String,
    responseBodyStream: InputStream,
  ): ModularResult<ThreadBookmarkInfoObject> {
    val error = CommonClientException("Bookmarks are not supported for site ${site.name()}")

    return ModularResult.error(error)
  }

  override suspend fun readFilterWatchCatalogInfoObject(
    boardDescriptor: BoardDescriptor,
    requestUrl: String,
    responseBodyStream: InputStream,
  ): ModularResult<FilterWatchCatalogInfoObject> {
    val error = CommonClientException("Filter watching is not supported for site ${site.name()}")

    return ModularResult.error(error)
  }

  data class ArchiveThreadPostCollector(
    val threadDescriptor: ChanDescriptor.ThreadDescriptor,
    val archivePosts: MutableList<ArchivePost> = mutableListOf()
  ) : KurobaHtmlParserCollector {

    fun lastPostOrNull(): ArchivePost? {
      return archivePosts.lastOrNull()
    }

    fun lastMediaOrNull(): ArchivePostMedia? {
      return archivePosts.lastOrNull()?.archivePostMediaList?.lastOrNull()
    }

  }

  companion object {
    private const val TAG = "YukilaApi"
  }
}