package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.thread.ChanThreadViewableInfo
import com.github.k1rakishou.model.mapper.ChanThreadViewableInfoMapper
import com.github.k1rakishou.model.source.cache.ChanDescriptorCache
import com.github.k1rakishou.model.source.cache.GenericSuspendableCacheSource

class ChanThreadViewableInfoLocalSource(
  database: KurobaDatabase,
  private val chanDescriptorCache: ChanDescriptorCache
) : AbstractLocalSource(database) {
  private val TAG = "ChanThreadViewableInfoLocalSource"
  private val chanThreadViewableInfoDao = database.chanThreadViewableInfoDao()
  private val chanThreadViewableInfoCache =
    GenericSuspendableCacheSource<ChanDescriptor.ThreadDescriptor, ChanThreadViewableInfo>()

  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ChanThreadViewableInfo? {
    ensureInTransaction()

    val fromCache = chanThreadViewableInfoCache.get(threadDescriptor)
    if (fromCache != null) {
      return fromCache
    }

    val threadId = chanDescriptorCache.getThreadIdByThreadDescriptor(threadDescriptor)
      ?: return null

    val chanThreadViewableInfoEntity = chanThreadViewableInfoDao.selectByOwnerThreadId(threadId)
      ?: return null

    val chanThreadViewableInfo = ChanThreadViewableInfoMapper.fromEntity(
      threadDescriptor,
      chanThreadViewableInfoEntity
    )

    chanThreadViewableInfoCache.store(threadDescriptor, chanThreadViewableInfo)
    return chanThreadViewableInfo
  }

  suspend fun persist(chanThreadViewableInfo: ChanThreadViewableInfo) {
    ensureInTransaction()

    val threadId = chanDescriptorCache.getThreadIdByThreadDescriptor(chanThreadViewableInfo.threadDescriptor)
      ?: return

    val databaseId = chanThreadViewableInfoDao.selectIdByOwnerThreadId(threadId)
    if (databaseId != null) {
      Logger.d(TAG, "Updating ChanThreadViewableInfo for ${chanThreadViewableInfo.threadDescriptor}")

      val chanThreadViewableInfoEntity = ChanThreadViewableInfoMapper.toEntity(
        databaseId,
        threadId,
        chanThreadViewableInfo
      )

      chanThreadViewableInfoDao.update(chanThreadViewableInfoEntity)
    } else {
      Logger.d(TAG, "Inserting ChanThreadViewableInfo for ${chanThreadViewableInfo.threadDescriptor}")

      val chanThreadViewableInfoEntity = ChanThreadViewableInfoMapper.toEntity(
        0L,
        threadId,
        chanThreadViewableInfo
      )

      chanThreadViewableInfoDao.insert(chanThreadViewableInfoEntity)
    }

    chanThreadViewableInfoCache.store(chanThreadViewableInfo.threadDescriptor, chanThreadViewableInfo)
  }

}