package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.Loadable2
import com.github.adamantcheese.model.entity.LoadableEntity

object Loadable2Mapper {

    fun toEntity(loadable2: Loadable2): LoadableEntity {
        return LoadableEntity(
                loadable2.threadUid,
                loadable2.siteName,
                loadable2.boardCode,
                loadable2.opId,
                loadable2.loadableType
        )
    }

    fun fromEntity(loadableEntity: LoadableEntity?): Loadable2? {
        if (loadableEntity == null) {
            return null
        }

        return Loadable2(
                loadableEntity.threadUid,
                loadableEntity.siteName,
                loadableEntity.boardCode,
                loadableEntity.opId,
                loadableEntity.loadableType
        )
    }


}