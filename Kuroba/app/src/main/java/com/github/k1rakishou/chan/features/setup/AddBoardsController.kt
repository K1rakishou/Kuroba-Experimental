package com.github.k1rakishou.chan.features.setup

import android.content.Context
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.features.setup.data.AddBoardsControllerState
import com.github.k1rakishou.chan.features.setup.epoxy.epoxySelectableBoardView
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.layout.SearchLayout
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.addOneshotModelBuildListener
import com.github.k1rakishou.chan.utils.plusAssign
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

class AddBoardsController(
  context: Context,
  private val siteDescriptor: SiteDescriptor,
  private val callback: RefreshBoardsCallback
) : BaseFloatingController(context), AddBoardsView {
  private val presenter = AddBoardsPresenter(siteDescriptor)

  @Inject
  lateinit var themeEngine: ThemeEngine

  private lateinit var outsideArea: FrameLayout
  private lateinit var searchView: SearchLayout
  private lateinit var epoxyRecyclerView: EpoxyRecyclerView
  private lateinit var checkAll: MaterialButton
  private lateinit var cancel: MaterialButton
  private lateinit var addBoards: MaterialButton
  private lateinit var addBoardsExecutor: RendezvousCoroutineExecutor

  private var presenting = true

  override fun getLayoutId(): Int = R.layout.controller_add_boards

  @OptIn(ExperimentalTime::class)
  override fun onCreate() {
    super.onCreate()
    Chan.inject(this)

    searchView = view.findViewById(R.id.search_view)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    checkAll = view.findViewById(R.id.check_uncheck_all_boards)
    cancel = view.findViewById(R.id.cancel_adding_boards)
    addBoards = view.findViewById(R.id.add_boards)
    outsideArea = view.findViewById(R.id.outside_area)

    val cardView = view.findViewById<CardView>(R.id.card_view)
    cardView.setCardBackgroundColor(themeEngine.chanTheme.primaryColor)

    mainScope.launch {
      startListeningForSearchQueries()
        .debounce(350.milliseconds)
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

    compositeDisposable += presenter.listenForStateChanges()
      .subscribe { state -> onStateChanged(state) }

    presenter.onCreate(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    presenter.onDestroy()
  }

  override fun onBack(): Boolean {
    if (presenting) {
      pop()
      return true
    }

    return super.onBack()
  }

  private fun pop() {
    if (!presenting) {
      return
    }

    presenting = false

    callback.onRefreshBoards()
    stopPresenting()
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
              id("add_boards_selectable_board_view_${selectableBoardCellData.boardCellData.boardDescriptor}")
              boardName(selectableBoardCellData.boardCellData.name)
              boardDescription(selectableBoardCellData.boardCellData.description)
              boardSelected(selectableBoardCellData.selected)
              onClickedCallback { isChecked ->
                presenter.onBoardSelectionChanged(
                  selectableBoardCellData.boardCellData.boardDescriptor,
                  isChecked,
                  searchView.text
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
        offer(query)
      }

      awaitClose()
    }
  }

  fun interface RefreshBoardsCallback {
    fun onRefreshBoards()
  }
}