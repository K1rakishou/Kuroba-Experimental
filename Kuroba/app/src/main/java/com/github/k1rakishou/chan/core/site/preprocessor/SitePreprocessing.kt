package com.github.k1rakishou.chan.core.site.preprocessor

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.common.awaitSilently
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

class SitePreprocessing(
  private val appScope: CoroutineScope,
  private val siteManager: SiteManager,
  private val preprocessors: Set<Preprocessor>
) {
  private val _lock = ReentrantReadWriteLock()

  @GuardedBy("_lock")
  private val _active = mutableMapOf<SiteDescriptor, ActivePreprocessor>()

  suspend fun start(siteDescriptor: SiteDescriptor) {
    val site = siteManager.bySiteDescriptor(siteDescriptor)
      ?: return

    val preprocessor = preprocessors
      .firstOrNull { preprocessor -> preprocessor.siteDescriptor == siteDescriptor }
      ?: return

    val job = Job()

    val activePreprocessor = _lock.write {
      val activePreprocessor = _active[siteDescriptor]
      if (activePreprocessor != null) {
        return@write activePreprocessor
      }

      _active[siteDescriptor] = ActivePreprocessor(CompletableDeferred(), job)
      return@write null
    }

    if (activePreprocessor != null) {
      activePreprocessor.waiter.awaitSilently()
      return
    }

    Logger.debug(TAG) { "Starting '${siteDescriptor}' site preprocessor" }

    appScope.launch(job + Dispatchers.IO) {
      try {
        preprocessor.preprocess(site)
      } catch (error: Throwable) {
        Logger.error(TAG, error) { "'${siteDescriptor}' site preprocessor crashed" }
      } finally {
        stop(siteDescriptor)
      }
    }
  }

  suspend fun awaitUntilDone(siteDescriptor: SiteDescriptor) {
    val activePreprocessor = _lock.write { _active[siteDescriptor] }
      ?: return

    Logger.debug(TAG) { "Awaiting for '${siteDescriptor}' site preprocessor..." }
    activePreprocessor.waiter.awaitSilently()
    Logger.debug(TAG) { "Awaiting for '${siteDescriptor}' site preprocessor...done" }
  }

  fun stop(siteDescriptor: SiteDescriptor) {
    val site = siteManager.bySiteDescriptor(siteDescriptor)
      ?: return

    val preprocessor = preprocessors
      .firstOrNull { preprocessor -> preprocessor.siteDescriptor == siteDescriptor }
      ?: return

    Logger.debug(TAG) { "Stopping '${siteDescriptor}' site preprocessor" }

    _lock.write {
      _active.remove(siteDescriptor)?.let { activePreprocessor ->
        activePreprocessor.waiter.cancel()
        activePreprocessor.job.cancel()
      }
    }

    try {
      preprocessor.stop(site)
    } catch (error: Throwable) {
      Logger.error(TAG, error) { "Crash when trying to stop '${siteDescriptor}' site preprocessor" }
    }
  }

  data class ActivePreprocessor(
    val waiter: CompletableDeferred<Unit>,
    val job: Job
  )

  interface Preprocessor {
    val siteDescriptor: SiteDescriptor
    val sitePreprocessingEventQueue: SitePreprocessingEventQueue

    suspend fun <T : Site> preprocess(site: T)
    fun <T : Site> stop(site: T)

    class Exception(siteDescriptor: SiteDescriptor, message: String)
      : ClientException("Preprocessor for site ${siteDescriptor} failed with message '${message}'")
  }

  companion object {
    private const val TAG = "SitePreprocessing"
  }
}