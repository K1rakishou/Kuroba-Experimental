package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v19_to_v20 : Migration(19, 20) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("ALTER TABLE chan_saved_reply ADD COLUMN post_comment TEXT DEFAULT NULL")
      database.execSQL("ALTER TABLE chan_saved_reply ADD COLUMN thread_subject TEXT DEFAULT NULL")
    }
  }

}