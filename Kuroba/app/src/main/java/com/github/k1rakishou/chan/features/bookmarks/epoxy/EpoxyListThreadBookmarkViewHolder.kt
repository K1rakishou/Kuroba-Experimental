package com.github.k1rakishou.chan.features.bookmarks.epoxy

import android.content.Context
import android.view.ViewParent
import androidx.appcompat.widget.AppCompatImageView
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import com.airbnb.epoxy.EpoxyModelWithHolder
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkSelection
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkStats
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import javax.inject.Inject

@EpoxyModelClass
abstract class EpoxyListThreadBookmarkViewHolder
  : EpoxyModelWithHolder<BaseThreadBookmarkViewHolder>(),
  ThemeEngine.ThemeChangesListener,
  UnifiedBookmarkInfoAccessor {

  @Inject
  lateinit var themeEngine: ThemeEngine

  @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
  var imageLoaderRequestData: BaseThreadBookmarkViewHolder.ImageLoaderRequestData? = null
  @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
  var threadDescriptor: ChanDescriptor.ThreadDescriptor? = null
  @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
  var context: Context? = null

  @EpoxyAttribute(value = [EpoxyAttribute.Option.IgnoreRequireHashCode])
  var bookmarkClickListener: ((ChanDescriptor.ThreadDescriptor) -> Unit)? = null
  @EpoxyAttribute(value = [EpoxyAttribute.Option.IgnoreRequireHashCode])
  var bookmarkLongClickListener: ((ChanDescriptor.ThreadDescriptor) -> Unit)? = null
  @EpoxyAttribute(value = [EpoxyAttribute.Option.IgnoreRequireHashCode])
  var bookmarkStatsClickListener: ((ChanDescriptor.ThreadDescriptor) -> Unit)? = null

  @EpoxyAttribute
  var threadBookmarkStats: ThreadBookmarkStats? = null
  @EpoxyAttribute
  var threadBookmarkSelection: ThreadBookmarkSelection? = null
  @EpoxyAttribute
  var titleString: String? = null
  @EpoxyAttribute
  var highlightBookmark: Boolean = false
  @EpoxyAttribute
  open var isTablet: Boolean = false
  @EpoxyAttribute
  var groupId: String? = null
  @EpoxyAttribute
  var reorderingMode: Boolean = false

  private var holder: BaseThreadBookmarkViewHolder? = null
  var dragIndicator: AppCompatImageView? = null

  override fun getDefaultLayout(): Int = R.layout.epoxy_list_thread_bookmark_view
  override fun getBookmarkGroupId(): String? = groupId
  override fun getBookmarkStats(): ThreadBookmarkStats? = threadBookmarkStats
  override fun getBookmarkDescriptor(): ChanDescriptor.ThreadDescriptor? = threadDescriptor

  override fun bind(holder: BaseThreadBookmarkViewHolder) {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    super.bind(holder)

    this.holder = holder

    if (reorderingMode) {
      this.dragIndicator = holder.dragIndicator
    } else {
      this.dragIndicator = null
    }

    holder.setImageLoaderRequestData(imageLoaderRequestData)
    holder.setDescriptor(threadDescriptor)
    holder.bookmarkSelection(threadBookmarkSelection)
    holder.bookmarkClickListener(bookmarkClickListener)
    holder.bookmarkLongClickListener(bookmarkLongClickListener)
    holder.bookmarkStatsClickListener(false, bookmarkStatsClickListener)
    holder.setThreadBookmarkStats(false, threadBookmarkStats)
    holder.setTitle(titleString, threadBookmarkStats?.watching ?: false)
    holder.highlightBookmark(highlightBookmark || threadBookmarkSelection?.isSelected == true)
    holder.updateListViewSizes(isTablet)
    holder.updateDragIndicatorColors(false)
    holder.updateDragIndicatorState(reorderingMode, threadBookmarkStats)

    val watching = threadBookmarkStats?.watching ?: true
    context?.let { holder.bindImage(false, watching, it) }

    themeEngine.addListener(id(), this)
  }

  override fun unbind(holder: BaseThreadBookmarkViewHolder) {
    super.unbind(holder)

    themeEngine.removeListener(id())
    holder.unbind()

    this.holder = null
  }

  override fun onThemeChanged() {
    holder?.apply {
      setThreadBookmarkStats(true, threadBookmarkStats)
      setTitle(titleString, threadBookmarkStats?.watching ?: false)
      highlightBookmark(highlightBookmark)
      updateDragIndicatorColors(false)
    }
  }

  override fun createNewHolder(parent: ViewParent): BaseThreadBookmarkViewHolder {
    return BaseThreadBookmarkViewHolder()
  }

}