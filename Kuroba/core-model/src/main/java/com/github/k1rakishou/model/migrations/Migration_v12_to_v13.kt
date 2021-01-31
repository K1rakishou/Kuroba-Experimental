package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.k1rakishou.model.data.bookmark.ThreadBookmark

class Migration_v12_to_v13 : Migration(12, 13) {

  override fun migrate(database: SupportSQLiteDatabase) {
    // We fucked up with ThreadBookmark flags. We were using them incorrectly when accessing bits
    // of BitSet. So now we need to reset state of every bookmark to default.
    val defaultState = (1 shl ThreadBookmark.BOOKMARK_STATE_WATCHING) or (1 shl ThreadBookmark.BOOKMARK_STATE_FIRST_FETCH)

    database.execSQL("UPDATE `thread_bookmark` SET `state` = $defaultState")
  }
}