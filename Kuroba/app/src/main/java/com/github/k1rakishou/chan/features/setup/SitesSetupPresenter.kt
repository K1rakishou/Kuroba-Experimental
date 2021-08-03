package com.github.k1rakishou.chan.features.setup

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

    scope.launch(Dispatchers.Default) {
      val loadingJob = scope.launch {
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
    scope.launch {
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
    val siteCellDataList = mutableListOf<SiteCellData>()

    siteManager.viewSitesOrdered { chanSiteData, site ->
      val siteEnableState = SiteEnableState.create(chanSiteData.active, site.enabled())
      siteCellDataList += SiteCellData(
        siteDescriptor = chanSiteData.siteDescriptor,
        siteIcon = site.icon().url.toString(),
        siteName = site.name(),
        siteEnableState = siteEnableState
      )

      return@viewSitesOrdered true
    }

    val groupedSiteCellDataList = groupSitesWithArchives(siteCellDataList)
    if (groupedSiteCellDataList.isEmpty()) {
      setState(SitesSetupControllerState.Empty)
      return
    }

    setState(SitesSetupControllerState.Data(groupedSiteCellDataList))
  }

  private fun groupSitesWithArchives(siteCellDataList: MutableList<SiteCellData>): List<SiteCellData> {
    val resultList = mutableListOf<SiteCellData>()

    siteCellDataList.forEach { siteCellData ->
      resultList += SiteCellData(
        siteDescriptor = siteCellData.siteDescriptor,
        siteIcon = siteCellData.siteIcon,
        siteName = siteCellData.siteName,
        siteEnableState = siteCellData.siteEnableState
      )
    }

    return resultList
  }

  private fun setState(stateSetup: SitesSetupControllerState) {
    stateSubject.onNext(stateSetup)
  }

  companion object {
    private const val TAG = "SitesSetupPresenter"
  }

}