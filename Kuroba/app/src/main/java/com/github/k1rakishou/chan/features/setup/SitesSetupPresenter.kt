package com.github.k1rakishou.chan.features.setup

import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.setup.data.SiteCellData
import com.github.k1rakishou.chan.features.setup.data.SiteEnableState
import com.github.k1rakishou.chan.features.setup.data.SitesSetupControllerState
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class SitesSetupPresenter : BasePresenter<SitesSetupView>() {

  @Inject
  lateinit var siteManager: SiteManager

  private val stateSubject = PublishProcessor.create<SitesSetupControllerState>()
    .toSerialized()

  override fun onCreate(view: SitesSetupView) {
    super.onCreate(view)
    Chan.inject(this)

    setState(SitesSetupControllerState.Loading)

    scope.launch(Dispatchers.Default) {
      siteManager.awaitUntilInitialized()

      showSites()
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

  fun onSiteMoved(siteDescriptor: SiteDescriptor, fromPosition: Int, toPosition: Int) {
    if (siteManager.onSiteMoved(siteDescriptor, fromPosition, toPosition)) {
      showSites()
    }
  }

  private fun showSites() {
    val siteCellDataList = mutableListOf<SiteCellData>()

    siteManager.viewSitesOrdered { chanSiteData, site ->
      val siteEnableState = SiteEnableState.create(chanSiteData.active, site.enabled())

      siteCellDataList += SiteCellData(
        chanSiteData.siteDescriptor,
        site.icon().url.toString(),
        site.name(),
        siteEnableState
      )

      return@viewSitesOrdered true
    }

    if (siteCellDataList.isEmpty()) {
      setState(SitesSetupControllerState.Empty)
      return
    }

    setState(SitesSetupControllerState.Data(siteCellDataList))
  }

  private fun setState(stateSetup: SitesSetupControllerState) {
    stateSubject.onNext(stateSetup)
  }

  companion object {
    private const val TAG = "SitesSetupPresenter"
  }

}