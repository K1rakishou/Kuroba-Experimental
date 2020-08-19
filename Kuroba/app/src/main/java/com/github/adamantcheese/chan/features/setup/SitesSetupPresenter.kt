package com.github.adamantcheese.chan.features.setup

import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.core.base.BasePresenter
import com.github.adamantcheese.chan.core.manager.BoardManager
import com.github.adamantcheese.chan.core.manager.SiteManager
import com.github.adamantcheese.chan.features.setup.data.SiteCellData
import com.github.adamantcheese.chan.features.setup.data.SiteEnableState
import com.github.adamantcheese.chan.features.setup.data.SitesSetupControllerState
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.errorMessageOrClassName
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.launch
import javax.inject.Inject

class SitesSetupPresenter : BasePresenter<SitesSetupView>() {

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager

  private val addSiteControllerSubject = PublishProcessor.create<SitesSetupControllerState>()
    .toSerialized()

  override fun onCreate(view: SitesSetupView) {
    super.onCreate(view)
    Chan.inject(this)

    setState(SitesSetupControllerState.Loading)

    scope.launch {
      siteManager.awaitUntilInitialized()
      boardManager.awaitUntilInitialized()

      showSites()
    }
  }

  fun listenForStateChanges(): Flowable<SitesSetupControllerState> {
    return addSiteControllerSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error ->
        Logger.e(TAG, "Unknown error subscribed to addSiteControllerSubject.listenForStateChanges()", error)
      }
      .onErrorReturn { error -> SitesSetupControllerState.Error(error.errorMessageOrClassName()) }
      .hide()
  }

  fun onSiteEnableStateChanged(siteDescriptor: SiteDescriptor, enabled: Boolean) {
    scope.launch {
      siteManager.activateOrDeactivateSite(siteDescriptor, enabled)
      showSites()
    }
  }

  fun onSiteMoved(fromPosition: Int, toPosition: Int) {
    siteManager.onSiteMoved(fromPosition, toPosition)
    showSites()
  }

  private fun showSites() {
    val siteCellDataList = mutableListOf<SiteCellData>()

    siteManager.viewAllSitesOrdered { chanSiteData, site ->
      val siteEnableState = SiteEnableState.create(chanSiteData.active, site.enabled())

      siteCellDataList += SiteCellData(
        chanSiteData.siteDescriptor,
        site.icon().url.toString(),
        site.name(),
        siteEnableState
      )
    }

    if (siteCellDataList.isEmpty()) {
      setState(SitesSetupControllerState.Empty)
      return
    }

    setState(SitesSetupControllerState.Data(siteCellDataList))
  }

  private fun setState(stateSetup: SitesSetupControllerState) {
    addSiteControllerSubject.onNext(stateSetup)
  }

  companion object {
    private const val TAG = "SitesSetupPresenter"
  }

}