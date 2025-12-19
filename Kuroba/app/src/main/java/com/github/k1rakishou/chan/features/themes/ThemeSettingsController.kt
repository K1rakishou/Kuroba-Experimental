package com.github.k1rakishou.chan.features.themes

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
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
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarState
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuCheckableOverflowItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuOverflowItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarOverflowMenuBuilder
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.ui.view.ViewPagerAdapter
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.ViewUtils.changeEdgeEffect
import com.github.k1rakishou.chan.utils.awaitUntilGloballyLaidOutAndGetSize
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeParser
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.callback.FileChooserCallback
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import com.github.k1rakishou.persist_state.PersistableChanState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class ThemeSettingsController(context: Context) : Controller(context), WindowInsetsListener {

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

  private val themeControllerHelper by lazy {
    ThemeControllerHelper(themeEngine, postFilterManager, archivesManager)
  }

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @Suppress("DEPRECATION")
  override fun onCreate() {
    super.onCreate()

    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags(swipeable = false)
    )

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.Id(R.string.settings_screen_theme)
      ),
      menuBuilder = {
        if (AndroidUtils.isAndroid10) {
          withOverflowMenu {
            withCheckableOverflowMenuItem(
              id = ACTION_IGNORE_DARK_NIGHT_MODE,
              stringId = R.string.action_ignore_dark_night_mode,
              visible = true,
              checked = ChanSettings.ignoreDarkNightMode.get(),
              onClick = { item -> onIgnoreDarkNightModeClick(item) }
            )
          }
        }
      }
    )

    view = inflate(context, R.layout.controller_theme)
    pager = view.findViewById(R.id.pager)
    currentThemeIndicator = view.findViewById(R.id.current_theme_indicator)

    val initialItemIndex = if (themeEngine.chanTheme.isLightTheme) 0 else 1

    updateCurrentThemeIndicator(true)
    controllerScope.launch {
      val (width, _) = pager.awaitUntilGloballyLaidOutAndGetSize(waitForWidth = true)
      reload(
        itemIndex = initialItemIndex,
        postCellDataWidthNoPaddings = width
      )
    }

    if (AndroidUtils.isAndroid10) {
      showIgnoreDayNightModeDialog()
    }

    controllerScope.launch {
      globalUiStateHolder.bottomPanel.bottomPanelHeight
        .onEach { onInsetsChanged() }
        .collect()
    }

    onInsetsChanged()
    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  override fun onInsetsChanged() {
    // no-op
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

  private fun onIgnoreDarkNightModeClick(item: ToolbarMenuCheckableOverflowItem) {
    toolbarState.findCheckableOverflowItem(ACTION_IGNORE_DARK_NIGHT_MODE)
      ?.updateChecked(ChanSettings.ignoreDarkNightMode.toggle())
  }

  private fun reload(itemIndex: Int, postCellDataWidthNoPaddings: Int) {
    val root = view.findViewById<LinearLayout>(R.id.root)

    val adapter = Adapter(postCellDataWidthNoPaddings)
    pager.adapter = adapter
    pager.setCurrentItem(itemIndex, false)
    pager.changeEdgeEffect(themeEngine.chanTheme)
    pager.setOnPageChangeListener(object : ViewPager.OnPageChangeListener {
      override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        // no-op
      }

      override fun onPageSelected(position: Int) {
        updateColors(adapter, position, root)
      }

      override fun onPageScrollStateChanged(state: Int) {
        // no-op
      }
    })

    view.postDelayed({ updateColors(adapter, pager.currentItem, root) }, UPDATE_COLORS_DELAY_MS)
  }

  private fun resetTheme(item: ToolbarMenuOverflowItem) {
    val isDarkTheme = when (item.id) {
      ACTION_RESET_DARK_THEME -> true
      ACTION_RESET_LIGHT_THEME -> false
      else -> throw IllegalStateException("Unknown action: ${item.id}")
    }

    if (!themeEngine.resetTheme(isDarkTheme)) {
      showToastLong(context.getString(R.string.theme_settings_controller_failed_to_reset_theme))
      return
    }

    reload(
      itemIndex = pager.currentItem,
      postCellDataWidthNoPaddings = pager.width
    )
  }

  private fun exportThemeToClipboard(item: ToolbarMenuOverflowItem) {
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

  private fun exportThemeToFile(item: ToolbarMenuOverflowItem) {
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

  private fun importTheme(item: ToolbarMenuOverflowItem) {
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

    controllerScope.launch {
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

  private fun importThemeFromClipboard(item: ToolbarMenuOverflowItem) {
    val isDarkTheme = when (item.id) {
      ACTION_IMPORT_DARK_THEME_FROM_CLIPBOARD -> true
      ACTION_IMPORT_LIGHT_THEME_FROM_CLIPBOARD -> false
      else -> throw IllegalStateException("Unknown action: ${item.id}")
    }

    val clipboardContent = AndroidUtils.clipboardContent
    if (clipboardContent.isNullOrEmpty()) {
      val message = context.getString(
        R.string.theme_settings_controller_failed_to_import_theme,
        "Clipboard is empty"
      )

      showToastLong(message)
      return
    }

    controllerScope.launch {
      handleParseThemeResult(themeEngine.tryParseAndApplyTheme(clipboardContent, isDarkTheme))
    }
  }

  private fun onFileToImportSelected(uri: Uri, isDarkTheme: Boolean) {
    val file = fileManager.fromUri(uri)
    if (file == null) {
      showToastLong(context.getString(R.string.theme_settings_controller_failed_to_open_theme_file))
      return
    }

    controllerScope.launch {
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
        reload(
          itemIndex = pager.currentItem,
          postCellDataWidthNoPaddings = pager.width
        )
      }
    }.exhaustive
  }

  private fun showToastLong(message: String) {
    showToast(message, Toast.LENGTH_LONG)
  }

  private fun updateColors(adapter: Adapter, position: Int, root: LinearLayout) {
    val theme = adapter.themeMap[position]
      ?: return

    val backgroundColor = if (ThemeEngine.isDarkColor(theme.toolbarBackgroundComposeColor)) {
      Color.LTGRAY
    } else {
      Color.DKGRAY
    }

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

      return runBlocking { createSimpleThreadViewInternal(position, theme, postCellDataWidthNoPaddings) }
    }

    private suspend fun createSimpleThreadViewInternal(
      position: Int,
      chanTheme: ChanTheme,
      postCellDataWidthNoPaddings: Int
    ): CoordinatorLayout {
      val kurobaToolbarState = KurobaToolbarState(
        controllerKey = ControllerKey("${controllerKey.key}_${chanTheme.name}"),
        globalUiStateHolder = globalUiStateHolder
      )
      kurobaToolbarState.overrideChanTheme(chanTheme)

      kurobaToolbarState.enterDefaultMode(
        leftItem = BackArrowMenuItem(
          onClick = {
            // no-op
          }
        ),
        middleContent = ToolbarMiddleContent.Title(
          title = ToolbarText.String(chanTheme.name)
        ),
        menuBuilder = {
          withOverflowMenu { addItems(chanTheme) }
        }
      )

      return themeControllerHelper.createSimpleThreadView(
        context = context,
        position = position,
        theme = chanTheme,
        kurobaToolbarState = kurobaToolbarState,
        navigationController = requireToolbarNavController(),
        options = ThemeControllerHelper.Options(
          showMoreThemesButton = true,
          refreshThemesControllerFunc = {
            view.postDelayed(
              {
                val root = view.findViewById<LinearLayout>(R.id.root)
                val prevPage = pager.currentItem

                val adapter = Adapter(postCellDataWidthNoPaddings)
                pager.adapter = adapter
                pager.setCurrentItem(prevPage)
                updateColors(adapter, prevPage, root)
              },
              UPDATE_COLORS_DELAY_MS
            )
          }
        ),
        postCellDataWidthNoPaddings = postCellDataWidthNoPaddings
      )
    }

    private fun ToolbarOverflowMenuBuilder.addItems(theme: ChanTheme) {
      if (theme.isDarkTheme) {
        withOverflowMenuItem(
          id = ACTION_IMPORT_DARK_THEME,
          stringId = R.string.action_import_dark_theme,
          onClick = { item -> importTheme(item) }
        )
        withOverflowMenuItem(
          id = ACTION_IMPORT_DARK_THEME_FROM_CLIPBOARD,
          stringId = R.string.action_import_dark_theme_from_clipboard,
          onClick = { item -> importThemeFromClipboard(item) }
        )
        withOverflowMenuItem(
          id = ACTION_EXPORT_DARK_THEME_TO_FILE,
          stringId = R.string.action_export_dark_theme_to_file,
          onClick = { item -> exportThemeToFile(item) }
        )
        withOverflowMenuItem(
          id = ACTION_EXPORT_DARK_THEME_TO_CLIPBOARD,
          stringId = R.string.action_export_dark_theme_to_clipboard,
          onClick = { item -> exportThemeToClipboard(item) }
        )
        withOverflowMenuItem(
          id = ACTION_RESET_DARK_THEME,
          stringId = R.string.action_reset_dark_theme,
          onClick = { item -> resetTheme(item) }
        )
      } else {
        withOverflowMenuItem(
          id = ACTION_IMPORT_LIGHT_THEME,
          stringId = R.string.action_import_light_theme,
          onClick = { item -> importTheme(item) }
        )
        withOverflowMenuItem(
          id = ACTION_IMPORT_LIGHT_THEME_FROM_CLIPBOARD,
          stringId = R.string.action_import_light_theme_from_clipboard,
          onClick = { item -> importThemeFromClipboard(item) }
        )
        withOverflowMenuItem(
          id = ACTION_EXPORT_LIGHT_THEME_TO_FILE,
          stringId = R.string.action_export_light_theme_to_file,
          onClick = { item -> exportThemeToFile(item) }
        )
        withOverflowMenuItem(
          id = ACTION_EXPORT_LIGHT_THEME_TO_CLIPBOARD,
          stringId = R.string.action_export_light_theme_to_clipboard,
          onClick = { item -> exportThemeToClipboard(item) }
        )
        withOverflowMenuItem(
          id = ACTION_RESET_LIGHT_THEME,
          stringId = R.string.action_reset_light_theme,
          onClick = { item -> resetTheme(item) }
        )
      }
    }

    override fun getCount(): Int {
      return 2
    }
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
