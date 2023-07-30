package com.github.k1rakishou.chan.features.setup

import android.content.Context
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.setup.data.AddBoardsControllerState
import com.github.k1rakishou.chan.features.setup.epoxy.epoxySelectableBoardView
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.layout.SearchLayout
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.utils.addOneshotModelBuildListener
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

class AddBoardsController(
  context: Context,
  private val siteDescriptor: SiteDescriptor,
  private val refreshBoardsFunc: () -> Unit
) : BaseFloatingController(context), AddBoardsView {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager

  private val presenter by lazy {
    AddBoardsPresenter(
      siteDescriptor = siteDescriptor,
      siteManager = siteManager,
      boardManager = boardManager
    )
  }

  private lateinit var outsideArea: FrameLayout
  private lateinit var searchView: SearchLayout
  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView
  private lateinit var checkAll: ColorizableBarButton
  private lateinit var cancel: ColorizableBarButton
  private lateinit var addBoards: ColorizableBarButton
  private lateinit var addBoardsExecutor: RendezvousCoroutineExecutor

  override fun getLayoutId(): Int = R.layout.controller_add_boards

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @OptIn(ExperimentalTime::class)
  override fun onCreate() {
    super.onCreate()

    searchView = view.findViewById(R.id.search_view)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    checkAll = view.findViewById(R.id.check_uncheck_all_boards)
    cancel = view.findViewById(R.id.cancel_adding_boards)
    addBoards = view.findViewById(R.id.add_boards)
    outsideArea = view.findViewById(R.id.outside_area)

    mainScope.launch {
      startListeningForSearchQueries()
        .debounce(50.milliseconds)
        .collect { query -> presenter.onSearchQueryChanged(query) }
    }

    cancel.setOnClickListener {
      pop()
    }
    outsideArea.setOnClickListener {
      pop()
    }

    addBoardsExecutor = RendezvousCoroutineExecutor(mainScope)

    checkAll.setOnClickListener {
      addBoardsExecutor.post {
        presenter.checkUncheckAll(searchView.text)
      }
    }
    addBoards.setOnClickListener {
      addBoardsExecutor.post {
        presenter.addSelectedBoards()
        pop()
      }
    }

    compositeDisposable.add(
      presenter.listenForStateChanges()
        .subscribe { state -> onStateChanged(state) }
    )

    presenter.onCreate(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    epoxyRecyclerView.clear()
    presenter.onDestroy()
  }

  override fun pop(): Boolean {
    if (!super.pop()) {
      return false
    }

    refreshBoardsFunc()
    stopPresenting()

    return true
  }

  private fun onStateChanged(state: AddBoardsControllerState) {
    epoxyRecyclerView.withModels {
      addOneshotModelBuildListener {
        val llm = (epoxyRecyclerView.layoutManager as LinearLayoutManager)

        if (llm.findFirstCompletelyVisibleItemPosition() <= 1) {
          // Scroll to the top of the nav history list if the previous fully visible item's position
          // was either 0 or 1
          llm.scrollToPosition(0)
        }
      }

      when (state) {
        AddBoardsControllerState.Loading -> {
          epoxyLoadingView {
            id("add_boards_loading_view")
          }
        }
        AddBoardsControllerState.Empty -> {
          epoxyTextView {
            id("add_boards_empty_text_view")
            message(context.getString(R.string.controller_add_boards_no_boards))
          }
        }
        is AddBoardsControllerState.Error -> {
          epoxyErrorView {
            id("add_boards_error_view")
            errorMessage(state.errorText)
          }
        }
        is AddBoardsControllerState.Data -> {
          state.selectableBoardCellDataList.forEach { selectableBoardCellData ->
            epoxySelectableBoardView {
              id("add_boards_selectable_board_view_${selectableBoardCellData.catalogCellData.catalogDescriptor}")
              boardName(selectableBoardCellData.catalogCellData.fullName)
              boardDescription(selectableBoardCellData.catalogCellData.description)
              boardSelected(selectableBoardCellData.selected)
              bindQuery(selectableBoardCellData.catalogCellData.searchQuery)
              onClickedCallback { isChecked ->
                val boardDescriptor = checkNotNull(selectableBoardCellData.catalogCellData.boardDescriptorOrNull) {
                  "Cannot use CompositeCatalogDescriptor here"
                }

                presenter.onBoardSelectionChanged(
                  boardDescriptor = boardDescriptor,
                  checked = isChecked,
                  query = searchView.text
                )
              }
            }
          }
        }
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun startListeningForSearchQueries(): Flow<String> {
    return callbackFlow<String> {
      searchView.setCallback { query ->
        trySend(query)
      }

      awaitClose()
    }
  }

}