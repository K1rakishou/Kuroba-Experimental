package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.InlinedFileInfo
import com.github.adamantcheese.model.entity.InlinedFileInfoEntity
import org.joda.time.DateTime

object InlinedFileInfoMapper {

    fun toEntity(inlinedFileInfo: InlinedFileInfo, insertedAt: DateTime): InlinedFileInfoEntity {
        return InlinedFileInfoEntity(
                inlinedFileInfo.fileUrl,
                inlinedFileInfo.fileSize,
                insertedAt
        )
    }

    fun fromEntity(inlinedFileInfoEntity: InlinedFileInfoEntity?): InlinedFileInfo? {
        if (inlinedFileInfoEntity == null) {
            return null
        }

        return InlinedFileInfo(
                inlinedFileInfoEntity.fileUrl,
                inlinedFileInfoEntity.fileSize
        )
    }

}