package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.ui.cell.ThreadCellData
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.*

class PostHighlightManager(
  private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
) {
  private val catalogHighlightedPosts = mutableMapWithCap<PostDescriptor, PostHighlight>(256)
  private val threadHighlightedPosts = mutableMapWithCap<PostDescriptor, PostHighlight>(256)

  private val _highlightedPostsUpdateFlow = MutableSharedFlow<PostHighlightEvent>(extraBufferCapacity = 512)
  val highlightedPostsUpdateFlow: SharedFlow<PostHighlightEvent>
    get() = _highlightedPostsUpdateFlow.asSharedFlow()

  fun onCatalogLoaded(threadCellData: ThreadCellData) {
    if (threadCellData.chanDescriptor?.isCatalogDescriptor() == false) {
      return
    }

    val openedThreadDescriptor = currentOpenedDescriptorStateManager.currentThreadDescriptor
    if (openedThreadDescriptor != null) {
      highlightPosts(threadCellData, setOf(openedThreadDescriptor.toOriginalPostDescriptor()), false)
    }
  }

  fun cleanup(chanDescriptor: ChanDescriptor) {
    getHighlightedPostsMap(chanDescriptor).clear()
  }

  fun onPostBound(chanDescriptor: ChanDescriptor, postDescriptor: PostDescriptor): PostHighlight? {
    BackgroundUtils.ensureMainThread()

    val postHighlight = getHighlightedPostsMap(chanDescriptor).get(postDescriptor)
      ?: return null

    postHighlight.onPostBound()
    return postHighlight
  }

  fun getPostHighlight(chanDescriptor: ChanDescriptor, postDescriptor: PostDescriptor): PostHighlight? {
    BackgroundUtils.ensureMainThread()
    return getHighlightedPostsMap(chanDescriptor).get(postDescriptor)
  }

  fun highlightPosts(threadCellData: ThreadCellData, postDescriptors: Set<PostDescriptor>?, blink: Boolean) {
    BackgroundUtils.ensureMainThread()

    threadCellData.forEach { postCellDataLazy ->
      val postDescriptor = postCellDataLazy.postDescriptor

      updatePostHighlight(threadCellData.chanDescriptor, postCellDataLazy.postDescriptor) { postHighlight ->
        if (postDescriptors != null && postDescriptors.contains(postDescriptor)) {
          if (blink) {
            postHighlight.currentHighlightTypes.set(HighlightType.Blink.bit)
          }

          if (!blink || threadCellData.chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
            postHighlight.currentHighlightTypes.set(HighlightType.Regular.bit)
          }
        } else {
          postHighlight.currentHighlightTypes.clear(HighlightType.Regular.bit)
        }

        return@updatePostHighlight postHighlight
      }
    }
  }

  private fun updatePostHighlight(
    chanDescriptor: ChanDescriptor?,
    postDescriptor: PostDescriptor,
    updater: (PostHighlight) -> PostHighlight
  ) {
    if (chanDescriptor == null) {
      return
    }

    BackgroundUtils.ensureMainThread()
    val highlightedPosts = getHighlightedPostsMap(chanDescriptor)

    var oldPostHighlight = highlightedPosts[postDescriptor]
    if (oldPostHighlight == null) {
      oldPostHighlight = PostHighlight(postDescriptor)
    }

    val updatedPostHighlight = updater(oldPostHighlight.fullCopy())
    highlightedPosts[postDescriptor] = updatedPostHighlight

    val postHighlightEvent = PostHighlightEvent(
      isCatalogDescriptor = chanDescriptor.isCatalogDescriptor(),
      postHighlight = updatedPostHighlight
    )
    _highlightedPostsUpdateFlow.tryEmit(postHighlightEvent)
  }

  private fun getHighlightedPostsMap(chanDescriptor: ChanDescriptor): MutableMap<PostDescriptor, PostHighlight> {
    return when (chanDescriptor) {
      is ChanDescriptor.CompositeCatalogDescriptor,
      is ChanDescriptor.CatalogDescriptor -> catalogHighlightedPosts
      is ChanDescriptor.ThreadDescriptor -> threadHighlightedPosts
    }
  }

  data class PostHighlightEvent(
    val isCatalogDescriptor: Boolean,
    val postHighlight: PostHighlight
  ) {
    val postNo: Long
      get() = postHighlight.postDescriptor.postNo
  }

  class PostHighlight(
    val postDescriptor: PostDescriptor,
    val currentHighlightTypes: BitSet = BitSet()
  ) {
    fun isHighlighted(): Boolean {
      return currentHighlightTypes.cardinality() > 0
    }

    fun isBlinking(): Boolean = currentHighlightTypes.get(HighlightType.Blink.bit)

    fun onPostBound() {
      currentHighlightTypes.clear(HighlightType.Blink.bit)
    }

    fun fullCopy(): PostHighlight {
      return PostHighlight(
        postDescriptor = postDescriptor,
        currentHighlightTypes = currentHighlightTypes.clone() as BitSet
      )
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as PostHighlight

      if (postDescriptor != other.postDescriptor) return false
      if (currentHighlightTypes != other.currentHighlightTypes) return false

      return true
    }

    override fun hashCode(): Int {
      var result = postDescriptor.hashCode()
      result = 31 * result + currentHighlightTypes.hashCode()
      return result
    }

    override fun toString(): String {
      return "PostHighlight(postDescriptor=$postDescriptor, currentHighlightTypes=$currentHighlightTypes)"
    }
  }

  enum class HighlightType(val bit: Int) {
    PostId(0),
    PostTripcode(1),
    Regular(2),
    Blink(3)
  }

}