package com.github.k1rakishou.chan.core.helper.migration

import android.content.Context
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.persist_state.PersistableChanState

interface ApplicationMigration {
  val version: Int
  val changes: String?

  fun perform(context: Context)
}

class ApplicationMigrationHelper {
  private val _appliedMigrations = mutableListOf<Int>()
  val appliedMigrations: List<Int>
    get() = _appliedMigrations.toList()

  private val _migrations = listOf<ApplicationMigration>(
    AppMigration_V0_V1(),
    AppMigration_V1_V2(),
    AppMigration_V2_V3(),
  )

  init {
    if (AppModuleAndroidUtils.isDevBuild) {
      check(_migrations.size == _migrations.map { it.version }.toHashSet().size) { "Not all migrations have unique ids" }
      var currentVersion = _migrations.minBy { it.version }.version

      for (migration in _migrations) {
        check(currentVersion == migration.version) { "Expected migration ${currentVersion}, got ${migration.version}" }
        ++currentVersion
      }
    }
  }

  fun changelog(migrationVersion: Int): String? {
    return _migrations
      .firstOrNull { migration -> migration.version == migrationVersion }
      ?.changes
  }

  fun processMigrations(context: Context) {
    val prevVersion = PersistableChanState.applicationMigrationVersion.get()
    if (prevVersion >= LATEST_VERSION) {
      Logger.d(TAG, "performMigration $prevVersion -> $LATEST_VERSION skipping")
      return
    }

    try {
      Logger.d(TAG, "performMigration $prevVersion -> $LATEST_VERSION start")

      for (migration in _migrations) {
        if (prevVersion < migration.version) {
          Logger.d(TAG, "performMigrationV${migration.version} begin")
          migration.perform(context)
          Logger.d(TAG, "performMigrationV${migration.version} success")

          _appliedMigrations.add(migration.version)
        } else {
          Logger.d(TAG, "performMigrationV${migration.version} skipping")
        }
      }

      PersistableChanState.applicationMigrationVersion.set(LATEST_VERSION)
      Logger.d(TAG, "performMigration $prevVersion -> $LATEST_VERSION success")
    } catch (error: Throwable) {
      Logger.e(TAG, "performMigration $prevVersion -> $LATEST_VERSION error", error)
    }
  }

  companion object {
    private const val TAG = "ApplicationMigrationManager"
    const val LATEST_VERSION = 3
  }

}