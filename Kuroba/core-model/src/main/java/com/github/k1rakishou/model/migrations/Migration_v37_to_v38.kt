package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v37_to_v38 : Migration(37, 38) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("ALTER TABLE chan_post ADD COLUMN poster_id_color INTEGER NOT NULL DEFAULT 0");
    }
  }

}