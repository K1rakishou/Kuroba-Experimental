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
package com.github.k1rakishou.chan.ui.controller.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.text.Spannable
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EdgeEffect
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.text.getSpans
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.MockReplyManager
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.ui.adapter.PostAdapter
import com.github.k1rakishou.chan.ui.adapter.PostAdapter.PostAdapterCallback
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.ThreadStatusCell
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem
import com.github.k1rakishou.chan.ui.view.ViewPagerAdapter
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.post_thumbnail.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.ViewUtils.changeEdgeEffect
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.core_spannable.ColorizableBackgroundColorSpan
import com.github.k1rakishou.core_spannable.ColorizableForegroundColorSpan
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_spannable.ThemeEditorPostLinkable
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.getComplementaryColor
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.isDarkColor
import com.github.k1rakishou.core_themes.ThemeParser
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.callback.FileChooserCallback
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import com.github.k1rakishou.model.data.board.pages.BoardPage
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageBuilder
import com.github.k1rakishou.model.data.post.PostIndexed
import com.github.k1rakishou.persist_state.PersistableChanState
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ThemeSettingsController(context: Context) : Controller(context),
  ToolbarMenuItem.ToobarThreedotMenuCallback {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postFilterManager: PostFilterManager
  @Inject
  lateinit var mockReplyManager: MockReplyManager
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

  private val dummyBoardDescriptor =
    BoardDescriptor.create("test_site", "test_board")
  private val dummyThreadDescriptor =
    ChanDescriptor.ThreadDescriptor.create("test_site", "test_board", 1234567890L)

  private val dummyPostCallback: PostCellCallback = object : PostCellCallback {
    override fun onPostBind(postDescriptor: PostDescriptor) {}
    override fun onPostUnbind(postDescriptor: PostDescriptor, isActuallyRecycling: Boolean) {}
    override fun onPostClicked(postDescriptor: PostDescriptor) {}
    override fun onGoToPostButtonClicked(post: ChanPost, postViewMode: PostCellData.PostViewMode) {}
    override fun onThumbnailClicked(postImage: ChanPostImage, thumbnail: ThumbnailView) {}
    override fun onThumbnailLongClicked(postImage: ChanPostImage, thumbnail: ThumbnailView) {}
    override fun onShowPostReplies(post: ChanPost) {}
    override fun onPreviewThreadPostsClicked(post: ChanPost) {}
    override fun onPopulatePostOptions(post: ChanPost, menu: MutableList<FloatingListMenuItem>) {}
    override fun onPostOptionClicked(post: ChanPost, id: Any, inPopup: Boolean) {}
    override fun onPostLinkableClicked(post: ChanPost, linkable: PostLinkable) {}
    override fun onPostLinkableLongClicked(post: ChanPost, linkable: PostLinkable, inPopup: Boolean) {}
    override fun onPostNoClicked(post: ChanPost) {}
    override fun onPostSelectionQuoted(postDescriptor: PostDescriptor, selection: CharSequence) {}
    override fun onPostSelectionFilter(postDescriptor: PostDescriptor, selection: CharSequence) {}
    override fun showPostOptions(post: ChanPost, inPopup: Boolean, items: List<FloatingListMenuItem>) {}
    override fun onUnhidePostClick(post: ChanPost) {}
    override fun currentSpanCount(): Int = 1

    override val currentChanDescriptor: ChanDescriptor?
      get() = dummyThreadDescriptor

    override fun getPage(originalPostDescriptor: PostDescriptor): BoardPage? {
      return null
    }

    override fun hasAlreadySeenPost(postDescriptor: PostDescriptor): Boolean {
      return false
    }
  }

  private val parserCallback: PostParser.Callback = object : PostParser.Callback {
    override fun isSaved(postNo: Long, postSubNo: Long): Boolean {
      return false
    }

    override fun isInternal(postNo: Long): Boolean {
      return true
    }

    override fun isValidBoard(boardDescriptor: BoardDescriptor): Boolean {
      return true
    }

    override fun isParsingCatalogPosts(): Boolean {
      return false
    }
  }

  private lateinit var pager: ViewPager
  private lateinit var currentThemeIndicator: TextView
  private lateinit var applyThemeFab: ColorizableFloatingActionButton
  private var currentItemIndex = 0

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
    reload()

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

    val adapter = Adapter()
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

  private fun exportTheme(item: ToolbarMenuSubItem) {
    val isDarkTheme = when (item.id) {
      ACTION_EXPORT_DARK_THEME -> true
      ACTION_EXPORT_LIGHT_THEME -> false
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
      when (val result = themeEngine.exportTheme(file, isDarkTheme)) {
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

  private fun FloatingActionButton.updateColors(theme: ChanTheme) {
    backgroundTintList = ColorStateList.valueOf(theme.accentColor)

    val isDarkColor = isDarkColor(theme.accentColor)
    if (isDarkColor) {
      drawable.setTint(Color.WHITE)
    } else {
      drawable.setTint(Color.BLACK)
    }
  }

  private inner class Adapter : ViewPagerAdapter() {
    val themeMap = mutableMapOf<Int, ChanTheme>()

    override fun getView(position: Int, parent: ViewGroup): View {
      val theme = when (position) {
        0 -> themeEngine.lightTheme()
        1 -> themeEngine.darkTheme()
        else -> throw IllegalStateException("Bad position: $position")
      }

      themeMap[position] = theme

      return runBlocking { createSimpleThreadViewInternal(theme) }
    }

    private suspend fun createSimpleThreadViewInternal(theme: ChanTheme): CoordinatorLayout {
      return createSimpleThreadView(theme)
    }

    override fun getCount(): Int {
      return 2
    }
  }

  private suspend fun createSimpleThreadView(
    theme: ChanTheme
  ): CoordinatorLayout {
    val parser = CommentParser(mockReplyManager)
      .addDefaultRules()

    val postParser = DefaultPostParser(parser, postFilterManager, archivesManager)
    val builder1 = ChanPostBuilder()
      .boardDescriptor(dummyBoardDescriptor)
      .id(123456789)
      .opId(123456789)
      .op(true)
      .replies(1)
      .setUnixTimestampSeconds(
        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30))
      )
      .subject("Lorem ipsum")
      .name("OP")
      .comment(
        "<span class=\"deadlink\">&gt;&gt;987654321</span><br>" + "http://example.com/<br>"
          + "Phasellus consequat semper sodales. Donec dolor lectus, aliquet nec mollis vel, rutrum vel enim.<br>"
          + "<span class=\"quote\">&gt;Nam non hendrerit justo, venenatis bibendum arcu.</span>"
      )

    val post1 = postParser.parse(builder1, parserCallback)
    post1.repliesFrom.add(234567890L)

    val pd2 = PostDescriptor.create(dummyBoardDescriptor, 234567890L, 123456789L)
    val builder2 = ChanPostBuilder()
      .boardDescriptor(dummyBoardDescriptor)
      .id(234567890)
      .opId(123456789)
      .setUnixTimestampSeconds(
        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15))
      )
      .comment(
        "<a href=\"#p123456789\" class=\"quotelink\">&gt;&gt;123456789</a><br>"
          + "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
      )
      .name("Test name")
      .tripcode("!N4EoL/Xuog")
      .postImages(
        listOf(
          ChanPostImageBuilder()
            .serverFilename("123123123123.jpg")
            .imageUrl((AppConstants.RESOURCES_ENDPOINT + "release_icon_512.png").toHttpUrl())
            .thumbnailUrl((AppConstants.RESOURCES_ENDPOINT + "release_icon_512.png").toHttpUrl())
            .filename("icon")
            .extension("png")
            .build()
        ),
        pd2
      )

    val post2 = postParser.parse(builder2, parserCallback)

    val posts: MutableList<ChanPost> = ArrayList()
    posts.add(post1)
    posts.add(post2)

    hackSpanColors(post1.subject, theme)
    hackSpanColors(post1.tripcode, theme)
    hackSpanColors(post1.postComment.comment(), theme)

    hackSpanColors(post2.subject, theme)
    hackSpanColors(post2.tripcode, theme)
    hackSpanColors(post2.postComment.comment(), theme)

    val linearLayout = LinearLayout(context)
    linearLayout.layoutParams = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.MATCH_PARENT
    )

    linearLayout.orientation = LinearLayout.VERTICAL
    linearLayout.setBackgroundColor(theme.backColor)

    val postsView = RecyclerView(context)
    postsView.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
      override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
        return EdgeEffect(view.context).apply { color = theme.accentColor }
      }
    }

    val layoutManager = LinearLayoutManager(context)
    layoutManager.orientation = RecyclerView.VERTICAL
    postsView.layoutManager = layoutManager

    val adapter = PostAdapter(
      postsView,
      object : PostAdapterCallback {
        override val currentChanDescriptor: ChanDescriptor?
          get() = dummyThreadDescriptor
      },
      dummyPostCallback,
      object : ThreadStatusCell.Callback {
        override suspend fun timeUntilLoadMoreMs(): Long {
          return 0
        }

        override val currentChanDescriptor: ChanDescriptor?
          get() = null

        override fun getPage(originalPostDescriptor: PostDescriptor): BoardPage? {
          return null
        }

        override fun isWatching(): Boolean {
          return false
        }

        override fun onListStatusClicked() {}
      }
    )

    adapter.setThread(dummyThreadDescriptor, theme, indexPosts(posts))
    adapter.setBoardPostViewMode(ChanSettings.BoardPostViewMode.LIST)
    postsView.adapter = adapter

    val bottomNavView = BottomNavigationView(context)
    bottomNavView.menu.clear()
    bottomNavView.inflateMenu(R.menu.bottom_navigation_menu)
    bottomNavView.selectedItemId = R.id.action_browse
    bottomNavView.setBackgroundColor(theme.primaryColor)

    val uncheckedColor = if (ThemeEngine.isNearToFullyBlackColor(theme.primaryColor)) {
      Color.DKGRAY
    } else {
      ThemeEngine.manipulateColor(theme.primaryColor, .7f)
    }

    bottomNavView.itemIconTintList = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
      intArrayOf(Color.WHITE, uncheckedColor)
    )

    bottomNavView.itemTextColor = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
      intArrayOf(Color.WHITE, uncheckedColor)
    )

    val toolbar = Toolbar(context)
    toolbar.setIgnoreThemeChanges()
    toolbar.setBackgroundColor(theme.primaryColor)

    val item = NavigationItem()
    item.title = theme.name
    item.hasBack = false

    item.buildMenu(context)
      .withOverflow(navigationController, this)
      .addSubItems(theme.isDarkTheme)
      .build()
      .build()

    toolbar.setNavigationItem(false, true, item, theme)

    val fab = FloatingActionButton(context)
    fab.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_create_white_24dp))
    fab.imageTintList = ColorStateList.valueOf(Color.WHITE)
    fab.updateColors(theme)
    fab.backgroundTintList = ColorStateList.valueOf(theme.accentColor)

    linearLayout.addView(
      toolbar,
      LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        getDimen(R.dimen.toolbar_height)
      )
    )
    linearLayout.addView(
      postsView,
      LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        0,
        1f
      )
    )
    linearLayout.addView(
      bottomNavView,
      LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        getDimen(R.dimen.bottom_nav_view_height)
      )
    )

    val coordinatorLayout = CoordinatorLayout(context)
    coordinatorLayout.addView(linearLayout)

    coordinatorLayout.addView(
      fab,
      CoordinatorLayout.LayoutParams(
        CoordinatorLayout.LayoutParams.WRAP_CONTENT,
        CoordinatorLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        gravity = Gravity.END or Gravity.BOTTOM
        bottomMargin = dp(16f) + getDimen(R.dimen.bottom_nav_view_height)
        marginEnd = dp(16f)
      }
    )

    return coordinatorLayout
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
          ACTION_EXPORT_DARK_THEME,
          R.string.action_export_dark_theme,
          { item -> exportTheme(item) }
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
          ACTION_EXPORT_LIGHT_THEME,
          R.string.action_export_light_theme,
          { item -> exportTheme(item) }
        )
        .withSubItem(
          ACTION_RESET_LIGHT_THEME,
          R.string.action_reset_light_theme,
          { item -> resetTheme(item) }
        )
    }
  }

  private fun indexPosts(posts: List<ChanPost>): List<PostIndexed> {
    return posts.mapIndexed { index, post -> PostIndexed(post, index) }
  }

  private fun hackSpanColors(input: CharSequence?, theme: ChanTheme) {
    if (input !is Spannable) {
      return
    }

    val spans = input.getSpans<CharacterStyle>()

    for (span in spans) {
      if (span is ColorizableBackgroundColorSpan || span is ColorizableForegroundColorSpan) {
        val start = input.getSpanStart(span)
        val end = input.getSpanEnd(span)
        val flags = input.getSpanFlags(span)

        val newColor = when (span) {
          is ColorizableForegroundColorSpan -> span.chanThemeColorId
          is ColorizableBackgroundColorSpan -> span.chanThemeColorId
          else -> throw IllegalStateException("Unknown span: ${span::class.java.simpleName}")
        }

        input.removeSpan(span)
        input.setSpan(
          ForegroundColorSpan(theme.getColorByColorId(newColor)),
          start,
          end,
          flags,
        )

        continue
      }

      if (span is PostLinkable) {
        val start = input.getSpanStart(span)
        val end = input.getSpanEnd(span)
        val flags = input.getSpanFlags(span)

        input.removeSpan(span)
        input.setSpan(
          ThemeEditorPostLinkable(themeEngine, theme, span.key, span.linkableValue, span.type),
          start,
          end,
          flags,
        )
      }
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
    private const val ACTION_EXPORT_LIGHT_THEME = 5
    private const val ACTION_EXPORT_DARK_THEME = 6
    private const val ACTION_RESET_LIGHT_THEME = 7
    private const val ACTION_RESET_DARK_THEME = 8
    private const val ACTION_IGNORE_DARK_NIGHT_MODE = 9

    private const val UPDATE_COLORS_DELAY_MS = 125L
  }

}
