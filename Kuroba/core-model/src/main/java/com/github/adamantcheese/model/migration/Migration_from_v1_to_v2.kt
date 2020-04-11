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
            kotlin.run {
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
            }

            /**
             * Add new table chan_post
             * */
            kotlin.run {
                database.execSQL("""
                CREATE TABLE IF NOT EXISTS `chan_post` 
                (`post_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `post_no` INTEGER NOT NULL,
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
                FOREIGN KEY(`owner_thread_id`) REFERENCES `chan_thread`(`thread_id`) ON UPDATE CASCADE ON DELETE CASCADE )
            """.trimIndent())

                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `chan_post_post_no_owner_thread_id_idx` ON `chan_post` (`post_no`, `owner_thread_id`)")
            }

            /**
             * Add new table chan_post_image
             * */
            kotlin.run {
                database.execSQL("""
                CREATE TABLE IF NOT EXISTS `chan_post_image` 
                (`post_image_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `owner_post_id` INTEGER NOT NULL, 
                `server_filename` TEXT NOT NULL, 
                `thumbnail_url` TEXT, 
                `spoiler_thumbnail_url` TEXT, 
                `image_url` TEXT, 
                `filename` TEXT, 
                `extension` TEXT, 
                `image_width` INTEGER NOT NULL, 
                `image_height` INTEGER NOT NULL, 
                `spoiler` INTEGER NOT NULL, 
                `is_inlined` INTEGER NOT NULL, 
                `file_hash` TEXT, 
                `type` INTEGER, 
                FOREIGN KEY(`owner_post_id`) REFERENCES `chan_post`(`post_id`) ON UPDATE CASCADE ON DELETE CASCADE )
            """.trimIndent())

                database.execSQL("CREATE INDEX IF NOT EXISTS `chan_post_image_owner_post_id_idx` ON `chan_post_image` (`owner_post_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `chan_post_image_file_hash_idx` ON `chan_post_image` (`file_hash`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `chan_post_image_image_url_idx` ON `chan_post_image` (`image_url`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `chan_post_image_thumbnail_url_idx` ON `chan_post_image` (`thumbnail_url`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `chan_post_image_server_filename_idx` ON `chan_post_image` (`server_filename`)")
            }

            /**
             * Add new table chan_post_http_icon
             * */
           kotlin.run {
               database.execSQL("""
                CREATE TABLE IF NOT EXISTS `chan_post_http_icon` 
                (`icon_url` TEXT NOT NULL, 
                `owner_post_id` INTEGER NOT NULL, 
                `icon_name` TEXT NOT NULL, 
                PRIMARY KEY(`icon_url`), 
                FOREIGN KEY(`owner_post_id`) REFERENCES `chan_post`(`post_id`) ON UPDATE CASCADE ON DELETE CASCADE )
            """.trimIndent())

               database.execSQL("CREATE INDEX IF NOT EXISTS `chan_post_http_icon_owner_post_id_idx` ON `chan_post_http_icon` (`owner_post_id`)")
           }
        }
    }
}