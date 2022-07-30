package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.repository.ImportExportRepository
import com.github.k1rakishou.chan.features.settings.ImportExportScreen
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.screens.delegate.ImportExportSettingsDelegate
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadingDelegate
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import kotlinx.coroutines.CoroutineScope

class ImportExportSettingsScreen(
  context: Context,
  coroutineScope: CoroutineScope,
  private val navigationController: NavigationController,
  private val fileChooser: FileChooser,
  private val fileManager: FileManager,
  private val dialogFactory: DialogFactory,
  private val appRestarter: AppRestarter,
  private val importExportRepository: ImportExportRepository,
  private val threadDownloadingDelegate: ThreadDownloadingDelegate
) : BaseSettingsScreen(
  context,
  ImportExportScreen,
  R.string.settings_import_export
) {
  private val importExportSettingsDelegate by lazy {
    ImportExportSettingsDelegate(
      context,
      coroutineScope,
      navigationController,
      appRestarter,
      fileChooser,
      fileManager,
      dialogFactory,
      importExportRepository,
      threadDownloadingDelegate
    )
  }

  override suspend fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMainSettingsGroup(),
      buildImportFromKurobaGroup()
    )
  }

  private fun buildImportFromKurobaGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = ImportExportScreen.ImportFromKurobaSettingsGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.import_from_kuroba),
          groupIdentifier = identifier
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier =  ImportExportScreen.ImportFromKurobaSettingsGroup.ImportSettingsFromKuroba,
          topDescriptionIdFunc = { R.string.import_from_kuroba },
          bottomDescriptionIdFunc = { R.string.import_settings_from_kuroba },
          callback = { importExportSettingsDelegate.onImportFromKurobaClicked() }
        )

        group
      }
    )
  }

  private fun buildMainSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = ImportExportScreen.MainSettingsGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
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

        group
      }
    )
  }

}