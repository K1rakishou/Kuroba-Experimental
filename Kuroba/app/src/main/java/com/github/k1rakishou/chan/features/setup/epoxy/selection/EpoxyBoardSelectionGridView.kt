package com.github.k1rakishou.chan.features.setup.epoxy.selection

import android.view.ViewParent
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import com.airbnb.epoxy.EpoxyModelWithHolder
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

@EpoxyModelClass(layout = R.layout.epoxy_board_selection_grid_view)
abstract class EpoxyBoardSelectionGridView : EpoxyModelWithHolder<BaseBoardSelectionViewHolder>()  {

  @EpoxyAttribute
  var topTitle: String? = null
  @EpoxyAttribute
  var bottomTitle: String? = null
  @EpoxyAttribute
  var selected: Boolean = false
  @EpoxyAttribute(value = [EpoxyAttribute.Option.IgnoreRequireHashCode])
  var catalogDescriptor: ChanDescriptor.ICatalogDescriptor? = null
  @EpoxyAttribute
  var searchQuery: String? = null
  @EpoxyAttribute(value = [EpoxyAttribute.Option.IgnoreRequireHashCode])
  var clickListener: (() -> Unit)? = null

  override fun bind(holder: BaseBoardSelectionViewHolder) {
    super.bind(holder)

    holder.bindTopTitle(topTitle)
    holder.bindBottomTitle(bottomTitle)
    holder.bindQuery(searchQuery)
    holder.bindCurrentlySelected(selected)
    holder.bindRowClickCallback(clickListener)
  }

  override fun createNewHolder(parent: ViewParent): BaseBoardSelectionViewHolder {
    return BaseBoardSelectionViewHolder()
  }

}