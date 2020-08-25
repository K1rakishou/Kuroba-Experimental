package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.thread.ChanThreadViewableInfo
import com.github.adamantcheese.model.entity.chan.thread.ChanThreadViewableInfoEntity

object ChanThreadViewableInfoMapper {

  fun toEntity(databaseId: Long, threadId: Long, chanThreadViewableInfo: ChanThreadViewableInfo): ChanThreadViewableInfoEntity {
    return ChanThreadViewableInfoEntity(
      chanThreadViewableInfoId = databaseId,
      ownerThreadId = threadId,
      listViewIndex = chanThreadViewableInfo.listViewIndex,
      listViewTop = chanThreadViewableInfo.listViewTop,
      lastViewedPostNo = chanThreadViewableInfo.lastViewedPostNo,
      lastLoadedPostNo = chanThreadViewableInfo.lastLoadedPostNo,
      markedPostNo = chanThreadViewableInfo.markedPostNo,
    )
  }

  fun fromEntity(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    chanThreadViewableInfoEntity: ChanThreadViewableInfoEntity
  ): ChanThreadViewableInfo {
    return ChanThreadViewableInfo(
      threadDescriptor = threadDescriptor,
      listViewIndex = chanThreadViewableInfoEntity.listViewIndex,
      listViewTop = chanThreadViewableInfoEntity.listViewTop,
      lastViewedPostNo = chanThreadViewableInfoEntity.lastViewedPostNo,
      lastLoadedPostNo = chanThreadViewableInfoEntity.lastLoadedPostNo,
      markedPostNo = chanThreadViewableInfoEntity.markedPostNo,
    )
  }

}