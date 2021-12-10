package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v39_to_v40 : Migration(39, 40) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.dropTable("inlined_file_info_entity")
    }
  }

}