package com.github.k1rakishou.chan.features.reordering

import android.annotation.SuppressLint
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyModel
import com.airbnb.epoxy.EpoxyModelTouchCallback
import com.airbnb.epoxy.EpoxyViewHolder
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView

class SimpleListItemsReorderingController(
  context: Context,
  items: List<ReorderableItem>,
  private val onApplyClicked: (List<ReorderableItem>) -> Unit
) : BaseFloatingController(context) {
  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView
  private lateinit var itemTouchHelper: ItemTouchHelper

  private val currentItems = ArrayList<ReorderableItem>(items)
  private val controller = ReorderableItemsEpoxyController()

  private val touchHelperCallback = object : EpoxyModelTouchCallback<EpoxyModel<*>>(controller, EpoxyModel::class.java) {

    override fun isLongPressDragEnabled(): Boolean = false
    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlagsForModel(model: EpoxyModel<*>?, adapterPosition: Int): Int {
      return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
    }

    override fun canDropOver(
      recyclerView: RecyclerView,
      current: EpoxyViewHolder,
      target: EpoxyViewHolder
    ): Boolean {
      return true
    }

    override fun onDragStarted(model: EpoxyModel<*>?, itemView: View?, adapterPosition: Int) {
      itemView?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    override fun onMove(
      recyclerView: RecyclerView,
      viewHolder: EpoxyViewHolder,
      target: EpoxyViewHolder
    ): Boolean {
      val fromPosition = viewHolder.adapterPosition
      val toPosition = target.adapterPosition

      currentItems.add(toPosition, currentItems.removeAt(fromPosition))
      renderItems()

      controller.moveModel(fromPosition, toPosition)

      return true
    }

    override fun onDragReleased(model: EpoxyModel<*>?, itemView: View?) {

    }

  }

  init {
    require(items.size > 1) { "There must be at least two items" }
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun getLayoutId(): Int = R.layout.controller_simple_list_items_reordering


  override fun onCreate() {
    super.onCreate()

    val clickableArea = view.findViewById<ConstraintLayout>(R.id.clickable_area)
    clickableArea.setOnClickListener { pop() }

    view.findViewById<ColorizableBarButton>(R.id.cancel_button).setOnClickListener {
      pop()
    }
    view.findViewById<ColorizableBarButton>(R.id.apply_button).setOnClickListener {
      onApplyClicked(currentItems)
      pop()
    }

    epoxyRecyclerView = view.findViewById(R.id.reordering_recycler_view)
    epoxyRecyclerView.setController(controller)

    itemTouchHelper = ItemTouchHelper(touchHelperCallback)
    itemTouchHelper.attachToRecyclerView(epoxyRecyclerView)

    renderItems()
  }

  private fun renderItems() {
    controller.callback = {
      currentItems.forEach { reorderableItem ->
        epoxyReorderableItemView {
          id("epoxy_reorderable_item_${reorderableItem.id}")
          context(context)
          titleText(reorderableItem.title)
        }
      }
    }

    controller.requestModelBuild()
  }

  interface ReorderableItem {
    val id: Long
    val title: String
  }

  data class SimpleReorderableItem(
    override val id: Long,
    override val title: String
  ) : ReorderableItem

  private inner class ReorderableItemsEpoxyController : EpoxyController() {
    var callback: EpoxyController.() -> Unit = {}

    override fun buildModels() {
      callback(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onModelBound(
      holder: EpoxyViewHolder,
      boundModel: EpoxyModel<*>,
      position: Int,
      previouslyBoundModel: EpoxyModel<*>?
    ) {
      val dragIndicator = (boundModel as? EpoxyReorderableItemView_)?.dragIndicator
      if (dragIndicator == null) {
        return
      }

      dragIndicator.setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
          itemTouchHelper.startDrag(holder)
        }

        return@setOnTouchListener false
      }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onModelUnbound(holder: EpoxyViewHolder, model: EpoxyModel<*>) {
      val dragIndicator = (model as? EpoxyReorderableItemView_)?.dragIndicator
      if (dragIndicator == null) {
        return
      }

      dragIndicator.setOnTouchListener(null)
    }
  }

}