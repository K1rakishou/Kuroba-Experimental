package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v9_to_v10 : Migration(9, 10) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL(
        """
          CREATE TABLE IF NOT EXISTS `chan_catalog_snapshot` 
          (
            `owner_board_id` INTEGER NOT NULL,
            `thread_no` INTEGER NOT NULL, 
            `thread_order` INTEGER NOT NULL,
            PRIMARY KEY(`owner_board_id`, `thread_no`), 
            FOREIGN KEY(`owner_board_id`) REFERENCES `chan_board_id`(`board_id`) ON UPDATE CASCADE ON DELETE CASCADE 
          )
        """.trimIndent()
      )
    }
  }

}