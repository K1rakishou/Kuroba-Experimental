package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Added is_expanded row to thread_bookmark_group table
 * */
class Migration_v5_to_v6 : Migration(5, 6) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL(
        """
          CREATE TABLE IF NOT EXISTS `thread_bookmark_group_temp`
          (
            `group_id` TEXT NOT NULL,
            `group_name` TEXT NOT NULL,
            `is_expanded` INTEGER NOT NULL DEFAULT 0,
            `group_order` INTEGER NOT NULL,
            PRIMARY KEY(`group_id`)
          )
        """.trimIndent()
      )

      database.execSQL(
        """
          INSERT INTO `thread_bookmark_group_temp` 
          (
            group_id, 
            group_name, 
            group_order
          )
          SELECT 
            thread_bookmark_group.group_id AS group_id,
            thread_bookmark_group.group_name AS group_name,
            thread_bookmark_group.group_order AS group_order
          FROM `thread_bookmark_group`
        """.trimIndent()
      )

      database.dropTable("thread_bookmark_group")
      database.changeTableName("thread_bookmark_group_temp", "thread_bookmark_group")

      database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_thread_bookmark_group_group_id` ON `thread_bookmark_group` (`group_id`)")
      database.execSQL("CREATE INDEX IF NOT EXISTS `index_thread_bookmark_group_group_order` ON `thread_bookmark_group` (`group_order`)")
    }
  }
}