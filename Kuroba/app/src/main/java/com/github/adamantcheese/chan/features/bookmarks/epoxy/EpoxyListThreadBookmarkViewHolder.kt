package com.github.adamantcheese.chan.features.bookmarks.epoxy

import android.content.Context
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import com.airbnb.epoxy.EpoxyModelWithHolder
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.features.bookmarks.data.ThreadBookmarkStats
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor

@EpoxyModelClass(layout = R.layout.epoxy_list_thread_bookmark_view)
abstract class EpoxyListThreadBookmarkViewHolder : EpoxyModelWithHolder<BaseThreadBookmarkViewHolder>() {
  @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
  var imageLoaderRequestData: BaseThreadBookmarkViewHolder.ImageLoaderRequestData? = null
  @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
  var threadDescriptor: ChanDescriptor.ThreadDescriptor? = null
  @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
  var clickListener: ((ChanDescriptor.ThreadDescriptor) -> Unit)? = null
  @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
  var context: Context? = null

  @EpoxyAttribute
  var threadBookmarkStats: ThreadBookmarkStats? = null
  @EpoxyAttribute
  var titleString: String? = null

  override fun bind(holder: BaseThreadBookmarkViewHolder) {
    super.bind(holder)

    holder.setImageLoaderRequestData(imageLoaderRequestData)
    holder.setDescriptor(threadDescriptor)
    holder.setThreadBookmarkStats(threadBookmarkStats)
    holder.clickListener(clickListener)
    holder.setTitle(titleString)

    context?.let { holder.bindImage(false, it) }
  }

  override fun unbind(holder: BaseThreadBookmarkViewHolder) {
    super.unbind(holder)

    holder.unbind()
  }

  override fun createNewHolder(): BaseThreadBookmarkViewHolder {
    return BaseThreadBookmarkViewHolder(
      context!!.resources.getDimension(R.dimen.thread_list_bookmark_view_image_size).toInt()
    )
  }

}