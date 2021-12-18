package com.github.k1rakishou.chan.core.loader.impl

import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader
import com.github.k1rakishou.chan.core.loader.PostLoaderData
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.PostFilterHighlightManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.flatMapNotNull
import com.github.k1rakishou.common.toHashSetBy
import com.github.k1rakishou.model.data.filter.HighlightFilterKeyword
import com.github.k1rakishou.model.data.post.LoaderType

class PostHighlightFilterLoader(
  private val chanFilterManager: ChanFilterManager,
  private val filterEngine: FilterEngine,
  private val postFilterManager: PostFilterManager,
  private val chanThreadManager: ChanThreadManager,
  private val postFilterHighlightManager: PostFilterHighlightManager
) : OnDemandContentLoader(LoaderType.PostHighlightFilterLoader) {

  override suspend fun isCached(postLoaderData: PostLoaderData): Boolean {
    return true
  }

  override suspend fun startLoading(postLoaderData: PostLoaderData): LoaderResult {
    BackgroundUtils.ensureBackgroundThread()

    if (postFilterHighlightManager.contains(postLoaderData.postDescriptor)) {
      return rejected()
    }

    val chanPost = chanThreadManager.getPost(postLoaderData.postDescriptor)
      ?: return rejected()

    if (!postFilterManager.contains(postLoaderData.postDescriptor)) {
      return rejected()
    }

    val highlightFilters = chanFilterManager.getEnabledHighlightFilters()
    if (highlightFilters.isEmpty()) {
      return rejected()
    }

    val matchedKeywords = highlightFilters.flatMapNotNull { chanFilter ->
      val keywordsSet = mutableSetOf<HighlightFilterKeyword>()

      if (filterEngine.matches(chanFilter, chanPost.postComment.comment(), false)) {
        val keywords = filterEngine.extractMatchedKeywords(chanFilter, chanPost.postComment.comment())
        if (keywords.isNotEmpty()) {
          keywordsSet += keywords.toHashSetBy { keyword -> HighlightFilterKeyword(keyword, chanFilter.color) }
        }
      }

      if (filterEngine.matches(chanFilter, chanPost.subject, false)) {
        val keywords = filterEngine.extractMatchedKeywords(chanFilter, chanPost.subject)
        if (keywords.isNotEmpty()) {
          keywordsSet += keywords.toHashSetBy { keyword -> HighlightFilterKeyword(keyword, chanFilter.color) }
        }
      }

      if (keywordsSet.isEmpty()) {
        return@flatMapNotNull null
      }

      return@flatMapNotNull keywordsSet
    }.toSet()

    if (matchedKeywords.isEmpty()) {
      return rejected()
    }

    postFilterHighlightManager.store(postLoaderData.postDescriptor, matchedKeywords)
    // Never call setContentLoadedForLoader here because we always want to recalculate the highlights
    // against the current filters which may change any minute
    return succeeded(needUpdateView = true)
  }

  override fun cancelLoading(postLoaderData: PostLoaderData) {
    // no-op
  }

}