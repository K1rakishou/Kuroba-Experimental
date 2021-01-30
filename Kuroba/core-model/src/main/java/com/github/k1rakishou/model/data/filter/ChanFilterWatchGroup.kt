package com.github.k1rakishou.model.data.filter

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

/**
 * One ChanFilterWatchGroup may have only one bookmark.
 * One ChanFilter may have multiple ChanFilterWatchGroups.
 * */
data class ChanFilterWatchGroup(
  val ownerChanFilterDatabaseId: Long,
  val threadDescriptor: ChanDescriptor.ThreadDescriptor
)