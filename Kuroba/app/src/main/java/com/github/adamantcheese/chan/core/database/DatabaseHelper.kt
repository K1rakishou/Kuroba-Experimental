/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.github.adamantcheese.chan.core.model.orm.Filter
import com.github.adamantcheese.chan.core.model.orm.PostHide
import com.github.adamantcheese.chan.core.model.orm.SavedReply
import com.github.adamantcheese.chan.utils.Logger
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper
import com.j256.ormlite.dao.Dao
import com.j256.ormlite.support.ConnectionSource
import com.j256.ormlite.table.TableUtils
import java.sql.SQLException
import javax.inject.Inject

@Suppress("UNCHECKED_CAST")
class DatabaseHelper @Inject constructor(
  private val context: Context
) : OrmLiteSqliteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

  val savedDao by lazy { getDao(SavedReply::class.java)!! as Dao<SavedReply, Int> }
  val postHideDao by lazy { getDao(PostHide::class.java)!! as Dao<PostHide, Int> }
  val filterDao by lazy { getDao(Filter::class.java)!! as Dao<Filter, Int> }

  override fun onCreate(database: SQLiteDatabase, connectionSource: ConnectionSource) {
    try {
      createTables(connectionSource)
    } catch (e: SQLException) {
      Logger.e(TAG, "Error creating db", e)
      throw RuntimeException(e)
    }
  }

  @Throws(SQLException::class)
  fun createTables(connectionSource: ConnectionSource?) {
    TableUtils.createTable(connectionSource, SavedReply::class.java)
    TableUtils.createTable(connectionSource, PostHide::class.java)
    TableUtils.createTable(connectionSource, Filter::class.java)
  }

  @Throws(SQLException::class)
  fun dropTables(connectionSource: ConnectionSource?) {
    TableUtils.dropTable<SavedReply, Any>(connectionSource, SavedReply::class.java, true)
    TableUtils.dropTable<PostHide, Any>(connectionSource, PostHide::class.java, true)
    TableUtils.dropTable<Filter, Any>(connectionSource, Filter::class.java, true)
  }

  /**
   * When modifying the database columns do no forget to change the
   * [com.github.adamantcheese.chan.core.model.export.ExportedAppSettings] as well and add your
   * handler in [com.github.adamantcheese.chan.core.repository.ImportExportRepository]
   * onUpgrade method
   */
  override fun onUpgrade(
    database: SQLiteDatabase,
    connectionSource: ConnectionSource,
    oldVersion: Int,
    newVersion: Int
  ) {
    // TODO(KurobaEx): remove
  }

  override fun onConfigure(db: SQLiteDatabase) {
    db.enableWriteAheadLogging()
  }

  fun reset() {
    Logger.i(TAG, "Resetting database!")
    if (context.deleteDatabase(DATABASE_NAME)) {
      Logger.i(TAG, "Deleted database")
    }
  }

  companion object {
    private const val TAG = "DatabaseHelper"
    private const val DATABASE_NAME = "ChanDB"
    private const val DATABASE_VERSION = 45
  }

}