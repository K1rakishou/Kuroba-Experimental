package com.github.k1rakishou.chan.features.search

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach
import com.github.k1rakishou.chan.core.site.sites.search.PageCursor
import com.github.k1rakishou.chan.core.usecase.GlobalSearchUseCase
import com.github.k1rakishou.chan.features.bypass.CookieResult
import com.github.k1rakishou.chan.features.bypass.FirewallType
import com.github.k1rakishou.chan.features.bypass.SiteFirewallBypassController
import com.github.k1rakishou.chan.features.search.data.SearchParameters
import com.github.k1rakishou.chan.features.search.data.SearchResultsControllerState
import com.github.k1rakishou.chan.features.search.data.SearchResultsControllerStateData
import com.github.k1rakishou.chan.features.search.epoxy.*
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.*
import com.github.k1rakishou.chan.utils.RecyclerUtils
import com.github.k1rakishou.chan.utils.addOneshotModelBuildListener
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import okhttp3.HttpUrl
import javax.inject.Inject

class SearchResultsController(
  context: Context,
  private val siteDescriptor: SiteDescriptor,
  private val searchParameters: SearchParameters,
  private val startActivityCallback: StartActivityStartupHandlerHelper.StartActivityCallbacks
) : Controller(context), SearchResultsView, WindowInsetsListener {

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

  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    updateTitle(null)
    navigation.swipeable = false
    navigation.scrollableTitle = true

    view = inflate(context, R.layout.controller_search_results)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    epoxyRecyclerView.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS

    compositeDisposable.add(
      presenter.listenForStateChanges()
        .subscribe { state -> onStateChanged(state) }
    )

    presenter.onCreate(this)
    onInsetsChanged()

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    epoxyRecyclerView.clear()
    presenter.onDestroy()
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  override fun onBack(): Boolean {
    presenter.resetSavedState()
    presenter.resetLastRecyclerViewScrollState()
    return super.onBack()
  }

  override fun onInsetsChanged() {
    if (ChanSettings.isSplitLayoutMode()) {
      epoxyRecyclerView.updatePaddings(bottom = globalWindowInsetsManager.bottom())
    }
  }

  override fun onFirewallDetected(firewallType: FirewallType, requestUrl: HttpUrl) {
    val hostUrl = getUrlToOpen(firewallType, requestUrl)
    val timeoutMs = getBypassTimeout(firewallType, requestUrl)

    val controller = SiteFirewallBypassController(
      context = context,
      firewallType = firewallType,
      urlToOpen = hostUrl,
      timeoutMs = timeoutMs,
      onResult = { cookieResult ->
        when (cookieResult) {
          is CookieResult.CookieValue -> {
            showToast(context, getString(R.string.firewall_check_success, firewallType))
            presenter.reloadCurrentPage()

            return@SiteFirewallBypassController
          }
          is CookieResult.Error -> {
            showToast(
              context,
              getString(R.string.firewall_check_failure, firewallType, cookieResult.exception.errorMessageOrClassName())
            )
          }
          is CookieResult.Timeout -> {
            showToast(context, getString(R.string.firewall_check_timeout, firewallType, cookieResult.timeoutMs))
          }
          CookieResult.Canceled -> {
            showToast(context, getString(R.string.firewall_check_canceled, firewallType))
          }
        }
      }
    )

    presentController(controller, animated = true)
  }

  private fun getBypassTimeout(firewallType: FirewallType, requestUrl: HttpUrl): Long {
    return when (firewallType) {
      FirewallType.Cloudflare -> {
        if (requestUrl.toString().contains(SearchResultsPresenter.CHAN4_SEARCH_ENDPOINT)) {
          SiteFirewallBypassController.CHAN4_SEARCH_TIMEOUT_MS
        } else {
          SiteFirewallBypassController.DEFAULT_TIMEOUT_MS
        }
      }
      FirewallType.DvachAntiSpam -> {
        SiteFirewallBypassController.DEFAULT_TIMEOUT_MS
      }
    }
  }

  private fun getUrlToOpen(firewallType: FirewallType, requestUrl: HttpUrl) =
    when (firewallType) {
      FirewallType.Cloudflare -> {
        HttpUrl.Builder()
          .scheme("https")
          .host(requestUrl.host)
          .build()
          .toString()
      }
      FirewallType.DvachAntiSpam -> {
        Dvach.ANTI_SPAM_CHALLENGE_ENDPOINT
      }
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
    startActivityCallback.loadThreadAndMarkPost(postDescriptor, animated = true)
  }

  private fun updateTitle(totalFound: Int?) {
    when (totalFound) {
      null -> {
        navigation.title = getString(
          R.string.controller_search_searching,
          siteDescriptor.siteName,
          searchParameters.getCurrentQuery()
        )
      }
      Int.MAX_VALUE -> {
        navigation.title = getString(
          R.string.controller_search_results_unknown,
          siteDescriptor.siteName,
          searchParameters.getCurrentQuery()
        )
      }
      else -> {
        navigation.title = getString(
          R.string.controller_search_results,
          siteDescriptor.siteName,
          searchParameters.getCurrentQuery(),
          totalFound
        )
      }
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