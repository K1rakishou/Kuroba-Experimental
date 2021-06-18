package com.github.k1rakishou.chan.features.search

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.site.sites.search.SearchBoard
import com.github.k1rakishou.chan.features.search.epoxy.epoxySelectableBoardItemView
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

class SelectBoardForSearchController(
  context: Context,
  private val supportsAllBoardsSearch: Boolean,
  private val searchBoardProvider: () -> Collection<SearchBoard>,
  private val prevSelectedBoard: SearchBoard?,
  private val onBoardSelected: (SearchBoard) -> Unit
) : BaseFloatingController(context), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var archivesManager: ArchivesManager

  private val controller = BoardsEpoxyController()

  private lateinit var clickableArea: ConstraintLayout
  private lateinit var recyclerView: ColorizableEpoxyRecyclerView

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun getLayoutId(): Int = R.layout.controller_generic_floating_with_recycler_view

  override fun onCreate() {
    super.onCreate()

    recyclerView = view.findViewById(R.id.recycler_view)

    clickableArea = view.findViewById(R.id.clickable_area)
    clickableArea.setOnClickListener { pop() }

    recyclerView.layoutManager = GridLayoutManager(context, SPAN_COUNT).apply {
      spanSizeLookup = controller.spanSizeLookup
    }

    renderArchiveSiteBoardsSupportingSearch()
    themeEngine.addListener(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    renderArchiveSiteBoardsSupportingSearch()
  }

  private fun renderArchiveSiteBoardsSupportingSearch() {
    val boardsSupportingSearch = mutableListOf<SearchBoard>()

    if (supportsAllBoardsSearch) {
      boardsSupportingSearch += SearchBoard.AllBoards
    }

    boardsSupportingSearch.addAll(searchBoardProvider())

    if (boardsSupportingSearch.isEmpty()) {
      pop()
      return
    }

    recyclerView.withModels {
      val backColor = themeEngine.chanTheme.backColor

      boardsSupportingSearch.forEach { searchBoard ->
        val siteBackgroundColor = getBoardItemBackgroundColor(searchBoard, backColor)
        val searchBoardCodeText = searchBoard.boardCode()

        epoxySelectableBoardItemView {
          id("epoxy_selectable_board_item_view_${searchBoardCodeText}")
          bindBoardName(searchBoardCodeText)
          itemBackgroundColor(siteBackgroundColor)
          bindClickCallback {
            onBoardSelected(searchBoard)
            pop()
          }
        }
      }
    }
  }

  private fun getBoardItemBackgroundColor(searchBoard: SearchBoard, backColor: Int): Int {
    if (searchBoard != prevSelectedBoard) {
      return backColor
    }

    return if (ThemeEngine.isDarkColor(backColor)) {
      ThemeEngine.manipulateColor(backColor, 1.3f)
    } else {
      ThemeEngine.manipulateColor(backColor, .7f)
    }
  }

  private inner class BoardsEpoxyController : EpoxyController() {
    var callback: EpoxyController.() -> Unit = {}

    override fun buildModels() {
      callback(this)
    }

  }

  companion object {
    private const val SPAN_COUNT = 4
  }

}