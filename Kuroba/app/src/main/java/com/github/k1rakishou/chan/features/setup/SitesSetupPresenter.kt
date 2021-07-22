package com.github.k1rakishou.chan.features.setup

import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.setup.data.ArchiveEnabledTotalCount
import com.github.k1rakishou.chan.features.setup.data.SiteCellArchiveGroupInfo
import com.github.k1rakishou.chan.features.setup.data.SiteCellData
import com.github.k1rakishou.chan.features.setup.data.SiteEnableState
import com.github.k1rakishou.chan.features.setup.data.SitesSetupControllerState
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SitesSetupPresenter(
  private val siteManager: SiteManager,
  private val archivesManager: ArchivesManager
) : BasePresenter<SitesSetupView>() {

  private val stateSubject = BehaviorProcessor.create<SitesSetupControllerState>()
  private val expandedGroups = mutableSetOf<SiteDescriptor>()

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

  fun toggleGroupCollapseState(siteDescriptor: SiteDescriptor) {
    if (expandedGroups.contains(siteDescriptor)) {
      expandedGroups.remove(siteDescriptor)
    } else {
      expandedGroups.add(siteDescriptor)
    }

    showSites()
  }

  private fun showSites() {
    val siteCellDataList = mutableListOf<SiteCellData>()
    val archiveCellDataMap = mutableMapOf<SiteDescriptor, MutableList<SiteCellData>>()

    siteManager.viewSitesOrdered { chanSiteData, site ->
      val siteEnableState = SiteEnableState.create(chanSiteData.active, site.enabled())
      val isArchive = archivesManager.isSiteArchive(site.siteDescriptor())

      if (!isArchive) {
        siteCellDataList += SiteCellData(
          chanSiteData.siteDescriptor,
          site.icon().url.toString(),
          site.name(),
          siteEnableState,
          null
        )

        return@viewSitesOrdered true
      }

      val sites = archivesManager.getSupportedSites(site.siteDescriptor())
      sites.forEach { siteDescriptor ->
        val archiveSiteCellData = SiteCellData(
          chanSiteData.siteDescriptor,
          site.icon().url.toString(),
          site.name(),
          siteEnableState,
          null
        )

        archiveCellDataMap.putIfNotContains(siteDescriptor, mutableListOf())
        archiveCellDataMap[siteDescriptor]!!.add(archiveSiteCellData)
      }

      return@viewSitesOrdered true
    }

    val groupedSiteCellDataList = groupSitesWithArchives(siteCellDataList, archiveCellDataMap)
    if (groupedSiteCellDataList.isEmpty()) {
      setState(SitesSetupControllerState.Empty)
      return
    }

    setState(SitesSetupControllerState.Data(groupedSiteCellDataList))
  }

  private fun groupSitesWithArchives(
    siteCellDataList: MutableList<SiteCellData>,
    archiveCellDataMap: MutableMap<SiteDescriptor, MutableList<SiteCellData>>
  ): List<SiteCellData> {
    val resultList = mutableListOf<SiteCellData>()

    siteCellDataList.forEach { siteCellData ->
      val isExpanded = siteCellData.siteDescriptor in expandedGroups

      val siteCellArchiveGroupInfo = if (archiveCellDataMap.containsKey(siteCellData.siteDescriptor)) {
        val archives = archiveCellDataMap[siteCellData.siteDescriptor]!!

        SiteCellArchiveGroupInfo(
          archives = archives,
          isGroupExpanded = isExpanded,
          archiveEnabledTotalCount = ArchiveEnabledTotalCount(
            enabledCount = archives.count { archive -> archive.siteEnableState == SiteEnableState.Active },
            totalCount = archives.size
          )
        )
      } else {
        null
      }

      resultList += SiteCellData(
        siteDescriptor = siteCellData.siteDescriptor,
        siteIcon = siteCellData.siteIcon,
        siteName = siteCellData.siteName,
        siteEnableState = siteCellData.siteEnableState,
        siteCellArchiveGroupInfo = siteCellArchiveGroupInfo
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