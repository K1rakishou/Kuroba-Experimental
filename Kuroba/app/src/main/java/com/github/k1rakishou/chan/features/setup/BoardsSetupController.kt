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
import com.airbnb.epoxy.EpoxyRecyclerView
import com.airbnb.epoxy.EpoxyViewHolder
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.features.bypass.CookieResult
import com.github.k1rakishou.chan.features.bypass.FirewallType
import com.github.k1rakishou.chan.features.bypass.SiteFirewallBypassController
import com.github.k1rakishou.chan.features.setup.data.BoardsSetupControllerState
import com.github.k1rakishou.chan.features.setup.epoxy.EpoxyBoardView
import com.github.k1rakishou.chan.features.setup.epoxy.EpoxyBoardViewModel_
import com.github.k1rakishou.chan.features.setup.epoxy.epoxyBoardView
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.updateMargins
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import javax.inject.Inject

class BoardsSetupController(
  context: Context,
  private val siteDescriptor: SiteDescriptor
) : Controller(context), BoardsSetupView, WindowInsetsListener {

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val controller = BoardsEpoxyController()
  private val fabBottomPadding = dp(16f)
  private val recyclerBottomPadding = dp(64f)

  private lateinit var epoxyRecyclerView: EpoxyRecyclerView
  private lateinit var fabAddBoards: ColorizableFloatingActionButton
  private lateinit var itemTouchHelper: ItemTouchHelper

  private val presenter by lazy {
    BoardsSetupPresenter(
      siteDescriptor = siteDescriptor,
      siteManager = siteManager,
      boardManager = boardManager
    )
  }

  private var currentLoadingViewController: LoadingViewController? = null

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
      recyclerView: RecyclerView,
      viewHolder: EpoxyViewHolder,
      target: EpoxyViewHolder
    ): Boolean {
      val fromPosition = viewHolder.adapterPosition
      val toPosition = target.adapterPosition

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

    override fun onSwiped(viewHolder: EpoxyViewHolder, direction: Int) {
      val boardDescriptor = (viewHolder.model as? EpoxyBoardViewModel_)?.boardDescriptor()
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

    val site = siteManager.bySiteDescriptor(siteDescriptor)!!
    val syntheticSite = site.isSynthetic

    val builder = navigation
      .buildMenu(context)

    if (!syntheticSite) {
      builder
        .withItem(R.drawable.ic_refresh_white_24dp) { presenter.updateBoardsFromServerAndDisplayActive() }
    }

    builder
      .withOverflow(navigationController)
      .withSubItem(ACTION_SORT_BOARDS_ALPHABETICALLY, R.string.controller_boards_setup_sort_boards_alphabetically) {
        presenter.sortBoardsAlphabetically()
      }
      .withSubItem(ACTION_DELETE_ALL_BOARDS, R.string.controller_boards_setup_delete_all_boards) {
        presenter.deactivateAllBoards()
      }
      .build()
      .build()

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

    onInsetsChanged()

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
    presenter.onCreate(this)
  }

  override fun onInsetsChanged() {
    val bottomPaddingDp = calculateBottomPaddingForRecyclerInDp(
      globalWindowInsetsManager = globalWindowInsetsManager,
      mainControllerCallbacks = null
    )

    val bottomPaddingPx = dp(bottomPaddingDp.toFloat())

    fabAddBoards.updateMargins(bottom = fabBottomPadding + bottomPaddingPx)
    epoxyRecyclerView.updatePaddings(bottom = recyclerBottomPadding + bottomPaddingPx)
  }

  override fun onDestroy() {
    super.onDestroy()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    epoxyRecyclerView.clear()
    presenter.onDestroy()
  }

  override fun onBoardsLoaded() {
    mainScope.launch { showToast(R.string.controller_boards_setup_boards_updated) }
  }

  override fun showCloudflareBypassController(
    firewallType: FirewallType,
    siteDescriptor: SiteDescriptor,
    urlToOpen: HttpUrl
  ) {
    val siteFirewallBypassController = SiteFirewallBypassController(
      context = context,
      firewallType = firewallType,
      urlToOpen = urlToOpen.toString(),
      onResult = { cookieResult ->
        when (cookieResult) {
          is CookieResult.CookieValue -> {
            presenter.updateBoardsFromServerAndDisplayActive(retrying = true)
            showToast(getString(R.string.firewall_check_success, firewallType))
            return@SiteFirewallBypassController
          }
          is CookieResult.Error -> {
            showToast(getString(
              R.string.firewall_check_failure,
              firewallType, cookieResult.exception.errorMessageOrClassName()
            ))
          }
          CookieResult.Canceled -> {
            showToast(getString(R.string.firewall_check_canceled, firewallType))
          }
          CookieResult.NotSupported -> {
            AppModuleAndroidUtils.showToast(
              context,
              getString(R.string.firewall_check_not_supported, firewallType, siteDescriptor.siteName)
            )
          }
        }
      }
    )

    presentController(siteFirewallBypassController)
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
          state.catalogCellDataList.forEach { boardCellData ->
            val boardDescriptor = checkNotNull(boardCellData.boardDescriptorOrNull) {
              "Cannot use CompositeCatalogDescriptor here"
            }

            epoxyBoardView {
              id("boards_setup_board_view_${boardDescriptor}")
              boardName(boardCellData.fullName)
              boardDescription(boardCellData.description)
              boardDescriptor(boardDescriptor)
            }
          }
        }
      }
    }

    controller.requestModelBuild()
  }

  override fun showLoadingView(titleMessage: String?) {
    val loadingViewController = if (titleMessage.isNullOrEmpty()) {
      LoadingViewController(context, true)
    } else {
      LoadingViewController(context, true, titleMessage)
    }

    presentController(loadingViewController, animated = false)
    currentLoadingViewController = loadingViewController
  }

  override fun hideLoadingView() {
    currentLoadingViewController?.stopPresenting()
    currentLoadingViewController = null
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

  companion object {
    private const val ACTION_SORT_BOARDS_ALPHABETICALLY = 0
    private const val ACTION_DELETE_ALL_BOARDS = 1
  }

}