package com.github.k1rakishou.chan.features.filters

import android.content.Context
import android.text.Html
import android.text.SpannableStringBuilder
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.DialogFactory.Builder.Companion.newBuilder
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.consumeClicks
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCardView
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCheckbox
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCustomTextField
import com.github.k1rakishou.chan.ui.compose.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.theme.DropdownArrowPainter
import com.github.k1rakishou.chan.ui.view.ColorPickerView
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.SpannableHelper
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import com.github.k1rakishou.model.data.filter.FilterAction
import com.github.k1rakishou.model.data.filter.FilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject


class CreateOrUpdateFilterController(
  context: Context,
  previousChanFilterMutable: ChanFilterMutable?,
  private val activeBoardsCountForAllSites: Int
) : BaseFloatingComposeController(context) {

  @Inject
  lateinit var filterEngine: FilterEngine
  @Inject
  lateinit var chanFilterManager: ChanFilterManager
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var dialogFactory: DialogFactory

  private val chanFilterMutableState = ChanFilterMutableState.fromChanFilterMutable(previousChanFilterMutable)

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    Box(
      modifier = Modifier
        .widthIn(max = 600.dp)
        .wrapContentHeight()
        .align(Alignment.Center)
    ) {
      KurobaComposeCardView {
        val focusManager = LocalFocusManager.current

        Column(modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .padding(all = 6.dp)
          .verticalScroll(rememberScrollState())
          .consumeClicks()
        ) {
          BuildCreateOrUpdateFilterLayout(
            onHelpClicked = { showFiltersHelpDialog() },
            onCancelClicked = {
              focusManager.clearFocus(force = true)
              pop()
            },
            onSaveClicked = { onSaveFilterClicked(focusManager = focusManager) },
            onChangeFilterTypesClicked = { showFilterTypeSelectionController() },
            onChangeFilterBoardsClicked = { showFilterBoardSelectorController() },
            onChangeFilterActionClicked = { showAvailableFilterActions() },
            onChangeFilterColorClicked = { showColorPicker() }
          )
        }
      }
    }
  }

  @Composable
  private fun ColumnScope.BuildCreateOrUpdateFilterLayout(
    onHelpClicked: () -> Unit,
    onCancelClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    onChangeFilterTypesClicked: () -> Unit,
    onChangeFilterBoardsClicked: () -> Unit,
    onChangeFilterActionClicked: () -> Unit,
    onChangeFilterColorClicked: () -> Unit,
  ) {
    BuildHeader(onHelpClicked = onHelpClicked)

    BuildFilterPatternSection()

    BuildFilterSettingsSection(
      onChangeFilterTypesClicked = onChangeFilterTypesClicked,
      onChangeFilterBoardsClicked = onChangeFilterBoardsClicked,
      onChangeFilterActionClicked = onChangeFilterActionClicked,
      onChangeFilterColorClicked = onChangeFilterColorClicked
    )

    BuildFooter(
      onCancelClicked = onCancelClicked,
      onSaveClicked = onSaveClicked
    )
  }

  @Composable
  private fun ColumnScope.BuildHeader(onHelpClicked: () -> Unit) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
    ) {
      var filterEnabled by remember { chanFilterMutableState.enabled }

      KurobaComposeCheckbox(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .weight(1f),
        text = stringResource(id = R.string.filter_enabled),
        currentlyChecked = filterEnabled,
        onCheckChanged = { checked -> filterEnabled = checked }
      )

      KurobaComposeIcon(
        modifier = Modifier
          .align(Alignment.CenterVertically)
          .kurobaClickable(onClick = { onHelpClicked() }),
        drawableId = R.drawable.ic_help_outline_white_24dp
      )
    }
  }

  @Composable
  private fun ColumnScope.BuildFilterSettingsSection(
    onChangeFilterTypesClicked: () -> Unit,
    onChangeFilterBoardsClicked: () -> Unit,
    onChangeFilterActionClicked: () -> Unit,
    onChangeFilterColorClicked: () -> Unit,
  ) {
    val chanTheme = LocalChanTheme.current

    val type by remember { chanFilterMutableState.type }
    val boards by remember { chanFilterMutableState.boards }
    val allBoards by remember { chanFilterMutableState.allBoards }
    val action by remember { chanFilterMutableState.action }
    var color by remember { chanFilterMutableState.color }
    var applyToReplies by remember { chanFilterMutableState.applyToReplies }
    var onlyOnOP by remember { chanFilterMutableState.onlyOnOP }
    var applyToSaved by remember { chanFilterMutableState.applyToSaved }
    var applyToEmptyComments by remember { chanFilterMutableState.applyToEmptyComments }
    var filterWatchNotify by remember { chanFilterMutableState.filterWatchNotify }
    val arrowDropDownDrawable = remember { getTextDrawableContent() }

    if (action == FilterAction.WATCH.id) {
      applyToReplies = false
      applyToSaved = false
      applyToEmptyComments = false
      onlyOnOP = true
    }

    if (action != FilterAction.COLOR.id) {
      color = defaultFilterHighlightColor
    }

    if (applyToEmptyComments) {
      chanFilterMutableState.pattern.value = null
    }

    val filterTypes = remember(key1 = type) {
      return@remember FilterType.forFlags(type)
        .joinToString(
          separator = ", ",
          transform = { filterType -> FilterType.filterTypeName(filterType) }
        )
    }

    val filterTypeGroupText = stringResource(id = R.string.filter_filter_type_group)
    val boardsGroupText = stringResource(id = R.string.filter_boards_group)
    val actionGroupText = stringResource(id = R.string.filter_action_group)
    val highlightColorGroupText = stringResource(id = R.string.filter_highlight_color_group)
    val allActiveBoardsText = stringResource(id = R.string.filter_all_current_boards, activeBoardsCountForAllSites)
    val boardsCountText = stringResource(id = R.string.filter_boards_count, boards.size)

    val filterTypeString = remember(key1 = chanTheme.textColorSecondaryCompose, key2 = filterTypes) {
      buildAnnotatedString {
        append(AnnotatedString(filterTypeGroupText, SpanStyle(color = chanTheme.textColorSecondaryCompose)))
        append(" ")
        append(filterTypes)
        append(" ")

        appendInlineContent(dropdownArrowDrawableKey)
      }
    }

    val filterBoards = remember(key1 = boards, key2 = allBoards) {
      if (allBoards) {
        allActiveBoardsText
      } else {
        boardsCountText
      }
    }

    val boardsString = remember(key1 = chanTheme.textColorSecondaryCompose, key2 = filterBoards) {
      buildAnnotatedString {
        append(AnnotatedString(boardsGroupText, SpanStyle(color = chanTheme.textColorSecondaryCompose)))
        append(" ")
        append(filterBoards)
        append(" ")

        appendInlineContent(dropdownArrowDrawableKey)
      }
    }

    val actionText = remember(key1 = action) {
      FilterAction.filterActionName(FilterAction.forId(action))
    }

    val actionString = remember(key1 = chanTheme.textColorSecondaryCompose, key2 = actionText) {
      buildAnnotatedString {
        append(AnnotatedString(actionGroupText, SpanStyle(color = chanTheme.textColorSecondaryCompose)))
        append(" ")
        append(actionText)
        append(" ")

        appendInlineContent(dropdownArrowDrawableKey)
      }
    }

    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .kurobaClickable(
          bounded = true,
          onClick = { onChangeFilterTypesClicked() }
        )
        .padding(vertical = 8.dp),
      text = filterTypeString,
      inlineContent = arrowDropDownDrawable
    )

    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .kurobaClickable(
          bounded = true,
          onClick = { onChangeFilterBoardsClicked() }
        )
        .padding(vertical = 8.dp),
      text = boardsString,
      inlineContent = arrowDropDownDrawable
    )

    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .kurobaClickable(
          bounded = true,
          onClick = { onChangeFilterActionClicked() }
        )
        .padding(vertical = 8.dp),
      text = actionString,
      inlineContent = arrowDropDownDrawable
    )

    if (action == FilterAction.COLOR.id) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .kurobaClickable(bounded = true, onClick = { onChangeFilterColorClicked() })
      ) {
        val highlightColorString = remember(key1 = chanTheme.textColorSecondaryCompose) {
          AnnotatedString(highlightColorGroupText, SpanStyle(color = chanTheme.textColorSecondaryCompose))
        }

        KurobaComposeText(
          modifier = Modifier
            .wrapContentWidth()
            .wrapContentHeight()
            .padding(vertical = 8.dp),
          text = highlightColorString,
        )

        val bgColor = remember(key1 = color) { Color(color) }

        Spacer(
          modifier = Modifier
            .width(8.dp)
        )

        Spacer(
          modifier = Modifier
            .size(20.dp)
            .background(bgColor)
            .align(Alignment.CenterVertically)
        )

        Spacer(modifier = Modifier.width(8.dp))
      }

    }

    if (action == FilterAction.HIDE.id || action == FilterAction.REMOVE.id) {
      KurobaComposeCheckbox(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        text = stringResource(id = R.string.filter_apply_to_replies),
        currentlyChecked = applyToReplies,
        onCheckChanged = { checked -> applyToReplies = checked }
      )
    }

    KurobaComposeCheckbox(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      text = stringResource(id = R.string.filter_only_on_op),
      enabled = action != FilterAction.WATCH.id,
      currentlyChecked = onlyOnOP,
      onCheckChanged = { checked -> onlyOnOP = checked }
    )

    if (action == FilterAction.WATCH.id) {
      KurobaComposeCheckbox(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        text = stringResource(id = R.string.filter_watcher_notify),
        enabled = true,
        currentlyChecked = filterWatchNotify,
        onCheckChanged = { checked -> filterWatchNotify = checked }
      )
    }

    if (action != FilterAction.WATCH.id) {
      KurobaComposeCheckbox(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        text = stringResource(id = R.string.filter_apply_to_own_posts),
        currentlyChecked = applyToSaved,
        onCheckChanged = { checked -> applyToSaved = checked }
      )

      KurobaComposeCheckbox(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        text = stringResource(id = R.string.filter_apply_to_post_with_empty_comment),
        currentlyChecked = applyToEmptyComments,
        onCheckChanged = { checked -> applyToEmptyComments = checked }
      )
    }
  }

  @Composable
  private fun ColumnScope.BuildFilterPatternSection() {
    val chanTheme = LocalChanTheme.current
    var pattern by remember { chanFilterMutableState.pattern }
    var testText by remember { chanFilterMutableState.testPattern }
    var note by remember { chanFilterMutableState.note }
    val applyToEmptyComments by remember { chanFilterMutableState.applyToEmptyComments }
    val filterValidationResult by remember { chanFilterMutableState.filterValidationResult }

    val keyboardOptions = remember {
      KeyboardOptions(
        autoCorrect = false,
        imeAction = ImeAction.Next,
        keyboardType = KeyboardType.Password
      )
    }

    val regexStatusBgColor = when (filterValidationResult) {
      is FilterValidationResult.Error -> regexStatusErrorColor
      is FilterValidationResult.Success -> regexStatusOkColor
      FilterValidationResult.Undefined -> regexStatusUnspecifiedColor
    }

    val regexStatusText = when (val validationResult = filterValidationResult) {
      is FilterValidationResult.Error -> {
        validationResult.errorMessage
      }
      is FilterValidationResult.Success -> {
        stringResource(id = R.string.filter_everything_is_ok, validationResult.mode.name)
      }
      FilterValidationResult.Undefined -> ""
    }

    Text(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .background(regexStatusBgColor)
        .padding(all = 2.dp),
      color = Color.White,
      text = regexStatusText
    )

    Spacer(modifier = Modifier.height(8.dp))

    KurobaComposeCustomTextField(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      enabled = !applyToEmptyComments,
      fontSize = 18.sp,
      keyboardOptions = keyboardOptions,
      value = pattern ?: "",
      labelText = stringResource(id = R.string.filter_enter_pattern),
      textColor = chanTheme.textColorPrimaryCompose,
      parentBackgroundColor = chanTheme.backColorCompose,
      onValueChange = { text -> pattern = text }
    )

    Spacer(modifier = Modifier.height(8.dp))

    val matchResultState by produceState<FilterMatchResult>(
      initialValue = FilterMatchResult.Undefined,
      key1 = testText,
      key2 = pattern,
      producer = {
        this.value = CreateOrUpdateFilterControllerHelper.checkFilterMatchesTestText(
          filterEngine = filterEngine,
          chanFilterMutable = chanFilterMutableState.asChanFilterMutable(),
          text = testText,
          pattern = pattern
        )
      }
    )

    val bgColor = remember(key1 = matchResultState) {
      when (matchResultState) {
        FilterMatchResult.Undefined -> regexOrTestTextEmptyColor
        FilterMatchResult.RegexEmpty -> regexOrTestTextEmptyColor
        FilterMatchResult.TestTextEmpty -> regexOrTestTextEmptyColor
        FilterMatchResult.Matches -> regexMatchesColor
        FilterMatchResult.DoNotMatch -> regexDoesNotMatchColor
      }
    }

    val textId = remember(key1 = matchResultState) {
      when (matchResultState) {
        FilterMatchResult.Undefined -> R.string.filter_test_text_match_undefined
        FilterMatchResult.RegexEmpty -> R.string.filter_regex_is_empty
        FilterMatchResult.TestTextEmpty -> R.string.filter_test_text_is_empty
        FilterMatchResult.Matches -> R.string.filter_regex_matches_test_text
        FilterMatchResult.DoNotMatch -> R.string.filter_regex_does_not_match_test_text
      }
    }

    Text(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .background(bgColor)
        .padding(all = 2.dp),
      color = Color.White,
      text = stringResource(id = textId)
    )

    Spacer(modifier = Modifier.height(8.dp))

    KurobaComposeCustomTextField(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      fontSize = 18.sp,
      keyboardOptions = keyboardOptions,
      value = testText,
      labelText = stringResource(id = R.string.filter_test_pattern),
      textColor = chanTheme.textColorPrimaryCompose,
      parentBackgroundColor = chanTheme.backColorCompose,
      onValueChange = { text -> testText = text }
    )

    Spacer(modifier = Modifier.height(8.dp))

    KurobaComposeCustomTextField(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      fontSize = 18.sp,
      keyboardOptions = keyboardOptions,
      value = note ?: "",
      labelText = stringResource(id = R.string.filter_note),
      textColor = chanTheme.textColorPrimaryCompose,
      parentBackgroundColor = chanTheme.backColorCompose,
      onValueChange = { text -> note = text }
    )

  }

  @Composable
  private fun ColumnScope.BuildFooter(
    onCancelClicked: () -> Unit,
    onSaveClicked: () -> Unit
  ) {
    val enabled by chanFilterMutableState.enabled
    val type by chanFilterMutableState.type
    val pattern by chanFilterMutableState.pattern
    val allBoards by chanFilterMutableState.allBoards
    val boards by chanFilterMutableState.boards
    val action by chanFilterMutableState.action
    val color by chanFilterMutableState.color
    val note by chanFilterMutableState.note
    val applyToReplies by chanFilterMutableState.applyToReplies
    val onlyOnOP by chanFilterMutableState.onlyOnOP
    val applyToSaved by chanFilterMutableState.applyToSaved
    val applyToEmptyComments by chanFilterMutableState.applyToEmptyComments

    val chanFilterMutable = remember(enabled, type, pattern, allBoards, boards, action, color,
      note, applyToReplies, onlyOnOP, applyToSaved, applyToEmptyComments
    ) {
      return@remember chanFilterMutableState.asChanFilterMutable()
    }

    val filterValidationResult by produceState<FilterValidationResult>(
      initialValue = FilterValidationResult.Undefined,
      key1 = chanFilterMutable,
      producer = {
        this.value = withContext(Dispatchers.Default) {
          return@withContext CreateOrUpdateFilterControllerHelper.validateFilter(
            filterEngine = filterEngine,
            chanFilterManager = chanFilterManager,
            archivesManager = archivesManager,
            chanFilterMutable = chanFilterMutable
          )
        }
      }
    )

    chanFilterMutableState.filterValidationResult.value = filterValidationResult

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
    ) {
      Spacer(modifier = Modifier.weight(1f))

      KurobaComposeTextBarButton(
        onClick = { onCancelClicked() },
        text = stringResource(id = R.string.cancel)
      )

      Spacer(modifier = Modifier.width(24.dp))

      KurobaComposeTextBarButton(
        enabled = filterValidationResult is FilterValidationResult.Success,
        onClick = { onSaveClicked() },
        text = stringResource(id = R.string.save)
      )
    }
  }

  private fun showAvailableFilterActions() {
    val items = FilterAction.values().map { filterAction ->
      return@map CheckableFloatingListMenuItem(
        key = filterAction.id,
        name = FilterAction.filterActionName(filterAction),
        isCurrentlySelected = chanFilterMutableState.action.value == filterAction.id
      )
    }

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = items,
      itemClickListener = { clickedMenuItem ->
        chanFilterMutableState.action.value = clickedMenuItem.key as Int
      }
    )

    presentController(floatingListMenuController)
  }

  private fun showFilterBoardSelectorController() {
    val currentlySelectedBoards = if (chanFilterMutableState.allBoards.value) {
      null
    } else {
      chanFilterMutableState.boards.value
    }

    val filterBoardSelectorController = FilterBoardSelectorController(
      context = context,
      currentlySelectedBoards = currentlySelectedBoards,
      onBoardsSelected = { selectedBoards ->
        when (selectedBoards) {
          FilterBoardSelectorController.SelectedBoards.AllBoards -> {
            chanFilterMutableState.allBoards.value = true
            chanFilterMutableState.boards.value = mutableSetOf()
          }
          is FilterBoardSelectorController.SelectedBoards.Boards -> {
            chanFilterMutableState.allBoards.value = false
            chanFilterMutableState.boards.value = selectedBoards.boardDescriptors.toMutableSet()
          }
        }
      }
    )

    presentController(filterBoardSelectorController)
  }

  private fun showColorPicker() {
    val colorPickerView = ColorPickerView(context)
    colorPickerView.color = chanFilterMutableState.color.value

    newBuilder(context, dialogFactory)
      .withTitle(R.string.filter_color_pick)
      .withCustomView(colorPickerView)
      .withNegativeButtonTextId(R.string.cancel)
      .withPositiveButtonTextId(R.string.ok)
      .withOnPositiveButtonClickListener {
        chanFilterMutableState.color.value = colorPickerView.color
      }
      .create()
  }

  private fun onSaveFilterClicked(focusManager: FocusManager) {
    val chanFilterMutable = chanFilterMutableState.asChanFilterMutable()

    if (chanFilterMutable.allBoards() && chanFilterMutable.isWatchFilter()) {
      dialogFactory.createSimpleInformationDialog(
        context,
        context.getString(R.string.filter_watch_check_all_warning_title),
        context.getString(R.string.filter_watch_check_all_warning_description)
      )
    }

    filterEngine.createOrUpdateFilter(chanFilterMutable) {
      BackgroundUtils.ensureMainThread()

      if (chanFilterMutable.enabled && chanFilterMutable.isWatchFilter()) {
        if (!ChanSettings.filterWatchEnabled.get()) {
          showToast(R.string.filter_watcher_disabled_message, Toast.LENGTH_LONG)
        }
      }

      focusManager.clearFocus(force = true)
      pop()
    }
  }

  private fun showFilterTypeSelectionController() {
    val filterTypeSelectionController = FilterTypeSelectionController(
      context = context,
      chanFilterMutable = chanFilterMutableState.asChanFilterMutable(),
      onSelected = { newType -> chanFilterMutableState.type.value = newType }
    )

    presentController(filterTypeSelectionController)
  }

  private fun showFiltersHelpDialog() {
    val message = SpannableHelper.convertHtmlStringTagsIntoSpans(
      message = SpannableStringBuilder.valueOf(Html.fromHtml(getString(R.string.filter_help))),
      chanTheme = themeEngine.chanTheme
    )

    DialogFactory.Builder.newBuilder(context, dialogFactory)
      .withTitle(R.string.filter_help_title)
      .withDescription(message)
      .create()
  }

  private fun getTextDrawableContent(): Map<String, InlineTextContent> {
    val placeholder = Placeholder(
      width = 12.sp,
      height = 12.sp,
      placeholderVerticalAlign = PlaceholderVerticalAlign.Center
    )

    val children: @Composable (String) -> Unit = {
      val chanTheme = LocalChanTheme.current
      val color = remember(key1 = chanTheme.backColorCompose) {
        ThemeEngine.resolveDrawableTintColorCompose(ThemeEngine.isDarkColor(chanTheme.backColorCompose))
      }

      val dropdownArrowPainter = remember {
        return@remember DropdownArrowPainter(
          color = color,
          down = true
        )
      }

      Image(
        modifier = Modifier.fillMaxSize(),
        painter = dropdownArrowPainter,
        contentDescription = null
      )
    }

    return mapOf(
      dropdownArrowDrawableKey to InlineTextContent(placeholder = placeholder, children = children)
    )
  }

  private data class ChanFilterMutableState(
    val databaseId: Long?,
    val enabled: MutableState<Boolean> = mutableStateOf(true),
    val type: MutableState<Int> = mutableStateOf(FilterType.SUBJECT.flag or FilterType.COMMENT.flag),
    val pattern: MutableState<String?> = mutableStateOf(null),
    val allBoards: MutableState<Boolean> = mutableStateOf(false),
    val boards: MutableState<MutableSet<BoardDescriptor>> = mutableStateOf(mutableSetOf()),
    val action: MutableState<Int> = mutableStateOf(0),
    val color: MutableState<Int> = mutableStateOf(defaultFilterHighlightColor),
    val note: MutableState<String?> = mutableStateOf(null),
    val applyToReplies: MutableState<Boolean> = mutableStateOf(false),
    val onlyOnOP: MutableState<Boolean> = mutableStateOf(false),
    val applyToSaved: MutableState<Boolean> = mutableStateOf(false),
    val applyToEmptyComments: MutableState<Boolean> = mutableStateOf(false),
    val filterWatchNotify: MutableState<Boolean> = mutableStateOf(false),

    val testPattern: MutableState<String> = mutableStateOf(""),
    val filterValidationResult: MutableState<FilterValidationResult> = mutableStateOf(FilterValidationResult.Undefined)
  ) {

    fun asChanFilterMutable(): ChanFilterMutable {
      return ChanFilterMutable(
        databaseId = databaseId ?: 0L,
        enabled = enabled.value,
        type = type.value,
        pattern = pattern.value,
        allBoardsSelected = allBoards.value,
        boards = boards.value,
        action = action.value,
        color = color.value,
        note = note.value,
        applyToReplies = applyToReplies.value,
        onlyOnOP = onlyOnOP.value,
        applyToSaved = applyToSaved.value,
        applyToEmptyComments = applyToEmptyComments.value,
        filterWatchNotify = filterWatchNotify.value,
      )
    }

    companion object {
      fun fromChanFilterMutable(chanFilterMutable: ChanFilterMutable?): ChanFilterMutableState {
        val chanFilterMutableState = ChanFilterMutableState(databaseId = chanFilterMutable?.databaseId)

        if (chanFilterMutable != null) {
          chanFilterMutableState.enabled.value = chanFilterMutable.enabled
          chanFilterMutableState.type.value = chanFilterMutable.type
          chanFilterMutableState.pattern.value = chanFilterMutable.pattern
          chanFilterMutableState.boards.value = chanFilterMutable.boards
          chanFilterMutableState.allBoards.value = chanFilterMutable.allBoards()
          chanFilterMutableState.action.value = chanFilterMutable.action
          chanFilterMutableState.note.value = chanFilterMutable.note
          chanFilterMutableState.applyToReplies.value = chanFilterMutable.applyToReplies
          chanFilterMutableState.onlyOnOP.value = chanFilterMutable.onlyOnOP
          chanFilterMutableState.applyToSaved.value = chanFilterMutable.applyToSaved
          chanFilterMutableState.applyToEmptyComments.value = chanFilterMutable.applyToEmptyComments
          chanFilterMutableState.filterWatchNotify.value = chanFilterMutable.filterWatchNotify

          chanFilterMutableState.color.value = if (chanFilterMutable.color != 0) {
            chanFilterMutable.color
          } else {
            defaultFilterHighlightColor
          }

          chanFilterMutableState.testPattern.value = ""
          chanFilterMutableState.filterValidationResult.value = FilterValidationResult.Undefined
        }

        return chanFilterMutableState
      }
    }

  }

  companion object {
    private const val dropdownArrowDrawableKey = "[DropdownArrowDrawable]"

    private val regexMatchesColor = Color(0xFF1cb01c)
    private val regexDoesNotMatchColor = Color(0xFFad1f1f)
    private val regexOrTestTextEmptyColor = Color(0xFF525050)

    private val regexStatusErrorColor = regexDoesNotMatchColor
    private val regexStatusUnspecifiedColor = regexOrTestTextEmptyColor
    private val regexStatusOkColor = regexMatchesColor

    private val defaultFilterHighlightColor = Color.Red.toArgb()
  }

}