package com.github.adamantcheese.model.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.adamantcheese.model.entity.ChanThreadEntity

class Migration_from_v1_to_v2 : Migration(1, 2) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.doWithoutForeignKeys {
            /**
             * Add index for chan_thread.owner_board_id
             * */
            val tempChanThreadTableName = ChanThreadEntity.TABLE_NAME.getTempTableName()

            database.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS `$tempChanThreadTableName` 
                        (`thread_id` INTEGER NOT NULL, 
                        `owner_board_id` INTEGER NOT NULL, 
                        PRIMARY KEY(`thread_id`), 
                        FOREIGN KEY(`owner_board_id`) REFERENCES `chan_board`(`board_id`) ON UPDATE CASCADE ON DELETE CASCADE )
                    """.trimIndent()
            )

            val chanThreadProperties = listOf(
                    ChanThreadEntity.THREAD_ID_COLUMN_NAME,
                    ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME
            ).getTablePropertiesAsRow()

            database.execSQL(
                    """
               INSERT INTO 
                    $tempChanThreadTableName ($chanThreadProperties)
               SELECT 
                    `${ChanThreadEntity.THREAD_ID_COLUMN_NAME}`, 
                    `${ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME}` 
               FROM ${ChanThreadEntity.TABLE_NAME};
            """
            )

            database.dropTable(ChanThreadEntity.TABLE_NAME)
            database.changeTableName(tempChanThreadTableName, ChanThreadEntity.TABLE_NAME)
            database.execSQL("CREATE INDEX IF NOT EXISTS `chan_thread_owner_board_id_idx` ON `chan_thread` (`owner_board_id`)")

            /**
             * Add new table chan_post
             * */
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `chan_post` 
                (`post_id` INTEGER NOT NULL, 
                `owner_thread_id` INTEGER NOT NULL, 
                `replies` INTEGER NOT NULL, 
                `thread_images_count` INTEGER NOT NULL, 
                `unique_ips` INTEGER NOT NULL,
                `last_modified` INTEGER NOT NULL, 
                `unix_timestamp_seconds` INTEGER NOT NULL,
                `id_color` INTEGER NOT NULL, 
                `filter_highlighted_color` INTEGER NOT NULL, 
                `post_comment` TEXT NOT NULL, 
                `subject` TEXT, 
                `name` TEXT, 
                `tripcode` TEXT, 
                `poster_id` TEXT,
                `moderator_capcode` TEXT, 
                `subject_span` TEXT, 
                `name_tripcode_id_capcode_span` TEXT, 
                `is_op` INTEGER NOT NULL,
                `sticky` INTEGER NOT NULL, 
                `closed` INTEGER NOT NULL, 
                `archived` INTEGER NOT NULL, 
                `is_light_color` INTEGER NOT NULL, 
                `is_saved_reply` INTEGER NOT NULL,
                `filter_stub` INTEGER NOT NULL, 
                `filter_remove` INTEGER NOT NULL, 
                `filter_watch` INTEGER NOT NULL, 
                `filter_replies` INTEGER NOT NULL,
                `filter_only_op` INTEGER NOT NULL, 
                `filter_saved` INTEGER NOT NULL, 
                PRIMARY KEY(`post_id`), 
                FOREIGN KEY(`owner_thread_id`) REFERENCES `chan_thread`(`thread_id`) ON UPDATE CASCADE ON DELETE CASCADE )
            """.trimIndent())

            database.execSQL("CREATE INDEX IF NOT EXISTS `chan_post_owner_thread_id_idx` ON `chan_post` (`owner_thread_id`)")
        }
    }
}