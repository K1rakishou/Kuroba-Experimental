package com.github.k1rakishou.chan.features.search

import android.content.Context
import android.view.View
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.core.site.sites.search.SearchBoard
import com.github.k1rakishou.chan.core.site.sites.search.SiteGlobalSearchType
import com.github.k1rakishou.chan.features.search.data.GlobalSearchControllerState
import com.github.k1rakishou.chan.features.search.data.GlobalSearchControllerStateData
import com.github.k1rakishou.chan.features.search.data.SearchParameters
import com.github.k1rakishou.chan.features.search.data.SitesWithSearch
import com.github.k1rakishou.chan.features.search.epoxy.epoxyBoardSelectionButtonView
import com.github.k1rakishou.chan.features.search.epoxy.epoxyButtonView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchInputView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchSiteView
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import java.lang.ref.WeakReference
import javax.inject.Inject

class GlobalSearchController(
  context: Context,
  private val startActivityCallback: StartActivityStartupHandlerHelper.StartActivityCallbacks
) : Controller(context), GlobalSearchView, WindowInsetsListener, ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val presenter by lazy { GlobalSearchPresenter(siteManager, themeEngine) }

  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView

  private val inputViewRefSet = mutableListOf<WeakReference<View>>()
  private var resetSearchParameters = false

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    navigation.title = getString(R.string.controller_search)
    navigation.swipeable = false
    navigation.hasDrawer = true
    navigation.hasBack = false

    view = inflate(context, R.layout.controller_global_search)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)

    compositeDisposable.add(
      presenter.listenForStateChanges()
        .subscribe { state -> onStateChanged(state) }
    )

    presenter.onCreate(this)
    onInsetsChanged()

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
    themeEngine.addListener(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    epoxyRecyclerView.clear()
    inputViewRefSet.clear()
    presenter.onDestroy()

    themeEngine.removeListener(this)
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  override fun onBack(): Boolean {
    resetSearchParameters = true
    presenter.resetSavedState()
    return super.onBack()
  }

  override fun onThemeChanged() {
    presenter.reloadCurrentState()
  }

  override fun onInsetsChanged() {
    val bottomPaddingDp = calculateBottomPaddingForRecyclerInDp(
      globalWindowInsetsManager = globalWindowInsetsManager,
      mainControllerCallbacks = null
    )

    epoxyRecyclerView.updatePaddings(bottom = dp(bottomPaddingDp.toFloat()))
  }

  override fun updateResetSearchParametersFlag(reset: Boolean) {
    resetSearchParameters = reset
  }

  override fun restoreSearchResultsController(
    siteDescriptor: SiteDescriptor,
    searchParameters: SearchParameters
  ) {
    hideKeyboard()

    requireNavController().pushController(
      SearchResultsController(context, siteDescriptor, searchParameters, startActivityCallback),
      false
    )
  }

  override fun openSearchResultsController(
    siteDescriptor: SiteDescriptor,
    searchParameters: SearchParameters
  ) {
    hideKeyboard()

    presenter.resetSearchResultsSavedState()
    requireNavController().pushController(
      SearchResultsController(context, siteDescriptor, searchParameters, startActivityCallback)
    )
  }

  private fun onStateChanged(state: GlobalSearchControllerState) {
    epoxyRecyclerView.withModels {
      when (state) {
        GlobalSearchControllerState.Uninitialized -> {
          // no-op
        }
        GlobalSearchControllerState.Loading -> {
          epoxyLoadingView {
            id("global_search_loading_view")
          }
        }
        GlobalSearchControllerState.Empty -> {
          epoxyTextView {
            id("global_search_empty_text_view")
            message(context.getString(R.string.controller_search_empty_sites))
          }
        }
        is GlobalSearchControllerState.Error -> {
          epoxyErrorView {
            id("global_search_error_view")
            errorMessage(state.errorText)
          }
        }
        is GlobalSearchControllerState.Data -> onDataStateChanged(state.data)
      }
    }
  }

  private fun EpoxyController.onDataStateChanged(dataState: GlobalSearchControllerStateData) {
    epoxySearchSiteView {
      id("global_search_epoxy_site")
      bindSiteName(dataState.sitesWithSearch.selectedSite.siteDescriptor.siteName)
      bindIconUrl(dataState.sitesWithSearch.selectedSite.siteIconUrl)
      itemBackgroundColor(themeEngine.chanTheme.backColor)
      bindClickCallback {
        val controller = SelectSiteForSearchController(
          context = context,
          selectedSite = dataState.sitesWithSearch.selectedSite.siteDescriptor,
          onSiteSelected = { selectedSiteDescriptor -> presenter.onSearchSiteSelected(selectedSiteDescriptor) }
        )

        requireNavController().presentController(controller)
      }
    }

    val selectedSite = dataState.sitesWithSearch.selectedSite

    val canRenderSearchButton = when (selectedSite.siteGlobalSearchType) {
      SiteGlobalSearchType.SimpleQuerySearch,
      SiteGlobalSearchType.SimpleQueryBoardSearch -> {
        renderSimpleQuerySearch(dataState, selectedSite.siteGlobalSearchType)
      }
      SiteGlobalSearchType.FuukaSearch,
      SiteGlobalSearchType.FoolFuukaSearch -> {
        renderFoolFuukaSearch(dataState)
      }
      SiteGlobalSearchType.SearchNotSupported -> false
    }

    if (!canRenderSearchButton) {
      return
    }

    renderSearchButton(dataState.sitesWithSearch, dataState.searchParameters)
  }

  private fun EpoxyController.renderFoolFuukaSearch(dataState: GlobalSearchControllerStateData): Boolean {
    val sitesWithSearch = dataState.sitesWithSearch
    val searchParameters = dataState.searchParameters as SearchParameters.AdvancedSearchParameters
    val selectedSiteDescriptor = sitesWithSearch.selectedSite.siteDescriptor

    // When site selection changes with want to redraw all epoxySearchInputViews with new initialQueries
    val selectedSiteName = selectedSiteDescriptor.siteName

    var initialQuery = searchParameters.query
    var initialSubjectQuery = searchParameters.subject
    var selectedBoard = searchParameters.searchBoard
    var selectedBoardCode = selectedBoard?.boardCode()

    fun createFuukaOrFoolFuukaSearchParams(
      siteDescriptor: SiteDescriptor,
      query: String,
      subject: String,
      searchBoard: SearchBoard?,
    ): SearchParameters.AdvancedSearchParameters? {
      val searchType = siteManager.bySiteDescriptor(siteDescriptor)?.siteGlobalSearchType()
        ?: return null

      when (searchType) {
        SiteGlobalSearchType.SearchNotSupported,
        SiteGlobalSearchType.SimpleQuerySearch,
        SiteGlobalSearchType.SimpleQueryBoardSearch,
        SiteGlobalSearchType.FuukaSearch -> {
          return SearchParameters.FuukaSearchParameters(
            query = query,
            subject = subject,
            searchBoard = searchBoard
          )
        }
        SiteGlobalSearchType.FoolFuukaSearch -> {
          return SearchParameters.FoolFuukaSearchParameters(
            query = query,
            subject = subject,
            searchBoard = searchBoard
          )
        }
      }
    }

    if (resetSearchParameters) {
      initialQuery = ""
      initialSubjectQuery = ""
      selectedBoard = null
      selectedBoardCode = null

      resetSearchParameters = false
    }

    epoxyBoardSelectionButtonView {
      id("global_search_board_selection_button_view_$selectedSiteName")
      boardCode(selectedBoardCode)
      bindClickCallback {
        val boardsSupportingSearch = getFoolFuukaBoardsSupportingSearch(selectedSiteDescriptor)
        if (boardsSupportingSearch.isEmpty()) {
          showToast(R.string.no_boards_supporting_search_found)
          return@bindClickCallback
        }

        val controller = SelectBoardForSearchController(
          context = context,
          siteDescriptor = selectedSiteDescriptor,
          supportsAllBoardsSearch = false,
          prevSelectedBoard = searchParameters.searchBoard,
          searchBoardProvider = { boardsSupportingSearch },
          onBoardSelected = { searchBoard ->
            val updatedSearchParameters = createFuukaOrFoolFuukaSearchParams(
              siteDescriptor = selectedSiteDescriptor,
              query = initialQuery,
              subject = initialSubjectQuery,
              searchBoard = searchBoard
            )

            presenter.reloadWithSearchParameters(updatedSearchParameters, sitesWithSearch)
          }
        )

        requireNavController().presentController(controller)
      }
    }

    epoxySearchInputView {
      id("global_search_fool_fuuka_search_input_comment_subject_view_$selectedSiteName")
      initialQuery(initialSubjectQuery)
      hint(context.getString(R.string.post_subject_search_query_hint))
      onTextEnteredListener { subjectQuery ->
        val updatedSearchParameters = createFuukaOrFoolFuukaSearchParams(
          siteDescriptor = selectedSiteDescriptor,
          query = initialQuery,
          subject = subjectQuery,
          searchBoard = selectedBoard
        )

        presenter.reloadWithSearchParameters(updatedSearchParameters, sitesWithSearch)
      }
      onBind { _, view, _ -> addViewToInputViewRefSet(view) }
      onUnbind { _, view -> removeViewFromInputViewRefSet(view) }
    }

    epoxySearchInputView {
      id("global_search_fool_fuuka_search_input_comment_query_view_$selectedSiteName")
      initialQuery(initialQuery)
      hint(context.getString(R.string.post_comment_search_query_hint))
      onTextEnteredListener { commentQuery ->
        val updatedSearchParameters = createFuukaOrFoolFuukaSearchParams(
          siteDescriptor = selectedSiteDescriptor,
          query = commentQuery,
          subject = initialSubjectQuery,
          searchBoard = selectedBoard
        )

        presenter.reloadWithSearchParameters(updatedSearchParameters, sitesWithSearch)
      }
      onBind { _, view, _ -> addViewToInputViewRefSet(view) }
      onUnbind { _, view -> removeViewFromInputViewRefSet(view) }
    }

    val newSearchParameters = createFuukaOrFoolFuukaSearchParams(
      siteDescriptor = selectedSiteDescriptor,
      query = initialQuery,
      subject = initialSubjectQuery,
      searchBoard = selectedBoard
    )

    if (newSearchParameters == null) {
      return false
    }

    return newSearchParameters.isValid()
  }

  private fun getFoolFuukaBoardsSupportingSearch(selectedSiteDescriptor: SiteDescriptor): List<SearchBoard> {
    val isFoolFuukaArchive = archivesManager.bySiteDescriptor(selectedSiteDescriptor)
      ?.archiveType
      ?.isFoolFuukaArchive()

    if (isFoolFuukaArchive == true) {
      return boardManager.getAllBoardDescriptorsForSite(selectedSiteDescriptor)
        .toList()
        .sortedBy { boardDescriptor -> boardDescriptor.boardCode }
        .map { boardDescriptor -> SearchBoard.SingleBoard(boardDescriptor) }
    }

    val isFuukaArchive = archivesManager.bySiteDescriptor(selectedSiteDescriptor)
      ?.archiveType
      ?.isFuukaArchive()

    if (isFuukaArchive == true) {
      return archivesManager.getBoardsSupportingSearch(selectedSiteDescriptor)
        .toList()
        .sortedBy { boardDescriptor -> boardDescriptor.boardCode }
        .map { boardDescriptor -> SearchBoard.SingleBoard(boardDescriptor) }
    }

    return emptyList()
  }

  private fun EpoxyController.renderSimpleQuerySearch(
    dataState: GlobalSearchControllerStateData,
    siteGlobalSearchType: SiteGlobalSearchType
  ): Boolean {
    val sitesWithSearch = dataState.sitesWithSearch
    val searchParameters = dataState.searchParameters as SearchParameters.SimpleQuerySearchParameters
    val selectedSiteDescriptor = sitesWithSearch.selectedSite.siteDescriptor

    var searchQuery = searchParameters.query
    var selectedBoard = searchParameters.searchBoard
    var selectedBoardCode: String? = selectedBoard?.boardCode()

    // When site selection changes with want to redraw epoxySearchInputView with new initialQuery
    val selectedSiteName = sitesWithSearch.selectedSite.siteDescriptor.siteName

    if (resetSearchParameters) {
      searchQuery = ""

      selectedBoard = if (searchParameters is SearchParameters.DvachSearchParams) {
        null
      } else {
        SearchBoard.AllBoards
      }

      selectedBoardCode = selectedBoard?.boardCode()

      resetSearchParameters = false
    }

    epoxySearchInputView {
      id("global_search_simple_query_search_view_$selectedSiteName")
      initialQuery(searchQuery)
      hint(context.getString(R.string.post_comment_search_query_hint))
      onTextEnteredListener { query ->
        val updatedSearchParameters = searchParameters.update(query, selectedBoard)
        presenter.reloadWithSearchParameters(updatedSearchParameters, sitesWithSearch)
      }
      onBind { _, view, _ -> addViewToInputViewRefSet(view) }
      onUnbind { _, view -> removeViewFromInputViewRefSet(view) }
    }

    if (siteGlobalSearchType == SiteGlobalSearchType.SimpleQueryBoardSearch) {
      epoxyBoardSelectionButtonView {
        id("global_search_board_selection_button_view_$selectedSiteName")
        boardCode(selectedBoardCode)
        bindClickCallback {
          val boardsSupportingSearch = boardManager.getAllBoardDescriptorsForSite(selectedSiteDescriptor)
            .sortedBy { bd -> bd.boardCode }
            .map { boardDescriptor -> SearchBoard.SingleBoard(boardDescriptor) }

          if (boardsSupportingSearch.isEmpty()) {
            showToast(R.string.no_boards_supporting_search_found)
            return@bindClickCallback
          }

          val controller = SelectBoardForSearchController(
            context = context,
            siteDescriptor = selectedSiteDescriptor,
            supportsAllBoardsSearch = searchParameters.supportsAllBoardsSearch,
            prevSelectedBoard = searchParameters.searchBoard,
            searchBoardProvider = { boardsSupportingSearch },
            onBoardSelected = { searchBoard ->
              val updatedSearchParameters = searchParameters.update(
                query = searchQuery,
                selectedBoard = searchBoard
              )

              presenter.reloadWithSearchParameters(updatedSearchParameters, sitesWithSearch)
            }
          )

          requireNavController().presentController(controller)
        }
      }
    }

    return searchParameters.update(
      query = searchQuery,
      selectedBoard = selectedBoard
    ).isValid()
  }

  private fun EpoxyController.renderSearchButton(
    sitesWithSearch: SitesWithSearch,
    searchParameters: SearchParameters
  ) {
    epoxyButtonView {
      id("global_search_button_view")
      title(getString(R.string.search_hint))
      onButtonClickListener {
        presenter.onSearchButtonClicked(sitesWithSearch.selectedSite, searchParameters)
      }
    }
  }

  private fun addViewToInputViewRefSet(view: View) {
    val alreadyAdded = inputViewRefSet.any { viewRef ->
      return@any viewRef.get() === view
    }

    if (alreadyAdded) {
      return
    }

    inputViewRefSet.add(WeakReference(view))
  }

  private fun removeViewFromInputViewRefSet(view: View) {
    inputViewRefSet.removeAll { viewRef -> viewRef.get() === view }
  }

  private fun hideKeyboard() {
    inputViewRefSet.forEach { viewRef ->
      viewRef.get()?.let { inputView ->
        if (inputView.hasFocus()) {
          AndroidUtils.hideKeyboard(inputView)
        }
      }
    }
  }
}