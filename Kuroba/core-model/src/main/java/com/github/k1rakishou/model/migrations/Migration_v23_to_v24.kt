package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.k1rakishou.model.KurobaDatabase

class Migration_v23_to_v24 : Migration(23, 24) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("ALTER TABLE nav_history_element_info ADD COLUMN pinned INTEGER NOT NULL DEFAULT ${KurobaDatabase.SQLITE_FALSE}")
    }
  }

}