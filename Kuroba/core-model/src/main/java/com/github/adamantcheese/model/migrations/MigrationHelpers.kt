package com.github.adamantcheese.model.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

internal fun SupportSQLiteDatabase.changeTableName(fromName: String, toName: String) {
  execSQL("ALTER TABLE $fromName RENAME TO $toName;")
}

internal fun SupportSQLiteDatabase.dropTable(tableName: String) {
  execSQL("DROP TABLE IF EXISTS `$tableName`")
}

internal fun String.getTempTableName(): String {
  return this + "_TEMP"
}

internal fun List<String>.getTablePropertiesAsRow(): String {
  return joinToString(separator = "`, `", prefix = "`", postfix = "`")
}

internal fun SupportSQLiteDatabase.doWithoutForeignKeys(func: () -> Unit) {
  try {
    execSQL("PRAGMA foreign_keys = OFF")
    func()
  } finally {
    execSQL("PRAGMA foreign_keys = ON")
  }
}