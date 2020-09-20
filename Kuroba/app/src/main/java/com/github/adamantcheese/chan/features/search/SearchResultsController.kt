package com.github.adamantcheese.chan.features.search

import android.content.Context
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.StartActivity
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.site.sites.search.PageCursor
import com.github.adamantcheese.chan.features.search.data.SearchResultsControllerState
import com.github.adamantcheese.chan.features.search.data.SearchResultsControllerStateData
import com.github.adamantcheese.chan.features.search.epoxy.*
import com.github.adamantcheese.chan.ui.epoxy.EpoxyDividerView
import com.github.adamantcheese.chan.ui.epoxy.epoxyDividerView
import com.github.adamantcheese.chan.ui.epoxy.epoxyLoadingView
import com.github.adamantcheese.chan.ui.epoxy.epoxyTextView
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.AndroidUtils.dp
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import com.github.adamantcheese.chan.utils.plusAssign
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor

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
        onPostClickListener { postDescriptor -> onSearchPostClicked(postDescriptor) }
      }

      val isNextPostOP = data.searchPostInfoList.getOrNull(index + 1)?.opInfo != null
      if (!isNextPostOP) {
        epoxyDividerView {
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

  private fun onSearchPostClicked(postDescriptor: PostDescriptor) {
    // TODO(KurobaEx): remember current state and restore it the next time GlobalSearchController
    //  is opened
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

    private val NEW_MARGINS = EpoxyDividerView.NewMargins(
      top = DIVIDER_VERTICAL_MARGINS,
      bottom = DIVIDER_VERTICAL_MARGINS,
      left = DIVIDER_HORIZONTAL_MARGINS,
      right = DIVIDER_HORIZONTAL_MARGINS
    )
  }
}