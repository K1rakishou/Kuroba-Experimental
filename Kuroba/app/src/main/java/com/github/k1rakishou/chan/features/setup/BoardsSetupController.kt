package com.github.k1rakishou.chan.features.setup

import android.annotation.SuppressLint
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyModel
import com.airbnb.epoxy.EpoxyModelTouchCallback
import com.airbnb.epoxy.EpoxyViewHolder
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.usecase.CreateBoardManuallyUseCase
import com.github.k1rakishou.chan.features.setup.data.BoardsSetupControllerState
import com.github.k1rakishou.chan.features.setup.epoxy.EpoxyBoardView
import com.github.k1rakishou.chan.features.setup.epoxy.EpoxyBoardViewModel_
import com.github.k1rakishou.chan.features.setup.epoxy.epoxyBoardView
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.launch
import javax.inject.Inject

class BoardsSetupController(
  context: Context,
  private val siteDescriptor: SiteDescriptor
) : Controller(context), BoardsSetupView {

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var createBoardManuallyUseCase: CreateBoardManuallyUseCase
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val controller = BoardsEpoxyController()

  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView
  private lateinit var fabAddBoards: ColorizableFloatingActionButton
  private lateinit var itemTouchHelper: ItemTouchHelper

  private val presenter by lazy {
    BoardsSetupPresenter(
      siteDescriptor = siteDescriptor,
      siteManager = siteManager,
      boardManager = boardManager,
      createBoardManuallyUseCase = createBoardManuallyUseCase
    )
  }

  private val loadingViewController by lazy {
    LoadingViewController(
      context,
      true,
    )
  }

  private val touchHelperCallback = object : EpoxyModelTouchCallback<EpoxyBoardViewModel_>(
    controller,
    EpoxyBoardViewModel_::class.java
  ) {
    override fun isLongPressDragEnabled(): Boolean = false
    override fun isItemViewSwipeEnabled(): Boolean = true

    override fun getMovementFlagsForModel(model: EpoxyBoardViewModel_?, adapterPosition: Int): Int {
      return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.RIGHT)
    }

    override fun onDragStarted(model: EpoxyBoardViewModel_?, itemView: View?, adapterPosition: Int) {
      itemView?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    override fun onMove(
      recyclerView: RecyclerView?,
      viewHolder: EpoxyViewHolder?,
      target: EpoxyViewHolder?
    ): Boolean {
      val fromPosition = viewHolder?.adapterPosition
        ?: return false
      val toPosition = target?.adapterPosition
        ?: return false

      val fromBoardDescriptor = (viewHolder.model as? EpoxyBoardViewModel_)?.boardDescriptor()
      val toBoardDescriptor = (target.model as? EpoxyBoardViewModel_)?.boardDescriptor()

      if (fromBoardDescriptor == null || toBoardDescriptor == null) {
        return false
      }

      presenter.onBoardMoving(fromBoardDescriptor, fromPosition, toPosition)
      controller.moveModel(fromPosition, toPosition)

      return true
    }

    override fun onDragReleased(model: EpoxyBoardViewModel_?, itemView: View?) {
      presenter.onBoardMoved()
    }

    override fun onSwiped(viewHolder: EpoxyViewHolder?, direction: Int) {
      val boardDescriptor = (viewHolder?.model as? EpoxyBoardViewModel_)?.boardDescriptor()
        ?: return

      presenter.onBoardRemoved(boardDescriptor)
    }
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()
    navigation.title = context.getString(R.string.controller_boards_setup_title, siteDescriptor.siteName)

    val canCreateBoardsManually = siteManager.bySiteDescriptor(siteDescriptor)
      ?.canCreateBoardsManually ?: false

    if (canCreateBoardsManually) {
      navigation.buildMenu(context)
        .withItem(R.drawable.ic_create_white_24dp) {
          onCreateBoardManuallyClicked()
        }
        .build()
    }

    view = inflate(context, R.layout.controller_boards_setup)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    epoxyRecyclerView.setController(controller)

    itemTouchHelper = ItemTouchHelper(touchHelperCallback)
    itemTouchHelper.attachToRecyclerView(epoxyRecyclerView)

    fabAddBoards = view.findViewById(R.id.fab_add_boards)
    fabAddBoards.setOnClickListener {
      val controller = AddBoardsController(context, siteDescriptor) {
        presenter.displayActiveBoards()
      }

      navigationController!!.presentController(controller)
    }

    compositeDisposable.add(
      presenter.listenForStateChanges()
        .subscribe { state -> onStateChanged(state) }
    )

    presenter.onCreate(this)
    presenter.updateBoardsFromServerAndDisplayActive()
  }

  private fun onCreateBoardManuallyClicked() {
    dialogFactory.createSimpleDialogWithInput(
      context = context,
      titleTextId = R.string.controller_enter_board_code,
      inputType = DialogFactory.DialogInputType.String,
      onValueEntered = { boardCode ->
        if (boardCode.isEmpty()) {
          showToast(R.string.controller_board_code_is_empty)
          return@createSimpleDialogWithInput
        }

        presenter.createBoardManually(boardCode)
      }
    )
  }

  override fun onDestroy() {
    super.onDestroy()

    epoxyRecyclerView.swapAdapter(null, true)
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
              boardName(boardCellData.fullName)
              boardDescription(boardCellData.description)
              boardDescriptor(boardCellData.boardDescriptor)
            }
          }
        }
      }
    }

    controller.requestModelBuild()
  }

  override fun showLoadingView() {
    presentController(loadingViewController, animated = false)
  }

  override fun hideLoadingView() {
    loadingViewController.stopPresenting()
  }

  override fun showMessageToast(message: String) {
    showToast(message, Toast.LENGTH_LONG)
  }

  private inner class BoardsEpoxyController : EpoxyController() {
    var callback: EpoxyController.() -> Unit = {}

    override fun buildModels() {
      callback(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewAttachedToWindow(holder: EpoxyViewHolder, model: EpoxyModel<*>) {
      val itemView = holder.itemView

      if (itemView is EpoxyBoardView) {
        itemView.boardReorder.setOnTouchListener { v, event ->
          if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            itemTouchHelper.startDrag(holder)
          }

          return@setOnTouchListener false
        }
      }
    }

  }

}