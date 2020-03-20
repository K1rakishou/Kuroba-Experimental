package com.github.adamantcheese.database.mapper

import com.github.adamantcheese.database.data.InlinedFileInfo
import com.github.adamantcheese.database.entity.InlinedFileInfoEntity
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