package com.github.adamantcheese.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.core.base.SuspendDebouncer
import com.github.adamantcheese.chan.core.settings.json.JsonSettings
import com.github.adamantcheese.chan.core.site.Site
import com.github.adamantcheese.chan.core.site.SiteRegistry
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.SuspendableInitializer
import com.github.adamantcheese.common.mutableListWithCap
import com.github.adamantcheese.common.mutableMapWithCap
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.github.adamantcheese.model.data.site.ChanSiteData
import com.github.adamantcheese.model.repository.SiteRepository
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class SiteManager(
  private val appScope: CoroutineScope,
  private val isDevFlavor: Boolean,
  private val verboseLogsEnabled: Boolean,
  private val siteRepository: SiteRepository,
  private val siteRegistry: SiteRegistry
) {
  private val suspendableInitializer = SuspendableInitializer<Unit>("SiteManager")
  private val updateOrdersDebouncer = SuspendDebouncer(appScope)

  private val sitesChangedSubject = PublishProcessor.create<Unit>()

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val siteDataMap = mutableMapWithCap<SiteDescriptor, ChanSiteData>(128)
  @GuardedBy("lock")
  private val siteMap = mutableMapWithCap<SiteDescriptor, Site>(128)
  @GuardedBy("lock")
  private val orders = mutableListWithCap<SiteDescriptor>(128)

  fun loadSites() {
    appScope.launch {
      val result = siteRepository.initialize(siteRegistry.SITE_CLASSES_MAP.keys)
      if (result is ModularResult.Error) {
        suspendableInitializer.initWithError(result.error)
        return@launch
      }

      result as ModularResult.Value

      lock.write {
        result.value.forEach { chanSiteData ->
          siteDataMap[chanSiteData.siteDescriptor] = chanSiteData
          siteMap[chanSiteData.siteDescriptor] = instantiateSite(chanSiteData)

          orders.add(0, chanSiteData.siteDescriptor)
        }
      }

      ensureSitesAndOrdersConsistency()
      suspendableInitializer.initWithValue(Unit)
    }
  }

  fun listenForSitesChanges(): Flowable<Unit> {
    return sitesChangedSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error -> Logger.e(TAG, "Error while listening for sitesChangedSubject updates", error) }
      .hide()
  }

  suspend fun activateOrDeactivateSite(siteDescriptor: SiteDescriptor, activate: Boolean) {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }

    val chanSiteData = lock.read { siteDataMap[siteDescriptor] }
      ?: return

    if (chanSiteData.active == activate) {
      return
    }

    ensureSitesAndOrdersConsistency()
    lock.write { chanSiteData.active = activate }

    siteRepository.persist(listOf(chanSiteData))
      .peekError { error -> Logger.e(TAG, "Failed to persist ChanSiteData", error) }
      .ignore()

    sitesChanged()
  }

  fun bySiteDescriptor(siteDescriptor: SiteDescriptor): Site? {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }

    return lock.read { siteMap[siteDescriptor] }
  }

  fun viewAllSitesOrdered(viewer: (ChanSiteData, Site) -> Unit) {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }

    lock.read {
      orders.forEach { siteDescriptor ->
        val chanSiteData = requireNotNull(siteDataMap[siteDescriptor]) {
          "Couldn't find chanSiteData by siteDescriptor: $siteDescriptor in orders"
        }

        val site = requireNotNull(siteMap[siteDescriptor]) {
          "Couldn't find site by siteDescriptor: $siteDescriptor"
        }

        viewer(chanSiteData, site)
      }
    }
  }

  fun onSiteMoved(from: Int, to: Int) {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }

    require(from >= 0) { "Bad from: $from" }
    require(to >= 0) { "Bad to: $to" }

    ensureSitesAndOrdersConsistency()
    lock.write { orders.add(to, orders.removeAt(from)) }

    updateOrdersDebouncer.post(500) { siteRepository.persist(getSitesOrdered()) }
    sitesChanged()
  }

  @OptIn(ExperimentalTime::class)
  suspend fun awaitUntilInitialized() {
    if (isReady()) {
      return
    }

    Logger.d(TAG, "SiteManager is not ready yet, waiting...")
    val duration = measureTime { suspendableInitializer.awaitUntilInitialized() }
    Logger.d(TAG, "SiteManager initialization completed, took $duration")
  }

  private fun sitesChanged() {
    if (isDevFlavor) {
      ensureSitesAndOrdersConsistency()
    }

    sitesChangedSubject.onNext(Unit)
  }

  private fun getSitesOrdered(): List<ChanSiteData> {
    return lock.read {
      return@read orders.map { siteDescriptor ->
        return@map checkNotNull(siteDataMap[siteDescriptor]) {
          "Sites do not contain ${siteDescriptor} even though orders does"
        }
      }
    }
  }

  private fun instantiateSite(chanSiteData: ChanSiteData): Site {
    val clazz = siteRegistry.SITE_CLASSES_MAP[chanSiteData.siteDescriptor]
      ?: throw java.lang.IllegalArgumentException("Unknown site descriptor: ${chanSiteData.siteDescriptor}")

    val site = instantiateSiteClass(clazz)
      ?: throw IllegalStateException("Couldn't instantiate site: ${clazz::class.java.simpleName}")
    val settings = JsonSettings()

    val siteId = siteRegistry.SITE_CLASSES.entries.firstOrNull { (_, siteClass) ->
      return@firstOrNull siteClass == clazz
    }
      ?.key
      ?: throw IllegalStateException("Couldn't find siteId in the site registry: ${clazz::class.java.simpleName}")

    site.initialize(siteId, settings)
    site.postInitialize()

    return site
  }

  private fun instantiateSiteClass(clazz: Class<out Site>): Site? {
    return try {
      clazz.newInstance()
    } catch (e: InstantiationException) {
      throw IllegalArgumentException(e)
    } catch (e: IllegalAccessException) {
      throw IllegalArgumentException(e)
    }
  }

  private fun ensureSitesAndOrdersConsistency() {
    if (isDevFlavor) {
      check(siteDataMap.size == orders.size) {
        "Inconsistency detected! siteDataMap.size (${siteDataMap.size}) != orders.size (${orders.size})"
      }

      check(siteMap.size == orders.size) {
        "Inconsistency detected! siteMap.size (${siteMap.size}) != orders.size (${orders.size})"
      }
    }
  }

  private fun isReady() = suspendableInitializer.isInitialized()

  companion object {
    private const val TAG = "SiteManager"
  }
}