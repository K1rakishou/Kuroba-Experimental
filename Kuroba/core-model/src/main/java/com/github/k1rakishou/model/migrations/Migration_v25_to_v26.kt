package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v25_to_v26 : Migration(25, 26) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.dropTable("composite_catalog")

      database.execSQL("""
        CREATE TABLE IF NOT EXISTS `composite_catalog` 
        (
          `id` INTEGER NOT NULL, 
          `name` TEXT NOT NULL, 
          `composite_boards` TEXT NOT NULL, 
          `catalog_order` INTEGER NOT NULL, 
          PRIMARY KEY(`id`, `composite_boards`)
        )
      """.trimIndent())
    }
  }

}