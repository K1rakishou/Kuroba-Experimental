package com.github.adamantcheese.model.entity

import androidx.room.*
import com.github.adamantcheese.model.KurobaDatabase

@Entity(
        tableName = ChanThreadEntity.TABLE_NAME,
        foreignKeys = [
            ForeignKey(
                    entity = ChanBoardEntity::class,
                    parentColumns = [ChanBoardEntity.BOARD_ID_COLUMN_NAME],
                    childColumns = [ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME],
                    onUpdate = ForeignKey.CASCADE,
                    onDelete = ForeignKey.CASCADE
            )
        ],
        indices = [
            Index(
                    name = ChanThreadEntity.THREAD_NO_INDEX_NAME,
                    value = [ChanThreadEntity.THREAD_NO_COLUMN_NAME]
            ),
            Index(
                    name = ChanThreadEntity.OWNER_BOARD_ID_INDEX_NAME,
                    value = [ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME]
            ),
            Index(
                    name = ChanThreadEntity.THREAD_NO_OWNER_BOARD_ID_INDEX_NAME,
                    value = [
                        ChanThreadEntity.THREAD_NO_COLUMN_NAME,
                        ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME
                    ],
                    unique = true
            )
        ]
)
data class ChanThreadEntity(
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = THREAD_ID_COLUMN_NAME)
        var threadId: Long,
        @ColumnInfo(name = THREAD_NO_COLUMN_NAME)
        val threadNo: Long,
        @ColumnInfo(name = OWNER_BOARD_ID_COLUMN_NAME)
        val ownerBoardId: Long,
        @ColumnInfo(name = LAST_MODIFIED_COLUMN_NAME)
        val lastModified: Long = -1L,
        @ColumnInfo(name = REPLIES_COLUMN_NAME)
        val replies: Int = -1,
        @ColumnInfo(name = THREAD_IMAGES_COUNT_COLUMN_NAME)
        val threadImagesCount: Int = -1,
        @ColumnInfo(name = UNIQUE_IPS_COLUMN_NAME)
        val uniqueIps: Int = -1,
        @ColumnInfo(name = STICKY_COLUMN_NAME)
        val sticky: Boolean = false,
        @ColumnInfo(name = CLOSED_COLUMN_NAME)
        val closed: Boolean = false,
        @ColumnInfo(name = ARCHIVED_COLUMN_NAME)
        val archived: Boolean = false
) {

    companion object {
        const val TABLE_NAME = "chan_thread"

        const val THREAD_ID_COLUMN_NAME = "thread_id"
        const val THREAD_NO_COLUMN_NAME = "thread_no"
        const val OWNER_BOARD_ID_COLUMN_NAME = "owner_board_id"
        const val LAST_MODIFIED_COLUMN_NAME = "last_modified"
        const val REPLIES_COLUMN_NAME = "replies"
        const val THREAD_IMAGES_COUNT_COLUMN_NAME = "thread_images_count"
        const val UNIQUE_IPS_COLUMN_NAME = "unique_ips"
        const val STICKY_COLUMN_NAME = "sticky"
        const val CLOSED_COLUMN_NAME = "closed"
        const val ARCHIVED_COLUMN_NAME = "archived"

        const val THREAD_NO_OWNER_BOARD_ID_INDEX_NAME = "${TABLE_NAME}_thread_no_owner_board_id_idx"
        const val THREAD_NO_INDEX_NAME = "${TABLE_NAME}_thread_no_idx"
        const val OWNER_BOARD_ID_INDEX_NAME = "${TABLE_NAME}_owner_board_id_idx"
    }
}

/**
 * Represents a thread with more than one post that is not an original post and which lastModified
 * is greater than zero.
 * */
@DatabaseView(
        viewName = ChanThreadsWithPosts.VIEW_NAME,
        value = """
            SELECT
                threads.${ChanThreadsWithPosts.THREAD_ID_COLUMN_NAME},
                threads.${ChanThreadsWithPosts.THREAD_NO_COLUMN_NAME},
                threads.${ChanThreadsWithPosts.LAST_MODIFIED_COLUMN_NAME},
                COUNT(posts.${ChanPostEntity.POST_ID_COLUMN_NAME}) as posts_count
            FROM
                ${ChanPostEntity.TABLE_NAME} posts
            LEFT JOIN ${ChanThreadEntity.TABLE_NAME} threads 
                ON posts.${ChanPostEntity.OWNER_THREAD_ID_COLUMN_NAME} = threads.${ChanThreadsWithPosts.THREAD_ID_COLUMN_NAME}
            WHERE 
                posts.${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_FALSE}
            AND 
                threads.${ChanThreadsWithPosts.LAST_MODIFIED_COLUMN_NAME} > 0
            GROUP BY threads.${ChanThreadsWithPosts.THREAD_ID_COLUMN_NAME}
            HAVING posts_count >= 0
            ORDER BY threads.${ChanThreadsWithPosts.LAST_MODIFIED_COLUMN_NAME} ASC
        """
)
data class ChanThreadsWithPosts(
        @ColumnInfo(name = THREAD_ID_COLUMN_NAME)
        val threadId: Long,
        @ColumnInfo(name = THREAD_NO_COLUMN_NAME)
        val threadNo: Long,
        @ColumnInfo(name = LAST_MODIFIED_COLUMN_NAME)
        val lastModified: Long,
        @ColumnInfo(name = POSTS_COUNT_COLUMN_NAME)
        val postsCount: Int
) {

    companion object {
        const val VIEW_NAME = "chan_threads_with_posts"

        const val THREAD_ID_COLUMN_NAME = ChanThreadEntity.THREAD_ID_COLUMN_NAME
        const val THREAD_NO_COLUMN_NAME = ChanThreadEntity.THREAD_NO_COLUMN_NAME
        const val LAST_MODIFIED_COLUMN_NAME = ChanThreadEntity.LAST_MODIFIED_COLUMN_NAME
        const val POSTS_COUNT_COLUMN_NAME = "posts_count"
    }
}