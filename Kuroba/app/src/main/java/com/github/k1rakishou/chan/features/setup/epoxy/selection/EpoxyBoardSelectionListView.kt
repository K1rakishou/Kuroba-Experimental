package com.github.k1rakishou.chan.features.setup.epoxy.selection

import android.view.ViewParent
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import com.airbnb.epoxy.EpoxyModelWithHolder
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

@EpoxyModelClass
abstract class EpoxyBoardSelectionListView : EpoxyModelWithHolder<BaseBoardSelectionViewHolder>() {

  @EpoxyAttribute
  var topTitle: String? = null
  @EpoxyAttribute
  var bottomTitle: CharSequence? = null
  @EpoxyAttribute
  var selected: Boolean = false
  @EpoxyAttribute(value = [EpoxyAttribute.Option.IgnoreRequireHashCode])
  var catalogDescriptor: ChanDescriptor.ICatalogDescriptor? = null
  @EpoxyAttribute
  var searchQuery: String? = null
  @EpoxyAttribute(value = [EpoxyAttribute.Option.IgnoreRequireHashCode])
  var clickListener: (() -> Unit)? = null

  override fun getDefaultLayout(): Int = R.layout.epoxy_board_selection_list_view

  override fun bind(holder: BaseBoardSelectionViewHolder) {
    super.bind(holder)

    holder.bindTopTitle(topTitle)
    holder.bindBottomTitle(bottomTitle)
    holder.bindQuery(searchQuery)
    holder.bindCurrentlySelected(selected)
    holder.bindRowClickCallback(clickListener)
    holder.afterPropsSet()
  }

  override fun unbind(holder: BaseBoardSelectionViewHolder) {
    super.unbind(holder)
    holder.unbind()
  }

  override fun createNewHolder(parent: ViewParent): BaseBoardSelectionViewHolder {
    return BaseBoardSelectionViewHolder()
  }

}