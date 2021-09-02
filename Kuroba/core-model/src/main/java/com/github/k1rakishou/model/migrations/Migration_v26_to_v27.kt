package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v26_to_v27 : Migration(26, 27) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.dropTable("composite_catalog")

      database.execSQL("""
        CREATE TABLE IF NOT EXISTS `composite_catalog` 
        (
          `composite_boards` TEXT NOT NULL, 
          `name` TEXT NOT NULL, 
          `catalog_order` INTEGER NOT NULL, 
          PRIMARY KEY(`composite_boards`)
        )
      """.trimIndent())
    }
  }

}