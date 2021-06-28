package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.source.local.ChanPostImageLocalSource
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl

class ChanPostImageRepository(
  database: KurobaDatabase,
  private val isDevFlavor: Boolean,
  private val applicationScope: CoroutineScope,
  private val chanPostImageLocalSource: ChanPostImageLocalSource
) : AbstractRepository(database) {
  private val TAG = "ChanPostImageRepository"

  suspend fun selectPostImageByUrl(imagesUrl: HttpUrl): ModularResult<ChanPostImage?> {
    return selectPostImagesByUrls(listOf(imagesUrl))
      .mapValue { chanPostImages -> chanPostImages.firstOrNull() }
  }

  suspend fun selectPostImagesByUrls(imagesUrls: Collection<HttpUrl>): ModularResult<List<ChanPostImage>> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction chanPostImageLocalSource.selectPostImagesByUrls(imagesUrls)
      }
    }
  }

  suspend fun selectPostImagesByOwnerThreadDatabaseId(threadDatabaseId: Long): ModularResult<List<ChanPostImage>> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction chanPostImageLocalSource.selectPostImagesByOwnerThreadDatabaseId(threadDatabaseId)
      }
    }
  }

}