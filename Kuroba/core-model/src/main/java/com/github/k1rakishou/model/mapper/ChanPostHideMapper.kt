package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostHide
import com.github.k1rakishou.model.entity.chan.post.ChanPostHideEntity

object ChanPostHideMapper {

  fun toEntity(chanPostHide: ChanPostHide): ChanPostHideEntity {
    return ChanPostHideEntity(
      siteName = chanPostHide.postDescriptor.boardDescriptor().siteName(),
      boardCode = chanPostHide.postDescriptor.boardDescriptor().boardCode,
      threadNo = chanPostHide.postDescriptor.threadDescriptor().threadNo,
      postNo = chanPostHide.postDescriptor.postNo,
      postSubNo = chanPostHide.postDescriptor.postSubNo,
      onlyHide = chanPostHide.onlyHide,
      applyToWholeThread = chanPostHide.applyToWholeThread,
      applyToReplies = chanPostHide.applyToReplies,
      manuallyRestored = chanPostHide.manuallyRestored
    )
  }

  fun fromEntity(chanPostHideEntity: ChanPostHideEntity): ChanPostHide {
    return ChanPostHide(
      postDescriptor = PostDescriptor.create(
        siteName = chanPostHideEntity.siteName,
        boardCode = chanPostHideEntity.boardCode,
        threadNo = chanPostHideEntity.threadNo,
        postNo = chanPostHideEntity.postNo,
        postSubNo = chanPostHideEntity.postSubNo
      ),
      onlyHide = chanPostHideEntity.onlyHide,
      applyToWholeThread = chanPostHideEntity.applyToWholeThread,
      applyToReplies = chanPostHideEntity.applyToReplies,
      manuallyRestored = chanPostHideEntity.manuallyRestored
    )
  }

}