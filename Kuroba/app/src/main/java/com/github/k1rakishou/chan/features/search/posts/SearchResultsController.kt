package com.github.k1rakishou.chan.features.search.posts

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.site.sites.search.PageCursor
import com.github.k1rakishou.chan.core.usecase.GlobalSearchUseCase
import com.github.k1rakishou.chan.features.search.posts.data.SearchParameters
import com.github.k1rakishou.chan.features.search.posts.data.SearchResultsControllerState
import com.github.k1rakishou.chan.features.search.posts.data.SearchResultsControllerStateData
import com.github.k1rakishou.chan.features.search.posts.epoxy.EpoxySearchPostDividerView
import com.github.k1rakishou.chan.features.search.posts.epoxy.epoxySearchEndOfResultsView
import com.github.k1rakishou.chan.features.search.posts.epoxy.epoxySearchErrorView
import com.github.k1rakishou.chan.features.search.posts.epoxy.epoxySearchLoadingView
import com.github.k1rakishou.chan.features.search.posts.epoxy.epoxySearchPostDividerView
import com.github.k1rakishou.chan.features.search.posts.epoxy.epoxySearchPostView
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.view.insets.InsetAwareEpoxyRecyclerView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.RecyclerUtils
import com.github.k1rakishou.chan.utils.addOneshotModelBuildListener
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import javax.inject.Inject

class SearchResultsController(
  context: Context,
  private val siteDescriptor: SiteDescriptor,
  private val searchParameters: SearchParameters,
  private val startActivityCallback: StartActivityStartupHandlerHelper.StartActivityCallbacks,
  private val onSearchResultClicked: (PostDescriptor) -> Unit
) : Controller(context), SearchResultsView {

  @Inject
  lateinit var globalSearchUseCase: GlobalSearchUseCase
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val presenter by lazy {
    SearchResultsPresenter(
      siteDescriptor = siteDescriptor,
      searchParameters = searchParameters,
      globalSearchUseCase = globalSearchUseCase,
      themeEngine = themeEngine
    )
  }

  private lateinit var epoxyRecyclerView: InsetAwareEpoxyRecyclerView

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags()
    )

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      scrollableTitle = true,
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.String("")
      )
    )

    updateTitle(null)

    view = inflate(context, R.layout.controller_search_results)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    epoxyRecyclerView.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS

    compositeDisposable.add(
      presenter.listenForStateChanges()
        .subscribe { state -> onStateChanged(state) }
    )

    presenter.onCreate(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    epoxyRecyclerView.clear()
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
        SearchResultsControllerState.Uninitialized,
        SearchResultsControllerState.Loading -> {
          epoxyLoadingView {
            id("epoxy_loading_view")
          }
        }
        is SearchResultsControllerState.NothingFound -> {
          epoxyTextView {
            id("search_result_controller_empty_view")
            message("Nothing was found by query \"${state.searchParameters.getCurrentQuery()}\"")
          }
        }
        is SearchResultsControllerState.Data -> renderDataState(state.data)
      }
    }
  }

  private fun EpoxyController.renderDataState(data: SearchResultsControllerStateData) {
    addOneshotModelBuildListener { tryRestorePreviousPosition() }

    data.searchPostInfoList.forEachIndexed { index, searchPostInfo ->
      epoxySearchPostView {
        id("epoxy_search_post_view_${searchPostInfo.postDescriptor.serializeToString()}}")
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

      epoxySearchPostDividerView {
        id("epoxy_divider_view_${searchPostInfo.postDescriptor.serializeToString()}")
        updateMargins(NEW_MARGINS)
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

    val nextPageCursor = data.nextPageCursor
    if (nextPageCursor !is PageCursor.End) {
      val searchLoadingViewId = if (nextPageCursor is PageCursor.Empty) {
        "epoxy_search_loading_view_initial"
      } else {
        val page = nextPageCursor as PageCursor.Page
        "epoxy_search_loading_view_${page.value}"
      }

      epoxySearchLoadingView {
        id(searchLoadingViewId)
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
      ?: return

    (epoxyRecyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
      indexAndTop.index,
      indexAndTop.top
    )
  }

  private fun onSearchPostClicked(postDescriptor: PostDescriptor) {
    withLayoutMode(
      phone = {
        requireNavController().popController {
          onSearchResultClicked(postDescriptor)
        }
      },
      tablet = {
        startActivityCallback.loadThreadAndMarkPost(
          postDescriptor = postDescriptor,
          animated = true
        )
      }
    )
  }

  private fun updateTitle(totalFound: Int?) {
    val title = when (totalFound) {
      null -> {
        getString(
          R.string.controller_search_searching,
          siteDescriptor.siteName,
          searchParameters.getCurrentQuery()
        )
      }
      Int.MAX_VALUE -> {
        getString(
          R.string.controller_search_results_unknown,
          siteDescriptor.siteName,
          searchParameters.getCurrentQuery()
        )
      }
      else -> {
        getString(
          R.string.controller_search_results_ok,
          siteDescriptor.siteName,
          searchParameters.getCurrentQuery(),
          totalFound
        )
      }
    }

    toolbarState.default.updateTitle(newTitle = ToolbarText.String(title))
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