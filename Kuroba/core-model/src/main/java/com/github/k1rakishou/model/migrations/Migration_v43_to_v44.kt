package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v43_to_v44 : Migration(43, 44) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("""
            CREATE TABLE IF NOT EXISTS `chan_board_new` (
                `owner_chan_board_id` INTEGER NOT NULL,
                `board_active` INTEGER NOT NULL,
                `board_order` INTEGER NOT NULL,
                `name` TEXT,
                `per_page` INTEGER NOT NULL,
                `pages` INTEGER NOT NULL,
                `max_file_size` INTEGER NOT NULL,
                `max_webm_size` INTEGER NOT NULL,
                `max_file_width` INTEGER NOT NULL,
                `max_file_height` INTEGER NOT NULL,
                `max_comment_chars` INTEGER NOT NULL,
                `bump_limit` INTEGER NOT NULL,
                `image_limit` INTEGER NOT NULL,
                `cooldown_threads` INTEGER NOT NULL,
                `cooldown_replies` INTEGER NOT NULL,
                `cooldown_images` INTEGER NOT NULL,
                `custom_spoilers` INTEGER NOT NULL,
                `description` TEXT NOT NULL,
                `work_safe` INTEGER,
                `spoilers` INTEGER,
                `user_ids` INTEGER,
                `country_flags` INTEGER,
                `is_unlimited_catalog` INTEGER NOT NULL,
                PRIMARY KEY(`owner_chan_board_id`),
                FOREIGN KEY(`owner_chan_board_id`) REFERENCES `chan_board_id`(`board_id`) ON UPDATE CASCADE ON DELETE CASCADE
            )
        """.trimIndent())

    db.execSQL("""
            INSERT INTO `chan_board_new` (
                `owner_chan_board_id`, `board_active`, `board_order`, `name`,
                `per_page`, `pages`, `max_file_size`, `max_webm_size`,
                `max_file_width`, `max_file_height`, `max_comment_chars`,
                `bump_limit`, `image_limit`, `cooldown_threads`, `cooldown_replies`,
                `cooldown_images`, `custom_spoilers`, `description`,
                `work_safe`, `spoilers`, `user_ids`, `country_flags`, `is_unlimited_catalog`
            )
            SELECT
                `owner_chan_board_id`, `board_active`, `board_order`, `name`,
                `per_page`, `pages`, `max_file_size`, `max_webm_size`,
                `max_file_width`, `max_file_height`, `max_comment_chars`,
                `bump_limit`, `image_limit`, `cooldown_threads`, `cooldown_replies`,
                `cooldown_images`, `custom_spoilers`, `description`,
                NULL, `spoilers`, `user_ids`, `country_flags`, `is_unlimited_catalog`
            FROM `chan_board`
        """.trimIndent())

    db.execSQL("DROP TABLE `chan_board`")

    db.execSQL("ALTER TABLE `chan_board_new` RENAME TO `chan_board`")
  }
}