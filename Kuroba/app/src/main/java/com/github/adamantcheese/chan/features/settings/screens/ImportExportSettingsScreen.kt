package com.github.adamantcheese.chan.features.settings.screens

import android.content.Context
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.NavigationController
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.features.settings.ImportExportScreen
import com.github.adamantcheese.chan.features.settings.SettingsGroup
import com.github.adamantcheese.chan.features.settings.screens.delegate.ImportExportSettingsDelegate
import com.github.adamantcheese.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager

class ImportExportSettingsScreen(
  context: Context,
  private val navigationController: NavigationController,
  private val fileChooser: FileChooser,
  private val fileManager: FileManager,
  private val databaseManager: DatabaseManager
) : BaseSettingsScreen(
  context,
  ImportExportScreen,
  R.string.settings_import_export
) {
  private val importExportSettingsDelegate by lazy {
    ImportExportSettingsDelegate(
      context,
      navigationController,
      fileChooser,
      fileManager,
      databaseManager
    )
  }

  override fun onDestroy() {
    super.onDestroy()

    importExportSettingsDelegate.onDestroy()
  }

  override fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMainSettingsScreen()
    )
  }

  private fun buildMainSettingsScreen(): SettingsGroup.SettingsGroupBuilder {
    val identifier = ImportExportScreen.MainSettingsGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.import_or_export_settings),
          groupIdentifier = identifier
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier =  ImportExportScreen.MainSettingsGroup.ExportSetting,
          topDescriptionIdFunc = { R.string.export_settings },
          bottomDescriptionIdFunc = { R.string.export_settings_to_a_file },
          callback = { importExportSettingsDelegate.onExportClicked() }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier =  ImportExportScreen.MainSettingsGroup.ImportSetting,
          topDescriptionIdFunc = { R.string.import_settings },
          bottomDescriptionIdFunc = { R.string.import_settings_from_a_file },
          callback = { importExportSettingsDelegate.onImportClicked() }
        )

        return group
      }
    )
  }

}