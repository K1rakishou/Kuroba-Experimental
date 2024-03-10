package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v42_to_v43 : Migration(42, 43) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("""
        CREATE TABLE IF NOT EXISTS chan_board_new 
        (
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
          `work_safe` INTEGER NOT NULL, 
          `spoilers` INTEGER NOT NULL, 
          `user_ids` INTEGER NOT NULL, 
          `country_flags` INTEGER NOT NULL, 
          `is_unlimited_catalog` INTEGER NOT NULL, 
          PRIMARY KEY(`owner_chan_board_id`),
          FOREIGN KEY(`owner_chan_board_id`) REFERENCES `chan_board_id`(`board_id`) ON UPDATE CASCADE ON DELETE CASCADE
        )
      """.trimIndent()
      )

      database.execSQL("""
      INSERT INTO chan_board_new 
      (
        owner_chan_board_id, 
        board_active, 
        board_order, 
        name, 
        per_page, 
        pages, 
        max_file_size, 
        max_webm_size, 
        max_file_width, 
        max_file_height, 
        max_comment_chars, 
        bump_limit, 
        image_limit, 
        cooldown_threads, 
        cooldown_replies, 
        cooldown_images, 
        custom_spoilers, 
        description, 
        work_safe, 
        spoilers, 
        user_ids, 
        country_flags, 
        is_unlimited_catalog
      )
      SELECT 
        owner_chan_board_id, 
        board_active, 
        board_order, 
        name, 
        per_page, 
        pages, 
        max_file_size, 
        max_webm_size, 
        -1,
        -1,
        max_comment_chars, 
        bump_limit, 
        image_limit, 
        cooldown_threads, 
        cooldown_replies, 
        cooldown_images, 
        custom_spoilers, 
        description, 
        work_safe, 
        spoilers, 
        user_ids, 
        country_flags, 
        is_unlimited_catalog
      FROM chan_board
    """.trimIndent()
      )

      database.execSQL("DROP TABLE chan_board")

      database.execSQL("ALTER TABLE chan_board_new RENAME TO chan_board")
    }
  }

}