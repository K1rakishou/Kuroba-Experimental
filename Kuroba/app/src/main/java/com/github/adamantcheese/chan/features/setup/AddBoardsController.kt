package com.github.adamantcheese.chan.features.setup

import android.content.Context
import android.widget.FrameLayout
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.base.RendezvousCoroutineExecutor
import com.github.adamantcheese.chan.features.setup.data.AddBoardsControllerState
import com.github.adamantcheese.chan.features.setup.epoxy.epoxySelectableBoardView
import com.github.adamantcheese.chan.ui.controller.BaseFloatingController
import com.github.adamantcheese.chan.ui.epoxy.epoxyErrorView
import com.github.adamantcheese.chan.ui.epoxy.epoxyLoadingView
import com.github.adamantcheese.chan.ui.epoxy.epoxyTextView
import com.github.adamantcheese.chan.ui.layout.SearchLayout
import com.github.adamantcheese.chan.utils.plusAssign
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

class AddBoardsController(
  context: Context,
  private val siteDescriptor: SiteDescriptor,
  private val callback: RefreshBoardsCallback
) : BaseFloatingController(context), AddBoardsView {
  private val presenter = AddBoardsPresenter(siteDescriptor)

  private lateinit var outsideArea: FrameLayout
  private lateinit var searchView: SearchLayout
  private lateinit var epoxyRecyclerView: EpoxyRecyclerView
  private lateinit var cancel: MaterialButton
  private lateinit var addBoards: MaterialButton
  private lateinit var addBoardsExecutor: RendezvousCoroutineExecutor

  private var presenting = true

  override fun getLayoutId(): Int = R.layout.controller_add_boards

  @OptIn(ExperimentalTime::class)
  override fun onCreate() {
    super.onCreate()

    searchView = view.findViewById(R.id.search_view)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    cancel = view.findViewById(R.id.cancel_adding_boards)
    addBoards = view.findViewById(R.id.add_boards)
    outsideArea = view.findViewById(R.id.outside_area)

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

    addBoards.setOnClickListener {
      addBoardsExecutor.post {
        presenter.addSelectedBoards()
        pop()
      }
    }

    addBoardsExecutor = RendezvousCoroutineExecutor(mainScope)

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
    presenting = false

    callback.onRefreshBoards()
    stopPresenting()
  }

  private fun onStateChanged(state: AddBoardsControllerState) {
    epoxyRecyclerView.withModels {
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
                  isChecked
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