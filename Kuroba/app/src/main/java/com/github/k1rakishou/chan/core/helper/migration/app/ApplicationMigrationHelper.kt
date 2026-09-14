package com.github.k1rakishou.chan.core.helper.migration.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettings

interface ApplicationMigration {
  val version: Int
  val changes: String?

  fun perform(context: Context)
}

class ApplicationMigrationHelper(
  private val kurobaSettings: KurobaSettings
) {
  private val _appliedMigrations = mutableListOf<Int>()
  val appliedMigrations: List<Int>
    get() = _appliedMigrations.toList()

  private val _migrations = listOf<ApplicationMigration>(
    AppMigration_V0_V1(),
    AppMigration_V1_V2(),
    AppMigration_V2_V3(),
    AppMigration_V3_V4(),
  )

  init {
    if (AppModuleAndroidUtils.isDevBuild) {
      check(_migrations.size == _migrations.map { it.version }.toHashSet().size) {
        "Not all migrations have unique ids"
      }

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
    var prevVersion = kurobaSettings.nonBackupable.applicationMigrationVersion.readBlocking()

    if (prevVersion == UNSET_VERSION) {
      if (isFreshInstall(context)) {
        // Nothing to migrate on a fresh install, just persist the current version
        Logger.d(TAG, "performMigration fresh install, setting version to $LATEST_VERSION")
        kurobaSettings.nonBackupable.applicationMigrationVersion.writeBlocking(LATEST_VERSION)
        return
      }

      // The migration version was never persisted by the previous versions of the app (it used to default to
      // LATEST_VERSION without ever being written) so we don't know which migrations were applied.
      Logger.d(TAG, "performMigration version is unset on app update, assuming $UNKNOWN_UPDATE_VERSION")
      prevVersion = UNKNOWN_UPDATE_VERSION
    }

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

      kurobaSettings.nonBackupable.applicationMigrationVersion.writeBlocking(LATEST_VERSION)
      Logger.d(TAG, "performMigration $prevVersion -> $LATEST_VERSION success")
    } catch (error: Throwable) {
      Logger.e(TAG, "performMigration $prevVersion -> $LATEST_VERSION error", error)
    }
  }

  private fun isFreshInstall(context: Context): Boolean {
    return try {
      val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
      }

      // lastUpdateTime is only different from firstInstallTime after the app has been updated at least once
      packageInfo.firstInstallTime == packageInfo.lastUpdateTime
    } catch (error: Throwable) {
      Logger.e(TAG, "isFreshInstall() error", error)
      false
    }
  }

  companion object {
    private const val TAG = "ApplicationMigrationManager"
    const val LATEST_VERSION = 4

    // Default value of the migration version setting, used to detect that the version was never persisted
    const val UNSET_VERSION = -1

    // Version assumed when updating from an app version that never persisted the migration version.
    // Migrations up to this version are not executed.
    private const val UNKNOWN_UPDATE_VERSION = 3
  }
}