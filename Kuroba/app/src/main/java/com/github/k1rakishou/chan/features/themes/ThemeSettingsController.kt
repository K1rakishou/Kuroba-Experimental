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
package com.github.k1rakishou.chan.features.themes

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.viewpager.widget.ViewPager
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem
import com.github.k1rakishou.chan.ui.view.ViewPagerAdapter
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.ViewUtils.changeEdgeEffect
import com.github.k1rakishou.chan.utils.awaitUntilGloballyLaidOut
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.getComplementaryColor
import com.github.k1rakishou.core_themes.ThemeParser
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.callback.FileChooserCallback
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import com.github.k1rakishou.persist_state.PersistableChanState
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*
import javax.inject.Inject

class ThemeSettingsController(context: Context) : Controller(context),
  ToolbarMenuItem.ToobarThreedotMenuCallback {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postFilterManager: PostFilterManager
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var fileManager: FileManager
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private lateinit var pager: ViewPager
  private lateinit var currentThemeIndicator: TextView
  private lateinit var applyThemeFab: ColorizableFloatingActionButton
  private var currentItemIndex = 0

  private val themeControllerHelper by lazy {
    ThemeControllerHelper(themeEngine, postFilterManager, archivesManager)
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @Suppress("DEPRECATION")
  override fun onCreate() {
    super.onCreate()

    navigation.setTitle(R.string.settings_screen_theme)
    navigation.swipeable = false

    if (AndroidUtils.isAndroid10()) {
      navigation
        .buildMenu(context)
        .withOverflow(navigationController, this)
        .withCheckableSubItem(
          ACTION_IGNORE_DARK_NIGHT_MODE,
          R.string.action_ignore_dark_night_mode,
          true,
          ChanSettings.ignoreDarkNightMode.get()
        ) { item -> onIgnoreDarkNightModeClick(item) }
        .build()
        .build()
    }

    view = inflate(context, R.layout.controller_theme)
    pager = view.findViewById(R.id.pager)
    currentThemeIndicator = view.findViewById(R.id.current_theme_indicator)
    applyThemeFab = view.findViewById(R.id.apply_theme_button)

    applyThemeFab.setOnClickListener {
      val switchToDark = currentItemIndex != 0
      themeEngine.switchTheme(switchToDarkTheme = switchToDark)
    }

    currentItemIndex = if (themeEngine.chanTheme.isLightTheme) {
      0
    } else {
      1
    }

    updateCurrentThemeIndicator(true)
    mainScope.launch {
      pager.awaitUntilGloballyLaidOut(waitForWidth = true)

      reload()
    }

    if (AndroidUtils.isAndroid10()) {
      showIgnoreDayNightModeDialog()
    }
  }

  private fun showIgnoreDayNightModeDialog() {
    if (ChanSettings.ignoreDarkNightMode.get()) {
      return
    }

    if (PersistableChanState.themesIgnoreSystemDayNightModeMessageShown.get()) {
      return
    }

    dialogFactory.createSimpleInformationDialog(
      context = context,
      titleText = context.getString(R.string.android_day_night_mode_dialog_title),
      descriptionText = context.getString(R.string.android_day_night_mode_dialog_description)
    )

    PersistableChanState.themesIgnoreSystemDayNightModeMessageShown.set(true)
  }

  private fun onIgnoreDarkNightModeClick(item: ToolbarMenuSubItem) {
    navigation.findCheckableSubItem(ACTION_IGNORE_DARK_NIGHT_MODE)?.let { subItem ->
      subItem.isChecked = ChanSettings.ignoreDarkNightMode.toggle()
    }
  }

  private fun reload() {
    val root = view.findViewById<LinearLayout>(R.id.root)

    val adapter = Adapter(pager.width)
    pager.adapter = adapter
    pager.setCurrentItem(currentItemIndex, false)
    pager.changeEdgeEffect(themeEngine.chanTheme)
    pager.setOnPageChangeListener(object : ViewPager.OnPageChangeListener {
      override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        // no-op
      }

      override fun onPageSelected(position: Int) {
        updateColors(adapter, position, root)
        currentItemIndex = position
      }

      override fun onPageScrollStateChanged(state: Int) {
        // no-op
      }
    })

    view.postDelayed({ updateColors(adapter, 0, root) }, UPDATE_COLORS_DELAY_MS)
  }

  private fun resetTheme(item: ToolbarMenuSubItem) {
    val isDarkTheme = when (item.id) {
      ACTION_RESET_DARK_THEME -> true
      ACTION_RESET_LIGHT_THEME -> false
      else -> throw IllegalStateException("Unknown action: ${item.id}")
    }

    if (!themeEngine.resetTheme(isDarkTheme)) {
      showToastLong(context.getString(R.string.theme_settings_controller_failed_to_reset_theme))
      return
    }

    reload()
  }

  private fun exportThemeToClipboard(item: ToolbarMenuSubItem) {
    val isDarkTheme = when (item.id) {
      ACTION_EXPORT_DARK_THEME_TO_CLIPBOARD -> true
      ACTION_EXPORT_LIGHT_THEME_TO_CLIPBOARD -> false
      else -> throw IllegalStateException("Unknown action: ${item.id}")
    }

    val themeJson = themeEngine.exportThemeToString(isDarkTheme)
    if (themeJson == null) {
      showToastLong("Failed to export theme into a json string!")
      return
    }

    AndroidUtils.setClipboardContent("Theme json", themeJson)
  }

  private fun exportThemeToFile(item: ToolbarMenuSubItem) {
    val isDarkTheme = when (item.id) {
      ACTION_EXPORT_DARK_THEME_TO_FILE -> true
      ACTION_EXPORT_LIGHT_THEME_TO_FILE -> false
      else -> throw IllegalStateException("Unknown action: ${item.id}")
    }

    val fileName = if (isDarkTheme) {
      ThemeParser.DARK_THEME_FILE_NAME
    } else {
      ThemeParser.LIGHT_THEME_FILE_NAME
    }

    fileChooser.openCreateFileDialog(fileName, object : FileCreateCallback() {
      override fun onCancel(reason: String) {
        showToastLong(context.getString(R.string.theme_settings_controller_canceled, reason))
      }

      override fun onResult(uri: Uri) {
        onFileToExportSelected(uri, isDarkTheme)
      }
    })
  }

  private fun importTheme(item: ToolbarMenuSubItem) {
    val isDarkTheme = when (item.id) {
      ACTION_IMPORT_DARK_THEME -> true
      ACTION_IMPORT_LIGHT_THEME -> false
      else -> throw IllegalStateException("Unknown action: ${item.id}")
    }

    fileChooser.openChooseFileDialog(object : FileChooserCallback() {
      override fun onCancel(reason: String) {
        showToastLong(context.getString(R.string.theme_settings_controller_canceled, reason))
      }

      override fun onResult(uri: Uri) {
        onFileToImportSelected(uri, isDarkTheme)
      }
    })
  }

  private fun onFileToExportSelected(uri: Uri, isDarkTheme: Boolean) {
    val file = fileManager.fromUri(uri)
    if (file == null) {
      showToastLong(context.getString(R.string.theme_settings_controller_failed_to_open_output_file))
      return
    }

    mainScope.launch {
      when (val result = themeEngine.exportThemeToFile(file, isDarkTheme)) {
        is ThemeParser.ThemeExportResult.Error -> {
          val message = context.getString(
            R.string.theme_settings_controller_failed_to_export_theme,
            result.error.errorMessageOrClassName()
          )

          showToastLong(message)
        }
        ThemeParser.ThemeExportResult.Success -> {
          showToastLong(context.getString(R.string.done))
        }
      }.exhaustive
    }
  }

  private fun importThemeFromClipboard(item: ToolbarMenuSubItem) {
    val isDarkTheme = when (item.id) {
      ACTION_IMPORT_DARK_THEME_FROM_CLIPBOARD -> true
      ACTION_IMPORT_LIGHT_THEME_FROM_CLIPBOARD -> false
      else -> throw IllegalStateException("Unknown action: ${item.id}")
    }

    val clipboardContent = AndroidUtils.getClipboardContent()
    if (clipboardContent.isNullOrEmpty()) {
      val message = context.getString(
        R.string.theme_settings_controller_failed_to_import_theme,
        "Clipboard is empty"
      )

      showToastLong(message)
      return
    }

    mainScope.launch {
      handleParseThemeResult(themeEngine.tryParseAndApplyTheme(clipboardContent, isDarkTheme))
    }
  }

  private fun onFileToImportSelected(uri: Uri, isDarkTheme: Boolean) {
    val file = fileManager.fromUri(uri)
    if (file == null) {
      showToastLong(context.getString(R.string.theme_settings_controller_failed_to_open_theme_file))
      return
    }

    mainScope.launch {
      handleParseThemeResult(themeEngine.tryParseAndApplyTheme(file, isDarkTheme))
    }
  }

  private fun handleParseThemeResult(result: ThemeParser.ThemeParseResult) {
    when (result) {
      is ThemeParser.ThemeParseResult.Error -> {
        val message = context.getString(
          R.string.theme_settings_controller_failed_to_import_theme,
          result.error.errorMessageOrClassName()
        )

        showToastLong(message)
      }
      is ThemeParser.ThemeParseResult.AttemptToImportWrongTheme -> {
        val lightThemeText = context.getString(R.string.theme_settings_controller_theme_light)
        val darkThemeText = context.getString(R.string.theme_settings_controller_theme_dark)

        val themeTypeText = if (result.themeIsLight) {
          lightThemeText
        } else {
          darkThemeText
        }

        val themeSlotTypeText = if (result.themeSlotIsLight) {
          lightThemeText
        } else {
          darkThemeText
        }

        val message = context.getString(
          R.string.theme_settings_controller_wrong_theme_type,
          themeTypeText,
          themeSlotTypeText
        )

        showToastLong(message)
      }
      is ThemeParser.ThemeParseResult.BadName -> {
        val message = context.getString(
          R.string.theme_settings_controller_failed_to_parse_bad_name,
          result.name
        )

        showToastLong(message)
      }
      is ThemeParser.ThemeParseResult.FailedToParseSomeFields -> {
        val fieldsString = buildString {
          appendLine()
          appendLine("Total fields failed to parse: ${result.unparsedFields.size}")
          appendLine()

          result.unparsedFields.forEach { unparsedField ->
            appendLine("'$unparsedField'")
          }

          appendLine()
          appendLine(context.getString(R.string.theme_settings_controller_failed_to_parse_some_fields_description))
        }

        dialogFactory.createSimpleInformationDialog(
          context = context,
          titleText = context.getString(R.string.theme_settings_controller_failed_to_parse_some_fields_title),
          descriptionText = fieldsString
        )

        Unit
      }
      is ThemeParser.ThemeParseResult.Success -> {
        showToastLong(context.getString(R.string.done))
        reload()
      }
    }.exhaustive
  }

  private fun showToastLong(message: String) {
    showToast(message, Toast.LENGTH_LONG)
  }

  private fun updateColors(adapter: Adapter, position: Int, root: LinearLayout) {
    val theme = adapter.themeMap[position]
      ?: return

    val compositeColor = (theme.backColor.toLong() + theme.primaryColor.toLong()) / 2
    val backgroundColor = getComplementaryColor(compositeColor.toInt())
    root.setBackgroundColor(backgroundColor)

    updateCurrentThemeIndicator(theme.isLightTheme)
  }

  @SuppressLint("SetTextI18n")
  private fun updateCurrentThemeIndicator(isLightTheme: Boolean) {
    val themeType = if (isLightTheme) {
      context.getString(R.string.theme_settings_controller_theme_light)
    } else {
      context.getString(R.string.theme_settings_controller_theme_dark)
    }

    currentThemeIndicator.text = context.getString(
      R.string.theme_settings_controller_theme,
      themeType
    )
  }

  private inner class Adapter(
    private val postCellDataWidthNoPaddings: Int = 0
  ) : ViewPagerAdapter() {
    val themeMap = mutableMapOf<Int, ChanTheme>()

    override fun getView(position: Int, parent: ViewGroup): View {
      val theme = when (position) {
        0 -> themeEngine.lightTheme()
        1 -> themeEngine.darkTheme()
        else -> throw IllegalStateException("Bad position: $position")
      }

      themeMap[position] = theme

      return runBlocking { createSimpleThreadViewInternal(theme, postCellDataWidthNoPaddings) }
    }

    private suspend fun createSimpleThreadViewInternal(
      theme: ChanTheme,
      postCellDataWidthNoPaddings: Int
    ): CoordinatorLayout {
      val navigationItem = NavigationItem()
      navigationItem.title = theme.name
      navigationItem.hasBack = false

      navigationItem.buildMenu(context)
        .withOverflow(navigationController, this@ThemeSettingsController)
        .addSubItems(theme.isDarkTheme)
        .build()
        .build()

      return themeControllerHelper.createSimpleThreadView(
        context,
        theme,
        navigationItem,
        requireNavController(),
        ThemeControllerHelper.Options(
          showMoreThemesButton = true,
          refreshThemesControllerFunc = { reload() }
        ),
        postCellDataWidthNoPaddings
      )
    }

    override fun getCount(): Int {
      return 2
    }
  }

  private fun NavigationItem.MenuOverflowBuilder.addSubItems(
    darkTheme: Boolean,
  ): NavigationItem.MenuOverflowBuilder {
    if (darkTheme) {
      return this.withSubItem(
        ACTION_IMPORT_DARK_THEME,
        R.string.action_import_dark_theme,
        { item -> importTheme(item) }
      )
        .withSubItem(
          ACTION_IMPORT_DARK_THEME_FROM_CLIPBOARD,
          R.string.action_import_dark_theme_from_clipboard,
          { item -> importThemeFromClipboard(item) }
        )
        .withSubItem(
          ACTION_EXPORT_DARK_THEME_TO_FILE,
          R.string.action_export_dark_theme_to_file,
          { item -> exportThemeToFile(item) }
        )
        .withSubItem(
          ACTION_EXPORT_DARK_THEME_TO_CLIPBOARD,
          R.string.action_export_dark_theme_to_clipboard,
          { item -> exportThemeToClipboard(item) }
        )
        .withSubItem(
          ACTION_RESET_DARK_THEME,
          R.string.action_reset_dark_theme,
          { item -> resetTheme(item) }
        )
    } else {
      return this.withSubItem(
        ACTION_IMPORT_LIGHT_THEME,
        R.string.action_import_light_theme,
        { item -> importTheme(item) }
      )
        .withSubItem(
          ACTION_IMPORT_LIGHT_THEME_FROM_CLIPBOARD,
          R.string.action_import_light_theme_from_clipboard,
          { item -> importThemeFromClipboard(item) }
        )
        .withSubItem(
          ACTION_EXPORT_LIGHT_THEME_TO_FILE,
          R.string.action_export_light_theme_to_file,
          { item -> exportThemeToFile(item) }
        )
        .withSubItem(
          ACTION_EXPORT_LIGHT_THEME_TO_CLIPBOARD,
          R.string.action_export_light_theme_to_clipboard,
          { item -> exportThemeToClipboard(item) }
        )
        .withSubItem(
          ACTION_RESET_LIGHT_THEME,
          R.string.action_reset_light_theme,
          { item -> resetTheme(item) }
        )
    }
  }

  override fun onMenuShown() {
    // no-op
  }

  override fun onMenuHidden() {
    // no-op
  }

  companion object {
    private const val ACTION_IMPORT_LIGHT_THEME = 1
    private const val ACTION_IMPORT_DARK_THEME = 2
    private const val ACTION_IMPORT_LIGHT_THEME_FROM_CLIPBOARD = 3
    private const val ACTION_IMPORT_DARK_THEME_FROM_CLIPBOARD = 4
    private const val ACTION_EXPORT_LIGHT_THEME_TO_FILE = 5
    private const val ACTION_EXPORT_DARK_THEME_TO_FILE = 6
    private const val ACTION_RESET_LIGHT_THEME = 7
    private const val ACTION_RESET_DARK_THEME = 8
    private const val ACTION_IGNORE_DARK_NIGHT_MODE = 9
    private const val ACTION_EXPORT_LIGHT_THEME_TO_CLIPBOARD = 10
    private const val ACTION_EXPORT_DARK_THEME_TO_CLIPBOARD = 11

    private const val UPDATE_COLORS_DELAY_MS = 250L
  }

}
