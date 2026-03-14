package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaMainDatabase
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.source.local.ChanPostImageLocalSource
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl

class ChanPostImageRepository(
  database: KurobaMainDatabase,
  applicationScope: CoroutineScope,
  private val isDevFlavor: Boolean,
  private val chanPostImageLocalSource: ChanPostImageLocalSource
) : AbstractRepository(database, applicationScope) {
  private val TAG = "ChanPostImageRepository"

  suspend fun selectPostImageByUrl(imagesUrl: HttpUrl): ModularResult<ChanPostImage?> {
    return selectPostImagesByUrls(listOf(imagesUrl))
      .mapValue { chanPostImages -> chanPostImages.firstOrNull() }
  }

  suspend fun selectPostImagesByUrls(imagesUrls: Collection<HttpUrl>): ModularResult<List<ChanPostImage>> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction chanPostImageLocalSource.selectPostImagesByUrls(imagesUrls)
      }
    }
  }

  suspend fun selectPostImagesByOwnerThreadDatabaseId(threadDatabaseId: Long): ModularResult<List<ChanPostImage>> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction chanPostImageLocalSource.selectPostImagesByOwnerThreadDatabaseId(threadDatabaseId)
      }
    }
  }

  suspend fun countPostImagesByOwnerThreadDatabaseId(threadDatabaseId: Long): ModularResult<Int> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction chanPostImageLocalSource.countPostImagesByOwnerThreadDatabaseId(threadDatabaseId)
      }
    }
  }

}