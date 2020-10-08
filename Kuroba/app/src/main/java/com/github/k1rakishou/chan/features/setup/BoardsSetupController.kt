package com.github.k1rakishou.chan.features.setup

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyTouchHelper
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.features.setup.data.BoardsSetupControllerState
import com.github.k1rakishou.chan.features.setup.epoxy.EpoxyBoardViewModel_
import com.github.k1rakishou.chan.features.setup.epoxy.epoxyBoardView
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.helper.BoardHelper
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.plusAssign
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.launch

class BoardsSetupController(
  context: Context,
  private val siteDescriptor: SiteDescriptor
) : Controller(context), BoardsSetupView {
  private val presenter = BoardsSetupPresenter(siteDescriptor)
  private val controller = BoardsEpoxyController()

  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView
  private lateinit var fabAddBoards: ColorizableFloatingActionButton

  override fun onCreate() {
    super.onCreate()
    Chan.inject(this)

    navigation.title = context.getString(R.string.controller_boards_setup_title, siteDescriptor.siteName)

    view = AndroidUtils.inflate(context, R.layout.controller_boards_setup)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    epoxyRecyclerView.setController(controller)

    fabAddBoards = view.findViewById(R.id.fab_add_boards)

    fabAddBoards.setOnClickListener {
      val controller = AddBoardsController(context, siteDescriptor) {
        presenter.displayActiveBoards()
      }

      navigationController!!.presentController(controller)
    }

    EpoxyTouchHelper
      .initDragging(controller)
      .withRecyclerView(epoxyRecyclerView)
      .forVerticalList()
      .withTarget(EpoxyBoardViewModel_::class.java)
      .andCallbacks(object : EpoxyTouchHelper.DragCallbacks<EpoxyBoardViewModel_>() {
        override fun onDragStarted(model: EpoxyBoardViewModel_?, itemView: View?, adapterPosition: Int) {
          itemView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        override fun onModelMoved(
          fromPosition: Int,
          toPosition: Int,
          modelBeingMoved: EpoxyBoardViewModel_,
          itemView: View?
        ) {
          modelBeingMoved.boardDescriptor()?.let { boardDescriptor ->
            presenter.onBoardMoving(boardDescriptor, fromPosition, toPosition)
          }
        }

        override fun onDragReleased(model: EpoxyBoardViewModel_?, itemView: View?) {
          presenter.onBoardMoved()
        }
      })

    EpoxyTouchHelper
      .initSwiping(epoxyRecyclerView)
      .right()
      .withTarget(EpoxyBoardViewModel_::class.java)
      .andCallbacks(object : EpoxyTouchHelper.SwipeCallbacks<EpoxyBoardViewModel_>() {
        override fun onSwipeCompleted(model: EpoxyBoardViewModel_?, itemView: View?, position: Int, direction: Int) {
          model?.boardDescriptor()?.let { boardDescriptor ->
            presenter.onBoardRemoved(boardDescriptor)
          }
        }
      })

    compositeDisposable += presenter.listenForStateChanges()
      .subscribe { state -> onStateChanged(state) }

    presenter.onCreate(this)
    presenter.updateBoardsFromServerAndDisplayActive()
  }

  override fun onDestroy() {
    super.onDestroy()

    presenter.onDestroy()
  }

  override fun onBoardsLoaded() {
    mainScope.launch { showToast(R.string.controller_boards_setup_boards_updated) }
  }

  private fun onStateChanged(state: BoardsSetupControllerState) {
    controller.callback = {
      when (state) {
        BoardsSetupControllerState.Loading -> {
          epoxyLoadingView {
            id("boards_setup_loading_view")
          }
        }
        BoardsSetupControllerState.Empty -> {
          epoxyTextView {
            id("boards_setup_empty_text_view")
            message(context.getString(R.string.controller_boards_setup_no_boards))
          }
        }
        is BoardsSetupControllerState.Error -> {
          epoxyErrorView {
            id("boards_setup_error_view")
            errorMessage(state.errorText)
          }
        }
        is BoardsSetupControllerState.Data -> {
          state.boardCellDataList.forEach { boardCellData ->
            epoxyBoardView {
              id("boards_setup_board_view_${boardCellData.boardDescriptor}")
              boardName(BoardHelper.getName(boardCellData.boardDescriptor.boardCode, boardCellData.name))
              boardDescription(boardCellData.description)
              boardDescriptor(boardCellData.boardDescriptor)
            }
          }
        }
      }
    }

    controller.requestModelBuild()
  }

  private class BoardsEpoxyController : EpoxyController() {
    var callback: EpoxyController.() -> Unit = {}

    override fun buildModels() {
      callback(this)
    }
  }

}