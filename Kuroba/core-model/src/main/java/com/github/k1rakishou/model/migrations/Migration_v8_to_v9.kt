package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v8_to_v9 : Migration(8, 9) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      updateChanThreadTableColumnNames(database)
      dropNotUsedColumnsFromChanPostIdTable(database)
      dropNotUsedColumnsFromChanPostImageTable(database)
    }
  }

  private fun dropNotUsedColumnsFromChanPostImageTable(database: SupportSQLiteDatabase) {
    database.execSQL(
      """
        CREATE TABLE IF NOT EXISTS `chan_post_image_temp` 
        (
          `post_image_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
          `owner_post_id` INTEGER NOT NULL, 
          `server_filename` TEXT NOT NULL, 
          `thumbnail_url` TEXT, 
          `image_url` TEXT, 
          `spoiler_thumbnail_url` TEXT, 
          `filename` TEXT, 
          `extension` TEXT, 
          `image_width` INTEGER NOT NULL, 
          `image_height` INTEGER NOT NULL, 
          `spoiler` INTEGER NOT NULL, 
          `is_inlined` INTEGER NOT NULL, 
          `file_size` INTEGER NOT NULL, 
          `file_hash` TEXT, 
          `type` INTEGER, 
          FOREIGN KEY(`owner_post_id`) REFERENCES `chan_post`(`chan_post_id`) ON UPDATE CASCADE ON DELETE CASCADE 
        )
      """.trimIndent()
    )

    database.execSQL(
      """
            INSERT INTO `chan_post_image_temp` 
            (
              post_image_id,
              owner_post_id,
              server_filename,
              thumbnail_url,
              image_url,
              spoiler_thumbnail_url,
              filename,
              extension,
              image_width,
              image_height,
              spoiler,
              is_inlined,
              file_size,
              file_hash,
              type
            )
            SELECT 
              chan_post_image.post_image_id AS post_image_id,
              chan_post_image.owner_post_id AS owner_post_id,
              chan_post_image.server_filename AS server_filename,
              chan_post_image.thumbnail_url AS thumbnail_url,
              chan_post_image.image_url AS image_url,
              chan_post_image.spoiler_thumbnail_url AS spoiler_thumbnail_url,
              chan_post_image.filename AS filename,
              chan_post_image.extension AS extension,
              chan_post_image.image_width AS image_width,
              chan_post_image.image_height AS image_height,
              chan_post_image.spoiler AS spoiler,
              chan_post_image.is_inlined AS is_inlined,
              chan_post_image.file_size AS file_size,
              chan_post_image.file_hash AS file_hash,
              chan_post_image.type AS type
            FROM `chan_post_image`
          """.trimIndent()
    )

    database.dropTable("chan_post_image")
    database.changeTableName("chan_post_image_temp", "chan_post_image")

    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chan_post_image_owner_post_id_server_filename` ON `chan_post_image` (`owner_post_id`, `server_filename`)")
    database.execSQL("CREATE INDEX IF NOT EXISTS `index_chan_post_image_owner_post_id` ON `chan_post_image` (`owner_post_id`)")
  }

  private fun dropNotUsedColumnsFromChanPostIdTable(database: SupportSQLiteDatabase) {
    database.execSQL(
      """
        CREATE TABLE IF NOT EXISTS `chan_post_id_temp` 
        (
          `post_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
          `owner_thread_id` INTEGER NOT NULL, 
          `post_no` INTEGER NOT NULL, 
          `post_sub_no` INTEGER NOT NULL, 
          FOREIGN KEY(`owner_thread_id`) REFERENCES `chan_thread`(`thread_id`) ON UPDATE CASCADE ON DELETE CASCADE )
      """.trimIndent()
    )

    database.execSQL(
      """
            INSERT INTO `chan_post_id_temp` 
            (
              post_id,
              owner_thread_id,
              post_no,
              post_sub_no
            )
            SELECT 
              chan_post_id.post_id AS post_id,
              chan_post_id.owner_thread_id AS owner_thread_id,
              chan_post_id.post_no AS post_no,
              chan_post_id.post_sub_no AS post_sub_no 
            FROM `chan_post_id`
          """.trimIndent()
    )

    database.dropTable("chan_post_id")
    database.changeTableName("chan_post_id_temp", "chan_post_id")

    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `chan_post_id_post_id_full_idx` ON `chan_post_id` (`owner_thread_id`, `post_no`, `post_sub_no`)")
    database.execSQL("CREATE INDEX IF NOT EXISTS `chan_post_id_post_no_idx` ON `chan_post_id` (`post_no`)")
    database.execSQL("CREATE INDEX IF NOT EXISTS `chan_post_id_post_sub_no_idx` ON `chan_post_id` (`post_sub_no`)")
    database.execSQL("CREATE INDEX IF NOT EXISTS `chan_post_id_thread_id_idx` ON `chan_post_id` (`owner_thread_id`)")
  }

  private fun updateChanThreadTableColumnNames(database: SupportSQLiteDatabase) {
    database.execSQL(
      """
            CREATE TABLE IF NOT EXISTS `chan_thread_temp` 
            (
              `thread_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
              `thread_no` INTEGER NOT NULL, 
              `owner_board_id` INTEGER NOT NULL, 
              `last_modified` INTEGER NOT NULL, 
              `catalog_replies_count` INTEGER NOT NULL, 
              `catalog_images_count` INTEGER NOT NULL, 
              `unique_ips` INTEGER NOT NULL, 
              `sticky` INTEGER NOT NULL, 
              `closed` INTEGER NOT NULL, 
              `archived` INTEGER NOT NULL, 
              FOREIGN KEY(`owner_board_id`) REFERENCES `chan_board_id`(`board_id`) ON UPDATE CASCADE ON DELETE CASCADE 
            )
          """.trimIndent()
    )

    database.execSQL(
      """
            INSERT INTO `chan_thread_temp` 
            (
              thread_id,
              thread_no,
              owner_board_id,
              last_modified,
              catalog_replies_count,
              catalog_images_count,
              unique_ips,
              sticky,
              closed,
              archived
            )
            SELECT 
              chan_thread.thread_id AS thread_id,
              chan_thread.thread_no AS thread_no,
              chan_thread.owner_board_id AS owner_board_id,
              chan_thread.last_modified AS last_modified,
              chan_thread.replies AS catalog_replies_count,
              chan_thread.thread_images_count AS catalog_images_count,
              chan_thread.unique_ips AS unique_ips,
              chan_thread.sticky AS sticky,
              chan_thread.closed AS closed,
              chan_thread.archived AS archived
            FROM `chan_thread`
          """.trimIndent()
    )

    database.dropTable("chan_thread")
    database.changeTableName("chan_thread_temp", "chan_thread")

    database.execSQL("CREATE INDEX IF NOT EXISTS `index_chan_thread_thread_no` ON `chan_thread` (`thread_no`)")
    database.execSQL("CREATE INDEX IF NOT EXISTS `index_chan_thread_owner_board_id` ON `chan_thread` (`owner_board_id`)")
    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chan_thread_thread_no_owner_board_id` ON `chan_thread` (`thread_no`, `owner_board_id`)")
  }

}