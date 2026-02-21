package com.github.k1rakishou.chan.features.setup.site.setup

import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.setup.data.SiteCellData
import com.github.k1rakishou.chan.features.setup.data.SiteEnableState
import com.github.k1rakishou.chan.features.setup.data.SitesSetupControllerState
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SitesSetupPresenter(
  private val siteManager: SiteManager
) : BasePresenter<SitesSetupView>() {

  private val stateSubject = BehaviorProcessor.create<SitesSetupControllerState>()

  override fun onCreate(view: SitesSetupView) {
    super.onCreate(view)

    presenterScope.launch(Dispatchers.Default) {
      val loadingJob = presenterScope.launch {
        delay(50)
        setState(SitesSetupControllerState.Loading)
      }

      siteManager.awaitUntilInitialized()

      showSites()
      loadingJob.cancel()
    }
  }

  fun listenForStateChanges(): Flowable<SitesSetupControllerState> {
    return stateSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error ->
        Logger.e(TAG, "Unknown error subscribed to stateSubject.listenForStateChanges()", error)
      }
      .onErrorReturn { error -> SitesSetupControllerState.Error(error.errorMessageOrClassName()) }
      .hide()
  }

  fun onSiteEnableStateChanged(siteDescriptor: SiteDescriptor, enabled: Boolean) {
    presenterScope.launch {
      if (siteManager.activateOrDeactivateSite(siteDescriptor, enabled)) {
        showSites()
      }
    }
  }

  fun onSiteMoving(fromSiteDescriptor: SiteDescriptor, toSiteDescriptor: SiteDescriptor) {
    siteManager.onSiteMoving(fromSiteDescriptor, toSiteDescriptor)
    showSites()
  }

  fun onSiteMoved() {
    siteManager.onSiteMoved()
  }

  private fun showSites() {
    val allSites = mutableListOf<SiteCellData>()
    val disabledSites = mutableListOf<SiteCellData>()

    siteManager.viewSitesOrdered { chanSiteData, site ->
      val siteEnableState = SiteEnableState.create(
        active = chanSiteData.active,
        enabled = site.enabled()
      )

      val siteCellData = SiteCellData(
        siteDescriptor = chanSiteData.siteDescriptor,
        siteIcon = site.icon(),
        siteName = site.name(),
        siteEnableState = siteEnableState
      )

      if (site.enabled()) {
        allSites += siteCellData
      } else {
        disabledSites += siteCellData
      }

      return@viewSitesOrdered true
    }

    // Put disabled sites at the end of the list
    allSites.addAll(disabledSites)

    if (allSites.isEmpty()) {
      setState(SitesSetupControllerState.Empty)
      return
    }

    setState(SitesSetupControllerState.Data(allSites))
  }

  private fun setState(stateSetup: SitesSetupControllerState) {
    stateSubject.onNext(stateSetup)
  }

  companion object {
    private const val TAG = "SitesSetupPresenter"
  }

}