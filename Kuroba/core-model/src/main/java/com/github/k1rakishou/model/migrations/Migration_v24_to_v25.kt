package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v24_to_v25 : Migration(24, 25) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("""
        CREATE TABLE IF NOT EXISTS `composite_catalog` 
        (
          `id` INTEGER NOT NULL,
          `composite_boards` TEXT NOT NULL, 
          `catalog_order` INTEGER NOT NULL, 
          PRIMARY KEY(`id`, `composite_boards`)
        )
      """.trimIndent())
    }
  }

}