package com.github.k1rakishou.chan.features.filters

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.ViewModelSelectionHelper
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.site.parser.CommentParserHelper
import com.github.k1rakishou.chan.ui.compose.reorder.move
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItem
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItemId
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.removeIfKt
import com.github.k1rakishou.common.toHashSetBy
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.filter.FilterAction
import com.github.k1rakishou.model.data.filter.FilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FiltersControllerViewModel : BaseViewModel() {

  @Inject
  lateinit var chanFilterManager: ChanFilterManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var themeEngine: ThemeEngine

  var searchQuery = mutableStateOf<String>("")

  private val _filters = mutableStateListOf<ChanFilterInfo>()
  val filters: List<ChanFilterInfo>
    get() = _filters

  private val _activeBoardsCountForAllSites = AtomicInteger(0)
  val activeBoardsCountForAllSites: Int
    get() = _activeBoardsCountForAllSites.get()

  private val _updateEnableDisableAllFiltersButtonFlow = MutableSharedFlow<Unit>(replay = 1)
  val updateEnableDisableAllFiltersButtonFlow: SharedFlow<Unit>
    get() = _updateEnableDisableAllFiltersButtonFlow.asSharedFlow()

  val viewModelSelectionHelper = ViewModelSelectionHelper<ChanFilter, MenuItemClickEvent>()

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    mainScope.launch {
      chanFilterManager.listenForFiltersChanges()
        .collect { filterEvent ->
          processFilterChanges(filterEvent)
          _updateEnableDisableAllFiltersButtonFlow.emit(Unit)
        }
    }
  }

  suspend fun awaitUntilDependenciesInitialized() {
    chanFilterManager.awaitUntilInitialized()
    boardManager.awaitUntilInitialized()
  }

  fun activeBoardsCountForAllSites(): Int {
    check(boardManager.isReady()) { "BoardManager is not initialized yet!" }

    return boardManager.activeBoardsCountForAllSites()
  }

  suspend fun enableOrDisableFilter(enable: Boolean, chanFilter: ChanFilter) {
    if (enable == chanFilter.enabled) {
      return
    }

    val updatedChanFilter = chanFilter
      .copy(enable = enable)

    suspendCoroutine<Unit> { continuation ->
      chanFilterManager.createOrUpdateFilter(
        chanFilter = updatedChanFilter,
        onFinished = { continuation.resume(Unit) }
      )
    }

    _updateEnableDisableAllFiltersButtonFlow.emit(Unit)
  }

  fun allFiltersEnabled(): Boolean = chanFilterManager.allFiltersEnabled()
  fun hasFilters(): Boolean = chanFilterManager.filtersCount() > 0

  suspend fun enableOrDisableAllFilters() {
    val allFilters = chanFilterManager.getAllFilters()
    if (allFilters.isEmpty()) {
      return
    }

    val shouldEnableAll = chanFilterManager.allFiltersEnabled().not()

    val updatedFilters = allFilters.mapNotNull { chanFilter ->
      if (chanFilter.enabled == shouldEnableAll) {
        return@mapNotNull null
      }

      return@mapNotNull chanFilter.copy(enable = shouldEnableAll)
    }

    if (updatedFilters.isEmpty()) {
      return
    }

    suspendCoroutine<Unit> { continuation ->
      chanFilterManager.createOrUpdateFilters(
        chanFilters = updatedFilters,
        onFinished = { continuation.resume(Unit) }
      )
    }

    _updateEnableDisableAllFiltersButtonFlow.emit(Unit)
  }

  suspend fun reorderFilterInMemory(fromIndex: Int, toIndex: Int) {
    val moved = suspendCoroutine<Boolean> { continuation ->
      chanFilterManager.onFilterMoved(
        fromIndex = fromIndex,
        toIndex = toIndex,
        onMoved = { moved -> continuation.resume(moved) }
      )
    }

    if (moved) {
      _filters.move(fromIdx = fromIndex, toIdx = toIndex)
    }
  }

  fun toggleSelection(clickedFilter: ChanFilter) {
    viewModelSelectionHelper.toggleSelection(clickedFilter)
  }

  suspend fun deleteFilters(filtersToDelete: List<ChanFilter>) {
    if (filtersToDelete.isEmpty()) {
      return
    }

    suspendCoroutine<Unit> { continuation ->
      chanFilterManager.deleteFilters(
        chanFilters = filtersToDelete,
        onDeleted = { continuation.resume(Unit) }
      )
    }
  }

  suspend fun persistReorderedFilters() {
    chanFilterManager.persistReorderedFilters()
  }

  private fun processFilterChanges(filterEvent: ChanFilterManager.FilterEvent) {
    when (filterEvent) {
      ChanFilterManager.FilterEvent.Initialized -> {
        // no-op
      }
      is ChanFilterManager.FilterEvent.Created -> {
        filterEvent.chanFilters.forEach { chanFilter ->
          _filters.add(createChanFilterInfo(chanFilter))
        }
      }
      is ChanFilterManager.FilterEvent.Deleted -> {
        val databaseIds = filterEvent.chanFilters
          .toHashSetBy { chanFilter -> chanFilter.getDatabaseId() }

        _filters.removeIfKt { chanFilterInfo ->
          chanFilterInfo.chanFilter.getDatabaseId() in databaseIds
        }
      }
      is ChanFilterManager.FilterEvent.Updated -> {
        filterEvent.chanFilters.forEach { chanFilter ->
          val index = _filters.indexOfFirst { chanFilterInfo ->
            chanFilterInfo.chanFilter.getDatabaseId() == chanFilter.getDatabaseId()
          }

          if (index >= 0) {
            _filters[index] = createChanFilterInfo(chanFilter)
          } else {
            _filters.add(createChanFilterInfo(chanFilter))
          }
        }
      }
    }
  }

  suspend fun reloadFilters() {
    awaitUntilDependenciesInitialized()
    _activeBoardsCountForAllSites.set(activeBoardsCountForAllSites())

    val allFilters = withContext(Dispatchers.Default) {
      return@withContext chanFilterManager.getAllFilters()
        .map { chanFilter -> createChanFilterInfo(chanFilter) }
    }

    _filters.clear()
    _filters.addAll(allFilters)

    _updateEnableDisableAllFiltersButtonFlow.emit(Unit)
  }

  private fun createChanFilterInfo(chanFilter: ChanFilter): ChanFilterInfo {
    val chanTheme = themeEngine.chanTheme
    val detectedLinks = detectLinksInNote(chanFilter.note)

    return ChanFilterInfo(
      chanFilter = chanFilter,
      filterText = extractFilterTextInfo(
        chanFilter = chanFilter,
        detectedLinksInNote = detectedLinks,
        chanTheme = chanTheme,
        activeBoardsCountForAllSites = activeBoardsCountForAllSites
      ),
      detectedLinksInNote = detectedLinks
    )
  }

  private fun detectLinksInNote(note: String?): List<TextRange> {
    if (note.isNullOrEmpty()) {
      return emptyList()
    }

    return CommentParserHelper.LINK_EXTRACTOR.extractLinks(note)
      .map { linkSpan -> TextRange(linkSpan.beginIndex, linkSpan.endIndex) }
  }

  private fun extractFilterTextInfo(
    chanFilter: ChanFilter,
    detectedLinksInNote: List<TextRange>,
    chanTheme: ChanTheme,
    activeBoardsCountForAllSites: Int?
  ): AnnotatedString {
    return buildAnnotatedString {
      append(
        AnnotatedString(getString(R.string.filter_pattern_part),
          SpanStyle(color = chanTheme.textColorSecondaryCompose))
      )
      append(" ")
      append(chanFilter.pattern ?: getString(R.string.filter_pattern_part_no_pattern))
      append("\n")

      val filterTypes = FilterType.forFlags(chanFilter.type)
        .joinToString(
          separator = ", ",
          transform = { filterType -> FilterType.filterTypeName(filterType) }
        )

      append(
        AnnotatedString(getString(R.string.filter_filter_type_part),
          SpanStyle(color = chanTheme.textColorSecondaryCompose))
      )
      append(" ")
      append(filterTypes)
      append("\n")

      append(
        AnnotatedString(getString(R.string.filter_boards_part),
          SpanStyle(color = chanTheme.textColorSecondaryCompose))
      )
      append(" ")

      val enabledOnBoards = if (chanFilter.allBoards()) {
        getString(
          R.string.filter_all_active_boards_part,
          activeBoardsCountForAllSites?.toString() ?: "???"
        )
      } else {
        getString(R.string.filter_n_boards_part, chanFilter.boards.size)
      }

      append(enabledOnBoards)
      append("\n")

      append(
        AnnotatedString(getString(R.string.filter_action_part),
          SpanStyle(color = chanTheme.textColorSecondaryCompose))
      )
      append(" ")

      val filterAction = FilterAction.forId(chanFilter.action)
      val filterActionName = FilterAction.filterActionName(filterAction)
      append(filterActionName)

      if (filterAction == FilterAction.COLOR) {
        append("  ")
        appendInlineContent(squareDrawableKey)
      }

      if (chanFilter.applyToReplies) {
        append("\n")
        append(
          AnnotatedString(getString(R.string.filter_apply_to_replies_part),
            SpanStyle(color = chanTheme.textColorSecondaryCompose))
        )
        append(" ")
        append(chanFilter.applyToReplies.toString())
      }

      if (chanFilter.onlyOnOP) {
        append("\n")
        append(
          AnnotatedString(getString(R.string.filter_only_apply_to_op_part),
            SpanStyle(color = chanTheme.textColorSecondaryCompose))
        )
        append(" ")
        append(chanFilter.onlyOnOP.toString())
      }

      if (chanFilter.applyToSaved) {
        append("\n")
        append(
          AnnotatedString(getString(R.string.filter_apply_to_own_posts_part),
            SpanStyle(color = chanTheme.textColorSecondaryCompose))
        )
        append(" ")
        append(chanFilter.applyToSaved.toString())
      }

      if (chanFilter.applyToEmptyComments) {
        append("\n")
        append(
          AnnotatedString(getString(R.string.filter_apply_to_post_with_empty_comment),
            SpanStyle(color = chanTheme.textColorSecondaryCompose))
        )
        append(" ")
        append(chanFilter.applyToEmptyComments.toString())
      }

      val filterNote = chanFilter.note
      if (filterNote.isNotNullNorBlank()) {
        append("\n")

        val filterNotAnnotated = buildAnnotatedString {
          append(filterNote)

          detectedLinksInNote.forEach { textRange ->
            addStyle(
              style = SpanStyle(color = chanTheme.postLinkColorCompose),
              start = textRange.start,
              end = textRange.end
            )

            addStringAnnotation(
              tag = LINK_TAG,
              annotation = LINK_TAG,
              start = textRange.start,
              end = textRange.end
            )
          }
        }

        append(
          AnnotatedString(getString(R.string.filter_note_part),
            SpanStyle(color = chanTheme.textColorSecondaryCompose))
        )
        append(" ")
        append(filterNotAnnotated)
      }
    }
  }

  fun getBottomPanelMenus(): List<BottomMenuPanelItem> {
    val currentlySelectedItems = viewModelSelectionHelper.getCurrentlySelectedItems()
    if (currentlySelectedItems.isEmpty()) {
      return emptyList()
    }

    val itemsList = mutableListOf<BottomMenuPanelItem>()

    itemsList += BottomMenuPanelItem(
      FilterMenuItemId(MenuItemType.Delete),
      R.drawable.ic_baseline_delete_outline_24,
      R.string.bottom_menu_item_delete,
      {
        val clickEvent = MenuItemClickEvent(
          menuItemType = MenuItemType.Delete,
          items = viewModelSelectionHelper.getCurrentlySelectedItems()
        )

        viewModelSelectionHelper.emitBottomPanelMenuItemClickEvent(clickEvent)
        viewModelSelectionHelper.unselectAll()
      }
    )

    return itemsList
  }

  data class ChanFilterInfo(
    val chanFilter: ChanFilter,
    val filterText: AnnotatedString,
    val detectedLinksInNote: List<TextRange>
  )

  class FilterMenuItemId(val menuItemType: MenuItemType) :
    BottomMenuPanelItemId {
    override fun id(): Int {
      return menuItemType.id
    }
  }

  data class MenuItemClickEvent(
    val menuItemType: MenuItemType,
    val items: List<ChanFilter>
  )

  enum class MenuItemType(val id: Int) {
    Delete(0)
  }

  companion object {
    private const val TAG = "FiltersControllerViewModel"

    const val squareDrawableKey = "[SQUARE_PAINTER]"
    const val LINK_TAG = "[LINK]"
  }

}