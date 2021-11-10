package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v33_to_v34 : Migration(33, 34) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("ALTER TABLE thread_bookmark_group ADD COLUMN group_matcher_pattern TEXT DEFAULT NULL")
    }
  }

}