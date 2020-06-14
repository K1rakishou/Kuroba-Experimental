package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.InlinedFileInfo
import com.github.adamantcheese.model.entity.InlinedFileInfoEntity
import org.joda.time.DateTime

object InlinedFileInfoMapper {

  fun toEntity(inlinedFileInfo: InlinedFileInfo, insertedAt: DateTime): InlinedFileInfoEntity {
    return InlinedFileInfoEntity(
      fileUrl = inlinedFileInfo.fileUrl,
      fileSize = inlinedFileInfo.fileSize,
      insertedAt = insertedAt
    )
  }

  fun fromEntity(inlinedFileInfoEntity: InlinedFileInfoEntity?): InlinedFileInfo? {
    if (inlinedFileInfoEntity == null) {
      return null
    }

    return InlinedFileInfo(
      fileUrl = inlinedFileInfoEntity.fileUrl,
      fileSize = inlinedFileInfoEntity.fileSize
    )
  }

}