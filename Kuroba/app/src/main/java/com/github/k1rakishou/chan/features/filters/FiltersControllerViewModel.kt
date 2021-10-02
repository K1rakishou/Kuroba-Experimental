package com.github.k1rakishou.chan.features.filters

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.site.parser.CommentParserHelper
import com.github.k1rakishou.chan.ui.compose.reorder.move
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.removeIfKt
import com.github.k1rakishou.common.toHashSetBy
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.filter.FilterAction
import com.github.k1rakishou.model.data.filter.FilterType
import kotlinx.coroutines.Dispatchers
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

  private val _filters = mutableStateListOf<ChanFilterInfo>()
  val filters: List<ChanFilterInfo>
    get() = _filters

  private val _activeBoardsCountForAllSites = AtomicInteger(0)
  val activeBoardsCountForAllSites: Int
    get() = _activeBoardsCountForAllSites.get()

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    mainScope.launch {
      chanFilterManager.listenForFiltersChanges()
        .collect { filterEvent -> processFilterChanges(filterEvent) }
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

  suspend fun persistReorderedFilters() {
    chanFilterManager.persistReorderedFilters()
  }

  private fun processFilterChanges(filterEvent: ChanFilterManager.FilterEvent) {
    when (filterEvent) {
      ChanFilterManager.FilterEvent.Initialized -> {
        // no-op
      }
      is ChanFilterManager.FilterEvent.Created -> {
        _filters.add(createChanFilterInfo(filterEvent.chanFilter))
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

  // TODO(KurobaEx-filters): strings
  private fun extractFilterTextInfo(
    chanFilter: ChanFilter,
    detectedLinksInNote: List<TextRange>,
    chanTheme: ChanTheme,
    activeBoardsCountForAllSites: Int?
  ): AnnotatedString {
    return buildAnnotatedString {
      append(AnnotatedString("Pattern: ", SpanStyle(color = chanTheme.textColorSecondaryCompose)))
      append(chanFilter.pattern ?: "<No pattern?!>")
      append("\n")

      val filterTypes = FilterType.forFlags(chanFilter.type)
        .joinToString(
          separator = ", ",
          transform = { filterType -> FilterType.filterTypeName(filterType) }
        )

      append(AnnotatedString("Filter type: ", SpanStyle(color = chanTheme.textColorSecondaryCompose)))
      append(filterTypes)
      append("\n")

      append(AnnotatedString("Boards: ", SpanStyle(color = chanTheme.textColorSecondaryCompose)))

      val enabledOnBoards = if (chanFilter.allBoards()) {
        "All currently added boards (${activeBoardsCountForAllSites ?: "???"})"
      } else {
        "${chanFilter.boards.size} boards"
      }

      append(enabledOnBoards)
      append("\n")

      append(AnnotatedString("Action: ", SpanStyle(color = chanTheme.textColorSecondaryCompose)))
      val filterAction = FilterAction.forId(chanFilter.action)
      val filterActionName = FilterAction.filterActionName(filterAction)
      append(filterActionName)

      if (filterAction == FilterAction.COLOR) {
        append("  ")
        appendInlineContent(squareDrawableKey)
      }

      if (chanFilter.applyToReplies) {
        append("\n")
        append(AnnotatedString("Apply to replies: ", SpanStyle(color = chanTheme.textColorSecondaryCompose)))
        append(chanFilter.applyToReplies.toString())
      }

      if (chanFilter.onlyOnOP) {
        append("\n")
        append(AnnotatedString("Only apply to OP: ", SpanStyle(color = chanTheme.textColorSecondaryCompose)))
        append(chanFilter.onlyOnOP.toString())
      }

      if (chanFilter.applyToSaved) {
        append("\n")
        append(AnnotatedString("Apply to my own posts: ", SpanStyle(color = chanTheme.textColorSecondaryCompose)))
        append(chanFilter.applyToSaved.toString())
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

        append(AnnotatedString("Note: ", SpanStyle(color = chanTheme.textColorSecondaryCompose)))
        append(filterNotAnnotated)
      }
    }
  }

  data class ChanFilterInfo(
    val chanFilter: ChanFilter,
    val filterText: AnnotatedString,
    val detectedLinksInNote: List<TextRange>
  )

  companion object {
    const val squareDrawableKey = "[SQUARE_PAINTER]"
    const val LINK_TAG = "[LINK]"
  }

}