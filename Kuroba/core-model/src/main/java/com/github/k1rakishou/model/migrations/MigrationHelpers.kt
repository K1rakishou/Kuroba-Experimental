package com.github.k1rakishou.model.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase

internal fun SupportSQLiteDatabase.changeTableName(fromName: String, toName: String) {
  execSQL("ALTER TABLE `${fromName}` RENAME TO `${toName}`;")
}

internal fun SupportSQLiteDatabase.dropTable(tableName: String) {
  execSQL("DROP TABLE IF EXISTS `$tableName`")
}

internal fun SupportSQLiteDatabase.dropIndex(indexName: String) {
  execSQL("DROP INDEX IF EXISTS `$indexName`")
}

internal fun SupportSQLiteDatabase.doWithoutForeignKeys(func: () -> Unit) {
  try {
    execSQL("PRAGMA foreign_keys = OFF")
    func()
  } finally {
    execSQL("PRAGMA foreign_keys = ON")
  }
}

internal fun getColumnIndexOrThrow(c: Cursor, name: String): Int {
  val index = c.getColumnIndex(name)
  if (index >= 0) {
    return index
  }

  return c.getColumnIndexOrThrow("`$name`")
}