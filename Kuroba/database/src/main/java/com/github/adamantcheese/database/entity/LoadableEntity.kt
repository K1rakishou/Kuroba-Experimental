package com.github.adamantcheese.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.github.adamantcheese.base.LoadableType

@Entity(
        tableName = LoadableEntity.TABLE_NAME,
        primaryKeys = [LoadableEntity.THREAD_UID_COLUMN_NAME],
        indices = [
            // So it's possible to find a thread among all cross-site threads by the site and board
            // it belongs to + it's opId
            Index(
                    name = LoadableEntity.SITE_BOARD_OPID_COMBO_INDEX_NAME,
                    // A thread is unique across all possible sites and boards combinations, i.e.
                    // two sites with different name (say 4chan.org and 2ch.hk) may have a thread
                    // with the same opId (let's say 1234567890) but since they have different site
                    // names and/or board codes - it's fine. The same goes for boards: a thread is
                    // unique across all boards of the same site.
                    value = [
                        LoadableEntity.SITE_NAME_COLUMN_NAME,
                        LoadableEntity.BOARD_CODE_COLUMN_NAME,
                        LoadableEntity.OP_ID_COLUMN_NAME
                    ],
                    unique = true
            ),
            // So it's possible to find a thread by it's precalculated unique id
            // (site name + board code + opId)
            Index(
                    name = LoadableEntity.THREAD_UID_INDEX_NAME,
                    value = [
                        LoadableEntity.THREAD_UID_COLUMN_NAME
                    ],
                    unique = true
            )
        ]
)
data class LoadableEntity(
        @ColumnInfo(name = THREAD_UID_COLUMN_NAME)
        val threadUid: String,
        @ColumnInfo(name = SITE_NAME_COLUMN_NAME)
        val siteName: String,
        @ColumnInfo(name = BOARD_CODE_COLUMN_NAME)
        val boardCode: String,
        @ColumnInfo(name = OP_ID_COLUMN_NAME)
        val opId: Long,
        @ColumnInfo(name = LOADABLE_TYPE_COLUMN_NAME)
        val loadableType: LoadableType
) {

    companion object {
        const val TABLE_NAME = "loadable"

        const val SITE_NAME_COLUMN_NAME = "site_name"
        const val BOARD_CODE_COLUMN_NAME = "board_code"
        const val OP_ID_COLUMN_NAME = "op_id"
        const val THREAD_UID_COLUMN_NAME = "thread_uid"
        const val LOADABLE_TYPE_COLUMN_NAME = "loadable_type"

        const val SITE_BOARD_OPID_COMBO_INDEX_NAME = "${TABLE_NAME}_site_board_opid_idx"
        const val THREAD_UID_INDEX_NAME = "${TABLE_NAME}_thread_uid_idx"
    }
}