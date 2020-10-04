package com.github.k1rakishou.chan.features.setup

import android.content.Context
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.setup.data.BoardSelectionControllerState
import com.github.k1rakishou.chan.features.setup.epoxy.selection.epoxyBoardSelectionView
import com.github.k1rakishou.chan.features.setup.epoxy.selection.epoxySiteSelectionView
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.layout.SearchLayout
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.utils.plusAssign
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

class BoardSelectionController(
  context: Context,
  private val callback: UserSelectionListener
) : BaseFloatingController(context), BoardSelectionView {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val presenter = BoardSelectionPresenter()

  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView
  private lateinit var searchView: SearchLayout
  private lateinit var outsideArea: FrameLayout
  private lateinit var openSitesButton: ColorizableBarButton

  private var presenting = true

  override fun getLayoutId(): Int = R.layout.controller_board_selection

  @OptIn(ExperimentalTime::class)
  override fun onCreate() {
    super.onCreate()
    Chan.inject(this)

    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    epoxyRecyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)

    outsideArea = view.findViewById(R.id.outside_area)
    searchView = view.findViewById(R.id.search_view)
    searchView.setAutoRequestFocus(false)
    openSitesButton = view.findViewById(R.id.open_all_sites_settings)

    openSitesButton.setOnClickListener {
      callback.onOpenSitesSettingsClicked()
      pop()
    }

    outsideArea.setOnClickListener {
      pop()
    }

    mainScope.launch {
      startListeningForSearchQueries()
        .debounce(350.milliseconds)
        .collect { query -> presenter.onSearchQueryChanged(query) }
    }

    compositeDisposable += presenter.listenForStateChanges()
      .subscribe { state -> onStateChanged(state) }

    presenter.onCreate(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    presenter.onDestroy()
  }

  private fun onStateChanged(state: BoardSelectionControllerState) {
    epoxyRecyclerView.withModels {
      when (state) {
        BoardSelectionControllerState.Loading -> {
          epoxyLoadingView {
            id("boards_selection_loading_view")
          }
        }
        BoardSelectionControllerState.Empty -> {
          epoxyTextView {
            id("boards_selection_empty_text_view")
            message(context.getString(R.string.controller_boards_selection_no_boards))
          }
        }
        is BoardSelectionControllerState.Error -> {
          epoxyErrorView {
            id("boards_selection_error_view")
            errorMessage(state.errorText)
          }
        }
        is BoardSelectionControllerState.Data -> {
          state.sortedSiteWithBoardsData.entries.forEach { (siteCellData, boardCellDataList) ->
            epoxySiteSelectionView {
              id("boards_selection_site_selection_view_${siteCellData.siteDescriptor}")
              bindIcon(siteCellData.siteIcon)
              bindSiteName(siteCellData.siteName)
              bindRowClickCallback {
                callback.onSiteSelected(siteCellData.siteDescriptor)
                pop()
              }
            }

            boardCellDataList.forEach { boardCellData ->
              epoxyBoardSelectionView {
                id("boards_selection_board_selection_view_${boardCellData.boardDescriptor}")
                bindBoardName(boardCellData.name)
                bindRowClickCallback {
                  callback.onBoardSelected(boardCellData.boardDescriptor)
                  pop()
                }
              }
            }
          }
        }
      }
    }
  }

  override fun onBack(): Boolean {
    if (presenting) {
      pop()
      return true
    }

    return super.onBack()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun startListeningForSearchQueries(): Flow<String> {
    return callbackFlow<String> {
      searchView.setCallback { query ->
        offer(query)
      }

      awaitClose()
    }
  }


  private fun pop() {
    if (!presenting) {
      return
    }

    presenting = false
    stopPresenting()
  }

  interface UserSelectionListener {
    fun onOpenSitesSettingsClicked()
    fun onSiteSelected(siteDescriptor: SiteDescriptor)
    fun onBoardSelected(boardDescriptor: BoardDescriptor)
  }

}