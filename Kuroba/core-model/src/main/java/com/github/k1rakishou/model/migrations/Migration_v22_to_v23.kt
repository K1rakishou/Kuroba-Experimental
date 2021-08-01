package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.k1rakishou.model.KurobaDatabase

class Migration_v22_to_v23 : Migration(22, 23) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("ALTER TABLE chan_board ADD COLUMN is_unlimited_catalog INTEGER NOT NULL DEFAULT ${KurobaDatabase.SQLITE_FALSE}")
    }
  }

}