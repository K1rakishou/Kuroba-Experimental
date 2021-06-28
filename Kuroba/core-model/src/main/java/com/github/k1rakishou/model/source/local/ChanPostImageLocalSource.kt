package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.id.PostDBId
import com.github.k1rakishou.model.data.id.ThreadDBId
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.mapper.ChanPostImageMapper
import com.github.k1rakishou.model.source.cache.ChanDescriptorCache
import okhttp3.HttpUrl

class ChanPostImageLocalSource(
  database: KurobaDatabase,
  private val chanDescriptorCache: ChanDescriptorCache
) : AbstractLocalSource(database) {
  private val TAG = "ChanPostImageLocalSource"
  private val chanPostImageDao = database.chanPostImageDao()

  suspend fun selectPostImagesByUrls(imagesUrls: Collection<HttpUrl>): List<ChanPostImage> {
    ensureInTransaction()

    val chanPostImageEntities = imagesUrls
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> chanPostImageDao.selectByImageUrlMany(chunk) }

    val chanPostOwnerIds = chanPostImageEntities
      .map { chanPostImageEntity -> chanPostImageEntity.ownerPostId }

    val postDescriptorsMap = chanDescriptorCache.getManyPostDescriptors(
      chanPostOwnerIds
        .map { chanPostOwnerId -> PostDBId(chanPostOwnerId) }
        .toSet()
    )

    return chanPostImageEntities.mapNotNull { chanPostImageEntity ->
      val postDescriptor = postDescriptorsMap[PostDBId(chanPostImageEntity.ownerPostId)]
        ?: return@mapNotNull null

      return@mapNotNull ChanPostImageMapper.fromEntity(
        chanPostImageEntity,
        postDescriptor
      )
    }
  }

  suspend fun selectPostImagesByOwnerThreadDatabaseId(threadDatabaseId: Long): List<ChanPostImage> {
    ensureInTransaction()

    val postDescriptorsMap = chanDescriptorCache.getManyPostDescriptors(ThreadDBId(threadDatabaseId))

    return chanPostImageDao.selectByOwnerThreadId(threadDatabaseId).mapNotNull { chanPostImageEntity ->
      val postDescriptor = postDescriptorsMap[PostDBId(chanPostImageEntity.ownerPostId)]
        ?: return@mapNotNull null

      return@mapNotNull ChanPostImageMapper.fromEntity(
        chanPostImageEntity,
        postDescriptor
      )
    }
  }

}