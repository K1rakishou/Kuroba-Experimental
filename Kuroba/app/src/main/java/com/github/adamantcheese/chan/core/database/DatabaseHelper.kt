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
import android.text.TextUtils
import com.github.adamantcheese.chan.core.model.orm.*
import com.github.adamantcheese.chan.core.settings.*
import com.github.adamantcheese.chan.core.settings.state.PersistableChanState
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper
import com.j256.ormlite.dao.Dao
import com.j256.ormlite.stmt.DeleteBuilder
import com.j256.ormlite.support.ConnectionSource
import com.j256.ormlite.table.TableUtils
import java.lang.reflect.Constructor
import java.sql.SQLException
import java.util.*
import javax.inject.Inject

@Suppress("UNCHECKED_CAST")
class DatabaseHelper @Inject constructor(
  private val context: Context
) : OrmLiteSqliteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

  val loadableDao by lazy { getDao(Loadable::class.java)!! as Dao<Loadable, Int> }
  val savedDao by lazy { getDao(SavedReply::class.java)!! as Dao<SavedReply, Int> }
  val boardsDao by lazy { getDao(Board::class.java)!! as Dao<Board, Int> }
  val postHideDao by lazy { getDao(PostHide::class.java)!! as Dao<PostHide, Int> }
  val historyDao by lazy { getDao(History::class.java)!! as Dao<History, Int> }
  val filterDao by lazy { getDao(Filter::class.java)!! as Dao<Filter, Int> }
  val siteDao by lazy { getDao(SiteModel::class.java)!! as Dao<SiteModel, Int> }

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
    TableUtils.createTable(connectionSource, Loadable::class.java)
    TableUtils.createTable(connectionSource, SavedReply::class.java)
    TableUtils.createTable(connectionSource, Board::class.java)
    TableUtils.createTable(connectionSource, PostHide::class.java)
    TableUtils.createTable(connectionSource, History::class.java)
    TableUtils.createTable(connectionSource, Filter::class.java)
    TableUtils.createTable(connectionSource, SiteModel::class.java)
  }

  @Throws(SQLException::class)
  fun dropTables(connectionSource: ConnectionSource?) {
    TableUtils.dropTable<Loadable, Any>(connectionSource, Loadable::class.java, true)
    TableUtils.dropTable<SavedReply, Any>(connectionSource, SavedReply::class.java, true)
    TableUtils.dropTable<Board, Any>(connectionSource, Board::class.java, true)
    TableUtils.dropTable<PostHide, Any>(connectionSource, PostHide::class.java, true)
    TableUtils.dropTable<History, Any>(connectionSource, History::class.java, true)
    TableUtils.dropTable<Filter, Any>(connectionSource, Filter::class.java, true)
    TableUtils.dropTable<SiteModel, Any>(connectionSource, SiteModel::class.java, true)
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
    Logger.i(TAG, "Upgrading database from $oldVersion to $newVersion")

    if (oldVersion < 25) {
      try {
        boardsDao.executeRawNoArgs("CREATE INDEX board_site_idx ON board(site);")
        boardsDao.executeRawNoArgs("CREATE INDEX board_saved_idx ON board(saved);")
        boardsDao.executeRawNoArgs("CREATE INDEX board_value_idx ON board(value);")
      } catch (e: SQLException) {
        Logger.e(TAG, "Error upgrading to version 25", e)
      }
    }

    if (oldVersion < 26) {
      try {
        postHideDao.executeRawNoArgs("ALTER TABLE threadhide RENAME TO posthide;")
        postHideDao.executeRawNoArgs("ALTER TABLE posthide ADD COLUMN whole_thread INTEGER default 0")
      } catch (e: SQLException) {
        Logger.e(TAG, "Error upgrading to version 26", e)
      }
    }

    if (oldVersion < 27) {
      try {
        // Create indexes for PostHides to speed up posts filtering
        postHideDao.executeRawNoArgs("CREATE INDEX posthide_site_idx ON posthide(site);")
        postHideDao.executeRawNoArgs("CREATE INDEX posthide_board_idx ON posthide(board);")
        postHideDao.executeRawNoArgs("CREATE INDEX posthide_no_idx ON posthide(no);")
      } catch (e: SQLException) {
        Logger.e(TAG, "Error upgrading to version 27", e)
      }
    }

    if (oldVersion < 28) {
      try {
        postHideDao.executeRawNoArgs("ALTER TABLE posthide ADD COLUMN hide INTEGER default 0")
        postHideDao.executeRawNoArgs(
          "ALTER TABLE posthide ADD COLUMN hide_replies_to_this_post INTEGER default 0")
        filterDao.executeRawNoArgs("ALTER TABLE filter ADD COLUMN apply_to_replies INTEGER default 0")
      } catch (e: SQLException) {
        Logger.e(TAG, "Error upgrading to version 28", e)
      }
    }

    if (oldVersion < 29) {
      try {
        postHideDao.executeRawNoArgs("ALTER TABLE posthide ADD COLUMN thread_no INTEGER default 0")
      } catch (e: SQLException) {
        Logger.e(TAG, "Error upgrading to version 29", e)
      }
    }

    if (oldVersion < 30) {
      try {
        boardsDao.executeRawNoArgs("""
    BEGIN TRANSACTION;
    CREATE TEMPORARY TABLE board_backup(archive,bumplimit,value,codeTags,cooldownImages,cooldownReplies,cooldownThreads,countryFlags,customSpoilers,description,id,imageLimit,mathTags,maxCommentChars,maxFileSize,maxWebmSize,key,order,pages,perPage,preuploadCaptcha,saved,site,spoilers,userIds,workSafe);
    INSERT INTO board_backup SELECT archive,bumplimit,value,codeTags,cooldownImages,cooldownReplies,cooldownThreads,countryFlags,customSpoilers,description,id,imageLimit,mathTags,maxCommentChars,maxFileSize,maxWebmSize,key,order,pages,perPage,preuploadCaptcha,saved,site,spoilers,userIds,workSafe FROM board;
    DROP TABLE board;
    CREATE TABLE board(archive,bumplimit,value,codeTags,cooldownImages,cooldownReplies,cooldownThreads,countryFlags,customSpoilers,description,id,imageLimit,mathTags,maxCommentChars,maxFileSize,maxWebmSize,key,order,pages,perPage,preuploadCaptcha,saved,site,spoilers,userIds,workSafe);
    INSERT INTO board SELECT archive,bumplimit,value,codeTags,cooldownImages,cooldownReplies,cooldownThreads,countryFlags,customSpoilers,description,id,imageLimit,mathTags,maxCommentChars,maxFileSize,maxWebmSize,key,order,pages,perPage,preuploadCaptcha,saved,site,spoilers,userIds,workSafe FROM board_backup;
    DROP TABLE board_backup;
    COMMIT;
    """.trimIndent())
      } catch (e: SQLException) {
        Logger.e(TAG, "Error upgrading to version 30")
      }
    }

    if (oldVersion < 31) {
      try {
        loadableDao.executeRawNoArgs("UPDATE loadable SET mode = 1 WHERE mode = 2")
      } catch (e: SQLException) {
        Logger.e(TAG, "Error upgrading to version 31")
      }
    }

    if (oldVersion < 32) {
      try {
        filterDao.executeRawNoArgs("ALTER TABLE filter ADD COLUMN \"order\" INTEGER")
      } catch (e: SQLException) {
        Logger.e(TAG, "Error upgrading to version 32")
      }
    }

    //33 set the new captcha window to default, but it's always on now so this was removed
    if (oldVersion < 34) {
      try {
        filterDao.executeRawNoArgs("ALTER TABLE filter ADD COLUMN onlyOnOP INTEGER default 0")
      } catch (e: SQLException) {
        Logger.e(TAG, "Error upgrading to version 34")
      }
    }

    if (oldVersion < 35) {
      try {
        filterDao.executeRawNoArgs("ALTER TABLE filter ADD COLUMN applyToSaved INTEGER default 0")
      } catch (e: SQLException) {
        Logger.e(TAG, "Error upgrading to version 35")
      }
    }

    if (oldVersion < 36) {
      try {
        filterDao.executeRawNoArgs("CREATE TABLE `saved_thread` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `loadable_id` INTEGER NOT NULL , `last_saved_post_no` INTEGER NOT NULL DEFAULT 0, `is_fully_downloaded` INTEGER NOT NULL DEFAULT 0 , `is_stopped` INTEGER NOT NULL DEFAULT 0);")
        filterDao.executeRawNoArgs("CREATE INDEX loadable_id_idx ON saved_thread(loadable_id);")

        // Because pins now has different type (the ones that watch threads and ones that
        // download them (also they can do both at the same time)) we need to use DEFAULT 1
        // to set flag WatchNewPosts for all of the old pins
        filterDao.executeRawNoArgs("ALTER TABLE pin ADD COLUMN pin_type INTEGER NOT NULL DEFAULT 1")
      } catch (e: SQLException) {
        Logger.e(TAG, "Error upgrading to version 36", e)
      }
    }

    if (oldVersion < 37) {
      try {
        //reset all settings, key was never saved which caused issues
        siteDao.executeRawNoArgs("UPDATE site SET userSettings='{}'")
      } catch (e: SQLException) {
        Logger.e(TAG, "Error upgrading to version 37", e)
      }
    }

    if (oldVersion < 38) {
      try {
        Logger.d(TAG, "Removing Chan55")
        deleteSiteByRegistryID(7)
        Logger.d(TAG, "Removed Chan55 successfully")
      } catch (e: Exception) {
        Logger.e(TAG, "Error upgrading to version 38")
      }
    }

    if (oldVersion < 39) {
      try {
        Logger.d(TAG, "Removing 8Chan")
        deleteSiteByRegistryID(1)
        Logger.d(TAG, "Removed 8Chan successfully")
      } catch (e: Exception) {
        Logger.e(TAG, "Error upgrading to version 39")
      }
    }

    if (oldVersion < 40) {
      try {
        //disable Youtube link parsing if it was enabled in a previous version to prevent issues
        val p: SettingProvider = SharedPreferencesSettingProvider(AndroidUtils.getPreferences())
        p.putBooleanSync("parse_youtube_titles", false)

        //remove arisuchan boards that don't exist anymore
        val where = boardsDao.queryBuilder().where()

        where.and(
          where.eq("site", 3),
          where.or(
            where.eq("value", "cyb"),
            where.eq("value", "feels"),
            where.eq("value", "x"),
            where.eq("value", "z")
          )
        )

        val toRemove: List<Board> = where.query()
        for (board in toRemove) {
          deleteBoard(board)
        }

        //some descriptions changed for arisuchan
        Try {
          val art = boardsDao.queryForEq("key", "art and design")[0]
          if (art != null) {
            art.name = "art and creative"
            boardsDao.update(art)
          }
        }.ignore()

        Try {
          val sci = boardsDao.queryForEq("key", "science and technology")[0]
          if (sci != null) {
            sci.name = "technology"
            boardsDao.update(sci)
          }
        }.ignore()

        Try {
          val diy = boardsDao.queryForEq("key", "diy and projects")[0]
          if (diy != null) {
            diy.name = "shape your world"
            boardsDao.update(diy)
          }
        }.ignore()

        Try {
          val ru = boardsDao.queryForEq("key", "киберпанк-доска")[0]
          if (ru != null) {
            ru.name = "Киберпанк"
            boardsDao.update(ru)
          }
        }.ignore()
      } catch (e: SQLException) {
        Logger.e(TAG, "Error upgrading to version 40")
      }
    }

    if (oldVersion < 41) {
      // enable the following as default for 4.10.2

      ChanSettings.parsePostImageLinks.set(true)
      val prefs = SharedPreferencesSettingProvider(AndroidUtils.getPreferences())
      prefs.putBooleanSync("parse_youtube_titles", true)
    }

    if (oldVersion < 42) {
      try {
        //remove wired-7 boards that don't exist anymore
        val where = boardsDao.queryBuilder().where()
        where.and(where.eq("site", 6), where.eq("value", "18"))

        for (board in where.query()) {
          deleteBoard(board)
        }
      } catch (e: Exception) {
        Logger.e(TAG, "Error upgrading to version 42")
      }
    }

    if (oldVersion < 43) {
      try {
        Logger.d(TAG, "Removing Arisuchan")
        deleteSiteByRegistryID(3)
        Logger.d(TAG, "Removed Arisuchan successfully")
      } catch (e: Exception) {
        Logger.e(TAG, "Error upgrading to version 44")
      }
    }

    if (oldVersion < 44) {
      try {
        val prefs = SharedPreferencesSettingProvider(AndroidUtils.getPreferences())

        // the PersistableChanState class was added, so some settings are being moved over there
        // get settings from before the move

        val watchLastCount = getSettingForKey(prefs, "preference_watch_last_count", IntegerSetting::class.java)!!
        val previousVersion = getSettingForKey(prefs, "preference_previous_version", IntegerSetting::class.java)!!
        val updateCheckTime = getSettingForKey(prefs, "update_check_time", LongSetting::class.java)!!
        val previousDevHash = getSettingForKey(prefs, "previous_dev_hash", StringSetting::class.java)!!
        val filterWatchIgnores = getSettingForKey(prefs, "filter_watch_last_ignored_set", StringSetting::class.java)!!
        val youtubeTitleCache = getSettingForKey(prefs, "yt_title_cache", StringSetting::class.java)!!
        val youtubeDurCache = getSettingForKey(prefs, "yt_dur_cache", StringSetting::class.java)!!

        // update a few of them
        PersistableChanState.watchLastCount.setSync(watchLastCount.get())
        PersistableChanState.previousVersion.setSync(previousVersion.get())
        PersistableChanState.updateCheckTime.setSync(updateCheckTime.get())
        PersistableChanState.previousDevHash.setSync(previousDevHash.get())

        // remove them; they are now in PersistableChanState
        prefs.removeSync(watchLastCount.key)
        prefs.removeSync(previousVersion.key)
        prefs.removeSync(updateCheckTime.key)
        prefs.removeSync(previousDevHash.key)
        prefs.removeSync(filterWatchIgnores.key)
        prefs.removeSync(youtubeTitleCache.key)
        prefs.removeSync(youtubeDurCache.key)

        // Preference key changed, move it over
        val uploadCrashLogs = getSettingForKey(prefs, "auto_upload_crash_logs", BooleanSetting::class.java)!!
        ChanSettings.collectCrashLogs.set(uploadCrashLogs.get())
      } catch (e: Exception) {
        Logger.e(TAG, "Error upgrading to version 44")
      }
    }

    if (oldVersion < 45) {
      try {
        val prefs: SettingProvider = SharedPreferencesSettingProvider(AndroidUtils.getPreferences())
        // These two settings were merged into one - parse_youtube_titles_and_duration
        prefs.removeSync("parse_youtube_titles")
        prefs.removeSync("parse_youtube_duration")
        val state: SettingProvider = SharedPreferencesSettingProvider(AndroidUtils.getAppState())
        // Youtube titles and durations cache was removed from PersistableChanState and moved
        // into the database
        state.removeSync("yt_cache")
      } catch (error: Throwable) {
        Logger.e(TAG, "Error upgrading to version 45")
      }
    }
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

  /**
   * This method deletes a site by the id it was given in SiteRegistry, but in the database
   * rather than in the application. This is useful for when a site's classes have been removed
   * from the application
   *
   * @param id The ID given in SiteRegistry before this site's class was removed
   */
  @Throws(SQLException::class)
  fun deleteSiteByRegistryID(id: Int) {
    // NOTE: most of this is a copy of the methods used by the runtime version, but condensed
    // down into one method convert the SiteRegistry id to the actual database id
    val allSites = siteDao.queryForAll()
    var toDelete: SiteModel? = null

    for (siteModel in allSites) {
      val siteModelConfig = siteModel.loadConfigFields()
      if (siteModelConfig.first!!.classId == id) {
        toDelete = siteModel
        break
      }
    }

    // if we can't find it then it doesn't exist so we don't need to delete anything
    if (toDelete == null) {
      return
    }

    // filters
    val filtersToDelete: MutableList<Filter> = ArrayList()
    for (filter in filterDao.queryForAll()) {
      if (filter.allBoards || TextUtils.isEmpty(filter.boards)) {
        continue
      }

      for (uniqueId in filter.boards.split(",").toTypedArray()) {
        val split = uniqueId.split(":").toTypedArray()
        if (split.size == 2 && split[0].toInt() == toDelete.id) {
          filtersToDelete.add(filter)
          break
        }
      }
    }

    val filterIdSet: MutableSet<Int?> = HashSet()
    for (filter in filtersToDelete) {
      filterIdSet.add(filter.id)
    }

    val filterDelete = filterDao.deleteBuilder()
    filterDelete.where().`in`("id", filterIdSet)
    val deletedCountFilters = filterDelete.delete()

    check(deletedCountFilters == filterIdSet.size) {
      ("Deleted count didn't equal filterIdList.size(). " +
        "(deletedCount = " + deletedCountFilters + "), "
        + "(filterIdSet = " + filterIdSet.size + ")")
    }

    // boards
    val boardDelete: DeleteBuilder<*, *> = boardsDao.deleteBuilder()
    boardDelete.where().eq("site", toDelete.id)
    boardDelete.delete()

    // loadables (saved threads, pins, history, loadables)
    val siteLoadables = loadableDao.queryForEq("site", toDelete.id)

    if (siteLoadables.isNotEmpty()) {
      val loadableIdSet: MutableSet<Int?> = HashSet()
      for (loadable in siteLoadables) {
        loadableIdSet.add(loadable.id)
      }

      // history
      val historyDelete = historyDao.deleteBuilder()
      historyDelete.where().`in`("loadable_id", loadableIdSet)
      historyDelete.delete()

      // loadables
      val loadableDelete = loadableDao.deleteBuilder()
      loadableDelete.where().`in`("id", loadableIdSet)

      val deletedCountLoadables = loadableDelete.delete()
      check(loadableIdSet.size == deletedCountLoadables) {
        ("Deleted count didn't equal loadableIdSet.size(). " +
          "(deletedCount = " + deletedCountLoadables + "), " +
          "(loadableIdSet = " + loadableIdSet.size + ")")
      }
    }

    // saved replies
    val savedReplyDelete = savedDao.deleteBuilder()
    savedReplyDelete.where().eq("site", toDelete.id)
    savedReplyDelete.delete()

    // thread hides
    val threadHideDelete = postHideDao.deleteBuilder()
    threadHideDelete.where().eq("site", toDelete.id)
    threadHideDelete.delete()

    // site itself
    val siteDelete = siteDao.deleteBuilder()
    siteDelete.where().eq("id", toDelete.id)
    siteDelete.delete()
  }

  /**
   * Deletes a board on a given site when the site no longer supports the given board
   */
  @Throws(SQLException::class)
  fun deleteBoard(board: Board) {
    // filters
    for (filter in filterDao.queryForAll()) {
      if (filter.allBoards || TextUtils.isEmpty(filter.boards)) {
        continue
      }

      val keep: MutableList<String?> = ArrayList()
      for (uniqueId in filter.boards.split(",").toTypedArray()) {
        val split = uniqueId.split(":").toTypedArray()
        if (!(split.size == 2 && split[0].toInt() == board.siteId && split[1] == board.code)) {
          keep.add(uniqueId)
        }
      }

      filter.boards = TextUtils.join(",", keep)

      // disable, but don't delete filters in case they're still wanted
      if (TextUtils.isEmpty(filter.boards)) {
        filter.enabled = false
      }

      filterDao.update(filter)
    }

    // loadables (saved threads, pins, history, loadables)
    val siteLoadables = loadableDao.queryForEq("site", board.siteId)

    if (siteLoadables.isNotEmpty()) {
      val loadableIdSet: MutableSet<Int> = HashSet()
      for (loadable in siteLoadables) {
        //only get the loadables with the same board code
        if (loadable.boardCode == board.code) {
          loadableIdSet.add(loadable.id)
        }
      }

      //history
      val historyDelete = historyDao.deleteBuilder()
      historyDelete.where().`in`("loadable_id", loadableIdSet)
      historyDelete.delete()

      //loadables
      val loadableDelete = loadableDao.deleteBuilder()
      loadableDelete.where().`in`("id", loadableIdSet)
      val deletedCountLoadables = loadableDelete.delete()
      check(loadableIdSet.size == deletedCountLoadables) {
        ("Deleted count didn't equal loadableIdSet.size(). " +
          "(deletedCount = " + deletedCountLoadables + "), " +
          "(loadableIdSet = " + loadableIdSet.size + ")")
      }
    }

    // saved replies
    val savedReplyDelete = savedDao.deleteBuilder()
    savedReplyDelete.where().eq("site", board.siteId).and().eq("board", board.code)
    savedReplyDelete.delete()

    // thread hides
    val threadHideDelete = postHideDao.deleteBuilder()
    threadHideDelete.where().eq("site", board.siteId).and().eq("board", board.code)
    threadHideDelete.delete()

    // board itself
    val boardDelete: DeleteBuilder<*, *> = boardsDao.deleteBuilder()
    boardDelete.where().eq("site", board.siteId).and().eq("value", board.code)
    boardDelete.delete()
  }

  companion object {
    private const val TAG = "DatabaseHelper"
    private const val DATABASE_NAME = "ChanDB"
    private const val DATABASE_VERSION = 45

    /**
     * @param p    the provider to get the setting from
     * @param key  the key for the setting
     * @param type the class of the setting, see parameter T; pass in Setting.class for whatever
     * setting class you need
     * @param <T>  the type of the setting, should extend Setting
     * @return the setting requested, or null
    </T> */
    fun <T> getSettingForKey(p: SettingProvider?, key: String?, type: Class<T>): T? {
      return if (!Setting::class.java.isAssignableFrom(type)) null else try {
        val c: Constructor<*> = type.getConstructor(
          SettingProvider::class.java,
          String::class.java,
          type
        )

        c.isAccessible = true
        val returnSetting = c.newInstance(p, key, null)
        c.isAccessible = false

        returnSetting as T
      } catch (failedSomething: Exception) {
        Logger.e(TAG, "Reflection failed", failedSomething)
        null
      }
    }
  }

}