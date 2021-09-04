package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteRegistry
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.ChanSiteData
import com.github.k1rakishou.model.repository.SiteRepository
import dagger.Lazy
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@DoNotStrip
open class SiteManager(
  private val appScope: CoroutineScope,
  private val isDevFlavor: Boolean,
  private val verboseLogsEnabled: Boolean,
  private val _siteRepository: Lazy<SiteRepository>,
  private val siteRegistry: SiteRegistry
) {
  private val suspendableInitializer = SuspendableInitializer<Unit>("SiteManager")
  private val debouncer = DebouncingCoroutineExecutor(appScope)

  private val sitesChangedSubject = PublishProcessor.create<Unit>()

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val siteDataMap = mutableMapWithCap<SiteDescriptor, ChanSiteData>(32)
  @GuardedBy("lock")
  private val siteMap = mutableMapWithCap<SiteDescriptor, Site>(32)
  @GuardedBy("lock")
  private val orders = mutableListWithCap<SiteDescriptor>(32)

  private val siteRepository: SiteRepository
    get() = _siteRepository.get()

  @OptIn(ExperimentalTime::class)
  fun initialize(allSitesDeferred: CompletableDeferred<List<ChanSiteData>>) {
    Logger.d(TAG, "SiteManager.initialize()")

    appScope.launch(Dispatchers.IO) {
      Logger.d(TAG, "loadSitesInternal() start")
      val time = measureTime { loadSitesInternal(allSitesDeferred) }
      Logger.d(TAG, "loadSitesInternal() end, took ${time}")
    }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun loadSitesInternal(allSitesDeferred: CompletableDeferred<List<ChanSiteData>>) {
    try {
      val result = siteRepository.initialize(siteRegistry.SITE_CLASSES_MAP.keys)
      if (result is ModularResult.Error) {
        Logger.e(TAG, "siteRepository.initializeSites() siteRepository.initialize() error", result.error)
        suspendableInitializer.initWithError(result.error)
        return
      }

      result as ModularResult.Value

      lock.write {
        siteDataMap.clear()
        siteMap.clear()
        orders.clear()

        result.value.forEach { chanSiteData ->
          val site = instantiateSite(chanSiteData)
            ?: return@forEach

          siteDataMap[chanSiteData.siteDescriptor] = chanSiteData
          siteMap[chanSiteData.siteDescriptor] = site

          orders.add(0, chanSiteData.siteDescriptor)
        }

        allSitesDeferred.complete(siteDataMap.values.toList())
      }

      ensureSitesAndOrdersConsistency()
      suspendableInitializer.initWithValue(Unit)

      Logger.d(TAG, "siteRepository.initializeSites() done. Loaded ${result.value.size} sites")
    } catch (error: Throwable) {
      suspendableInitializer.initWithError(error)
      allSitesDeferred.completeExceptionally(error)
      Logger.e(TAG, "siteRepository.initializeSites() unknown error", error)
    }
  }

  fun listenForSitesChanges(): Flowable<Unit> {
    return sitesChangedSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error -> Logger.e(TAG, "Error while listening for sitesChangedSubject updates", error) }
      .hide()
  }

  fun firstSiteDescriptor(): SiteDescriptor? {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    return lock.read {
      return@read orders.firstOrNull(this@SiteManager::isSiteActive)
    }
  }

  fun secondSiteDescriptor(): SiteDescriptor? {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    return lock.read {
      return@read orders.getOrNull(1)?.let { siteDescriptor ->
        if (!isSiteActive(siteDescriptor)) {
          return@read null
        }

        return@read siteDescriptor
      }
    }
  }

  fun activeSiteCount(): Int {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    return lock.read { siteMap.keys.count { siteDescriptor -> isSiteActive(siteDescriptor) } }
  }

  suspend fun activateOrDeactivateSite(
    siteDescriptor: SiteDescriptor,
    activate: Boolean,
    doAfterPersist: (() -> Unit)? = null
  ): Boolean {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    val updated = lock.write {
      val enabled = siteMap[siteDescriptor]?.enabled() ?: false
      if (!enabled) {
        return@write false
      }

      val chanSiteData = siteDataMap[siteDescriptor]
        ?: return@write false

      if (chanSiteData.active == activate) {
        return@write false
      }

      chanSiteData.active = activate
      return@write true
    }

    if (!updated) {
      return false
    }

    debouncer.post(DEBOUNCE_TIME_MS) {
      siteRepository.persist(getSitesOrdered())
      doAfterPersist?.invoke()
    }

    sitesChanged()

    return true
  }

  fun isSiteActive(siteDescriptor: SiteDescriptor): Boolean {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    return lock.read {
      val enabled = siteMap[siteDescriptor]?.enabled()
        ?: false

      if (!enabled) {
        return@read false
      }

      return@read siteDataMap[siteDescriptor]?.active
        ?: false
    }
  }

  fun areSitesSetup(): Boolean {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    return lock.read {
      for ((siteDescriptor, site) in siteMap) {
        if (!site.enabled()) {
          continue
        }

        val isActive = siteDataMap[siteDescriptor]?.active ?: false
        if (!isActive) {
          continue
        }

        return@read true
      }

      return@read false
    }
  }

  open fun bySiteDescriptor(siteDescriptor: SiteDescriptor): Site? {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    return lock.read {
      if (!isSiteActive(siteDescriptor)) {
        return@read null
      }

      return@read siteMap[siteDescriptor]
    }
  }

  fun viewSitesOrdered(viewer: (ChanSiteData, Site) -> Boolean) {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    lock.read {
      orders.forEach { siteDescriptor ->
        val chanSiteData = requireNotNull(siteDataMap[siteDescriptor]) {
          "Couldn't find chanSiteData by siteDescriptor: $siteDescriptor in orders"
        }

        val site = requireNotNull(siteMap[siteDescriptor]) {
          "Couldn't find site by siteDescriptor: $siteDescriptor"
        }

        if (!viewer(chanSiteData, site)) {
          return@read
        }
      }
    }
  }

  fun <T> mapFirstActiveSiteOrNull(mapper: (ChanSiteData, Site) -> T?): T? {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    return lock.read {
      for (siteDescriptor in orders) {
        if (!isSiteActive(siteDescriptor)) {
          continue
        }

        val chanSiteData = requireNotNull(siteDataMap[siteDescriptor]) {
          "Couldn't find chanSiteData by siteDescriptor: $siteDescriptor in orders"
        }

        val site = requireNotNull(siteMap[siteDescriptor]) {
          "Couldn't find site by siteDescriptor: $siteDescriptor"
        }

        val mapped = mapper(chanSiteData, site)
        if (mapped != null) {
          return@read mapped
        }
      }

      return@read null
    }
  }

  fun firstActiveSiteOrNull(predicate: (ChanSiteData, Site) -> Boolean): Site? {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    return lock.read {
      val descriptor = orders.firstOrNull { siteDescriptor ->
        if (!isSiteActive(siteDescriptor)) {
          return@firstOrNull false
        }

        val chanSiteData = requireNotNull(siteDataMap[siteDescriptor]) {
          "Couldn't find chanSiteData by siteDescriptor: $siteDescriptor in orders"
        }

        val site = requireNotNull(siteMap[siteDescriptor]) {
          "Couldn't find site by siteDescriptor: $siteDescriptor"
        }

        return@firstOrNull predicate(chanSiteData, site)
      }

      if (descriptor == null) {
        return@read null
      }

      return@read siteMap[descriptor]
    }
  }

  fun viewActiveSitesOrderedWhile(viewer: (ChanSiteData, Site) -> Boolean) {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    lock.read {
      orders.forEach { siteDescriptor ->
        if (!isSiteActive(siteDescriptor)) {
          return@forEach
        }

        val chanSiteData = requireNotNull(siteDataMap[siteDescriptor]) {
          "Couldn't find chanSiteData by siteDescriptor: $siteDescriptor in orders"
        }

        val site = requireNotNull(siteMap[siteDescriptor]) {
          "Couldn't find site by siteDescriptor: $siteDescriptor"
        }

        if (!viewer(chanSiteData, site)) {
          return@read
        }
      }
    }
  }

  fun onSiteMoving(fromSiteDescriptor: SiteDescriptor, toSiteDescriptor: SiteDescriptor) {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }

    lock.write {
      val fromIndex = orders.indexOf(fromSiteDescriptor)
      if (fromIndex < 0) {
        return@write
      }

      val toIndex = orders.indexOf(toSiteDescriptor)
      if (toIndex < 0) {
        return@write
      }

      orders.add(toIndex, orders.removeAt(fromIndex))
    }

    ensureSitesAndOrdersConsistency()
  }

  fun onSiteMoved() {
    debouncer.post(SITE_MOVED_DEBOUNCE_TIME_MS) { siteRepository.persist(getSitesOrdered()) }
    sitesChanged()
  }

  fun runWhenInitialized(func: (Throwable?) -> Unit) {
    suspendableInitializer.runWhenInitialized(func)
  }

  @OptIn(ExperimentalTime::class)
  open suspend fun awaitUntilInitialized() {
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

  private fun instantiateSite(chanSiteData: ChanSiteData): Site? {
    val clazz = siteRegistry.SITE_CLASSES_MAP[chanSiteData.siteDescriptor]
    if (clazz == null) {
      Logger.e(TAG, "Unknown site descriptor: ${chanSiteData.siteDescriptor}")
      return null
    }

    val site = instantiateSiteClass(clazz)
    if (site == null) {
      Logger.e(TAG, "Couldn't instantiate site: ${clazz::class.java.simpleName}")
      return null
    }

    site.initialize()
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
      lock.read {
        check(siteDataMap.size == orders.size) {
          "Inconsistency detected! siteDataMap.size (${siteDataMap.size}) != orders.size (${orders.size})"
        }

        check(siteMap.size == orders.size) {
          "Inconsistency detected! siteMap.size (${siteMap.size}) != orders.size (${orders.size})"
        }
      }
    }
  }

  fun isReady() = suspendableInitializer.isInitialized()

  class SiteNotFoundException(siteDescriptor: SiteDescriptor) : Exception("Site ${siteDescriptor} not found")

  companion object {
    private const val TAG = "SiteManager"

    private const val DEBOUNCE_TIME_MS = 500L
    private const val SITE_MOVED_DEBOUNCE_TIME_MS = 100L
  }
}