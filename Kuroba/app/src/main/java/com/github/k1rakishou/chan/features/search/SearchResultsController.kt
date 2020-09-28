package com.github.k1rakishou.chan.features.search

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.StartActivity
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.site.sites.search.PageCursor
import com.github.k1rakishou.chan.features.search.data.SearchResultsControllerState
import com.github.k1rakishou.chan.features.search.data.SearchResultsControllerStateData
import com.github.k1rakishou.chan.features.search.epoxy.*
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.AndroidUtils.dp
import com.github.k1rakishou.chan.utils.AndroidUtils.getString
import com.github.k1rakishou.chan.utils.RecyclerUtils
import com.github.k1rakishou.chan.utils.addOneshotModelBuildListener
import com.github.k1rakishou.chan.utils.plusAssign
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

class SearchResultsController(
  context: Context,
  private val siteDescriptor: SiteDescriptor,
  private val query: String
) : Controller(context), SearchResultsView {

  private lateinit var epoxyRecyclerView: EpoxyRecyclerView
  private val presenter = SearchResultsPresenter(siteDescriptor, query)

  override fun onCreate() {
    super.onCreate()

    updateTitle(null)
    navigation.swipeable = false

    view = AndroidUtils.inflate(context, R.layout.controller_search_results)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)

    compositeDisposable += presenter.listenForStateChanges()
      .subscribe { state -> onStateChanged(state) }

    presenter.onCreate(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    presenter.onDestroy()
  }

  override fun onBack(): Boolean {
    presenter.resetSavedState()
    presenter.resetLastRecyclerViewScrollState()
    return super.onBack()
  }

  private fun onStateChanged(state: SearchResultsControllerState) {
    epoxyRecyclerView.withModels {
      when (state) {
        SearchResultsControllerState.InitialLoading -> {
          epoxyLoadingView {
            id("search_result_controller_loading_view")
          }
        }
        is SearchResultsControllerState.NothingFound -> {
          epoxyTextView {
            id("search_result_controller_empty_view")
            message("Nothing was found by query \"${state.query}\"")
          }
        }
        is SearchResultsControllerState.Data -> renderDataState(state.data)
      }
    }
  }

  private fun EpoxyController.renderDataState(data: SearchResultsControllerStateData) {
    addOneshotModelBuildListener { tryRestorePreviousPosition() }

    data.searchPostInfoList.forEachIndexed { index, searchPostInfo ->
      if (index != 0 && searchPostInfo.opInfo != null) {
        epoxySearchPostGapView {
          id("epoxy_search_post_gap_view_${searchPostInfo.opInfo.hashString()}")
        }
      }

      epoxySearchPostView {
        id("epoxy_search_post_view_${searchPostInfo.combinedHash()}")
        postDescriptor(searchPostInfo.postDescriptor)
        postOpInfo(searchPostInfo.opInfo?.spannedText)
        postInfo(searchPostInfo.postInfo.spannedText)
        thumbnail(searchPostInfo.thumbnail)
        postComment(searchPostInfo.postComment.spannedText)
        onBind { _, _, _ ->
          presenter.updateLastRecyclerViewScrollState(RecyclerUtils.getIndexAndTop(epoxyRecyclerView))
        }
        onPostClickListener { postDescriptor ->
          presenter.updateLastRecyclerViewScrollState(RecyclerUtils.getIndexAndTop(epoxyRecyclerView))
          onSearchPostClicked(postDescriptor)
        }
      }

      val isNextPostOP = data.searchPostInfoList.getOrNull(index + 1)?.opInfo != null
      if (!isNextPostOP) {
        epoxySearchPostDividerView {
          id("epoxy_divider_view_$index")
          updateMargins(NEW_MARGINS)
        }
      }
    }

    if (data.errorInfo != null) {
      epoxySearchErrorView {
        id("epoxy_search_error_view")
        errorText(data.errorInfo.errorText)
        clickListener { presenter.reloadCurrentPage() }
      }

      return
    }

    if (data.nextPageCursor !is PageCursor.End) {
      epoxySearchLoadingView {
        id("epoxy_search_loading_view")
        onBind { _, _, _ -> presenter.loadNewPage(data) }
      }
    } else {
      epoxySearchEndOfResultsView {
        id("epoxy_search_end_of_results_view")
        text(getString(R.string.controller_search_results_end_of_list))
      }
    }

    updateTitle(data.currentQueryInfo?.totalFoundEntries)
  }

  private fun tryRestorePreviousPosition() {
    val indexAndTop = presenter.lastRecyclerViewScrollStateOrNull()
    if (indexAndTop == null) {
      return
    }

    (epoxyRecyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
      indexAndTop.index,
      indexAndTop.top
    )
  }

  private fun onSearchPostClicked(postDescriptor: PostDescriptor) {
    (context as? StartActivity)?.loadThread(postDescriptor)
  }

  private fun updateTitle(totalFound: Int?) {
    if (totalFound == null) {
      navigation.title = getString(
        R.string.controller_search_searching,
        siteDescriptor.siteName,
        query
      )
    } else {
      navigation.title = AndroidUtils.getString(
        R.string.controller_search_results,
        siteDescriptor.siteName,
        query,
        totalFound
      )
    }

    requireNavController().requireToolbar().updateTitle(navigation)
  }

  companion object {
    private val DIVIDER_VERTICAL_MARGINS = dp(8f)
    private val DIVIDER_HORIZONTAL_MARGINS = dp(4f)

    private val NEW_MARGINS = EpoxySearchPostDividerView.NewMargins(
      top = DIVIDER_VERTICAL_MARGINS,
      bottom = DIVIDER_VERTICAL_MARGINS,
      left = DIVIDER_HORIZONTAL_MARGINS,
      right = DIVIDER_HORIZONTAL_MARGINS
    )
  }
}