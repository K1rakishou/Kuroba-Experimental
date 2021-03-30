package com.github.k1rakishou.chan.core.manager

import android.content.Context
import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.data.ArchiveType
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ArchiveDescriptor
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.google.gson.Gson
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@DoNotStrip
open class ArchivesManager(
  gson: Gson,
  private val appContext: Context,
  private val applicationScope: CoroutineScope,
  private val appConstants: AppConstants,
  private val verboseLogsEnabled: Boolean
) {
  private val suspendableInitializer = SuspendableInitializer<Unit>("ArchivesManager")
  private val modifiedGson = gson
    .newBuilder()
    .excludeFieldsWithoutExposeAnnotation()
    .create()

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val allArchivesData = mutableListOf<ArchiveData>()
  @GuardedBy("lock")
  private val allArchiveDescriptors = mutableListOf<ArchiveDescriptor>()

  fun initialize() {
    Logger.d(TAG, "ArchivesManager.initialize()")

    applicationScope.launch(Dispatchers.IO) {
      Logger.d(TAG, "initializeArchivesManagerInternal() start")
      initializeArchivesManagerInternal()
      Logger.d(TAG, "initializeArchivesManagerInternal() end")
    }
  }

  private fun initializeArchivesManagerInternal() {
    val result = Try {
      val allArchives = loadArchives()

      val archiveDescriptors = allArchives.map { archive ->
        return@map ArchiveDescriptor(
          archive.name,
          archive.domain,
          ArchiveType.byDomain(archive.domain)
        )
      }

      archiveDescriptors.forEach { descriptor ->
        for (archive in allArchives) {
          if (descriptor.domain == archive.domain) {
            archive.setArchiveDescriptor(descriptor)
            archive.setSupportedSites(setOf(Chan4.SITE_DESCRIPTOR))
            break
          }
        }
      }

      lock.write {
        allArchivesData.clear()
        allArchivesData.addAll(allArchives)

        allArchiveDescriptors.clear()
        allArchiveDescriptors.addAll(archiveDescriptors)
      }

      return@Try allArchives.size
    }

    suspendableInitializer.initWithModularResult(result.mapValue { Unit })

    when (result) {
      is ModularResult.Value -> {
        Logger.d(TAG, "initializeArchivesManagerInternal() done. Loaded ${result.value} archives")
      }
      is ModularResult.Error ->     {
        Logger.e(TAG, "initializeArchivesManagerInternal() error", result.error)
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun awaitUntilInitialized() {
    if (suspendableInitializer.isInitialized()) {
      return
    }

    Logger.d(TAG, "ArchivesManager is not ready yet, waiting...")
    val duration = measureTime { suspendableInitializer.awaitUntilInitialized() }
    Logger.d(TAG, "ArchivesManager initialization completed, took $duration")
  }

  suspend fun getAllArchiveData(): List<ArchiveData> {
    suspendableInitializer.awaitUntilInitialized()

    return lock.read { allArchivesData.toList() }
  }

  suspend fun getAllArchivesDescriptors(): List<ArchiveDescriptor> {
    suspendableInitializer.awaitUntilInitialized()

    return lock.read { allArchiveDescriptors.toList() }
  }

  fun extractArchiveTypeFromLinkOrNull(link: String): ArchiveType? {
    return lock.read {
      return@read allArchiveDescriptors
        .firstOrNull { link.contains(it.domain, ignoreCase = true) }
        ?.archiveType
    }
  }

  fun getRequestLinkForThread(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    archiveDescriptor: ArchiveDescriptor
  ): String? {
    val archiveData = getArchiveDataByArchiveDescriptor(archiveDescriptor)
      ?: return null

    val threadEndpointFormat = if (archiveDescriptor.domain == ArchiveType.WakarimasenMoe.domain) {
      WAKARIMASEN_THREAD_ENDPOINT_FORMAT
    } else {
      FOOLFUUKA_THREAD_ENDPOINT_FORMAT
    }

    return String.format(
      Locale.ENGLISH,
      threadEndpointFormat,
      archiveData.domain,
      threadDescriptor.boardCode(),
      threadDescriptor.threadNo
    )
  }

  fun getBoardsSupportingSearch(siteDescriptor: SiteDescriptor): Set<BoardDescriptor> {
    return lock.read {
      val archiveData = allArchivesData.firstOrNull { archiveData ->
        val archiveDescriptor = archiveData.getArchiveDescriptor()

        return@firstOrNull archiveDescriptor.siteDescriptor == siteDescriptor
      }

      if (archiveData == null) {
        return@read emptySet()
      }

      val boardsSupportingSearch = archiveData.boardsSupporingSearch
        ?: emptySet()

      return@read boardsSupportingSearch.map { boardCode ->
        return@map BoardDescriptor.create(siteDescriptor, boardCode)
      }.toSet()
    }
  }

  private fun getArchiveDataByArchiveDescriptor(archiveDescriptor: ArchiveDescriptor): ArchiveData? {
    return lock.read {
      return@read allArchivesData.firstOrNull { archiveData ->
        return@firstOrNull archiveData.name == archiveDescriptor.name
          && archiveData.domain == archiveDescriptor.domain
      }
    }
  }

  private fun loadArchives(): List<ArchiveData> {
    return appContext.assets.open(ARCHIVES_JSON_FILE_NAME).use { inputStream ->
      return@use modifiedGson.fromJson<Array<ArchiveData>>(
        JsonReader(InputStreamReader(inputStream)),
        Array<ArchiveData>::class.java
      ).toList()
    }
  }

  fun supports(threadDescriptor: ChanDescriptor.ThreadDescriptor): Boolean {
    return supports(threadDescriptor.boardDescriptor)
  }

  fun supports(boardDescriptor: BoardDescriptor): Boolean {
    return lock.read {
      return@read allArchivesData.any { archiveData ->
        return@any archiveData.supports(boardDescriptor)
      }
    }
  }

  fun getSupportedSites(siteDescriptor: SiteDescriptor): Set<SiteDescriptor> {
    return lock.read {
      val archiveData = allArchivesData.firstOrNull { archiveData ->
        archiveData.domain == siteDescriptor.siteName
      }

      if (archiveData == null) {
        return@read emptySet()
      }

      return@read archiveData.getSupportedSites()
    }
  }

  fun getSupportedArchiveDescriptors(threadDescriptor: ChanDescriptor.ThreadDescriptor): List<ArchiveDescriptor> {
    return lock.read {
      return@read allArchivesData
        .filter { archiveData -> archiveData.supports(threadDescriptor.boardDescriptor) }
        .map { archiveData -> archiveData.getArchiveDescriptor() }
    }
  }

  fun byBoardDescriptor(boardDescriptor: BoardDescriptor): ArchiveDescriptor? {
    return lock.read {
      return@read allArchivesData
        .firstOrNull { archiveData -> archiveData.supports(boardDescriptor) }
        ?.getArchiveDescriptor()
    }
  }

  fun isSiteArchive(siteDescriptor: SiteDescriptor): Boolean {
    return lock.read {
      return@read allArchiveDescriptors.any { archiveDescriptor -> archiveDescriptor.domain == siteDescriptor.siteName }
    }
  }

  fun getArchiveDescriptorByArchiveType(archiveType: ArchiveType): ArchiveDescriptor? {
    return lock.read {
      return@read allArchiveDescriptors.firstOrNull { archiveDescriptor ->
        return@firstOrNull archiveDescriptor.archiveType == archiveType
      }
    }
  }

  @DoNotStrip
  class ArchiveData(
    @Expose
    @SerializedName("name")
    val name: String,
    @Expose
    @SerializedName("domain")
    private val realDomain: String,
    @Expose
    @SerializedName("boards")
    val supportedBoards: Set<String>?,
    @Expose
    @SerializedName("files")
    val supportedFiles: Set<String>,
    @Expose
    @SerializedName("search")
    val boardsSupporingSearch: Set<String>?
  ) {
    @Expose(serialize = false, deserialize = false)
    private var archiveDescriptor: ArchiveDescriptor? = null

    @Expose(serialize = false, deserialize = false)
    private var supportedSites: Set<SiteDescriptor>? = null

    val domain: String
      get() = getSanitizedDomain()

    fun isEnabled(): Boolean = domain !in disabledArchives

    fun setSupportedSites(sites: Set<SiteDescriptor>) {
      require(this.supportedSites == null) { "Double initialization!" }

      this.supportedSites = sites
    }

    fun getSupportedSites(): Set<SiteDescriptor> = supportedSites?.toSet() ?: emptySet()

    fun setArchiveDescriptor(archiveDescriptor: ArchiveDescriptor) {
      require(this.archiveDescriptor == null) { "Double initialization!" }

      this.archiveDescriptor = archiveDescriptor
    }

    fun getArchiveDescriptor(): ArchiveDescriptor {
      return requireNotNull(archiveDescriptor) {
        "Attempt to access archiveDescriptor before ArchiveData was fully initialized"
      }
    }

    fun supports(boardDescriptor: BoardDescriptor): Boolean {
      val isTheSameArchive = boardDescriptor.siteDescriptor.siteName == domain
      val supportsThisSite = supportedSites?.contains(boardDescriptor.siteDescriptor) ?: false

      val boards = supportedBoards
        ?: emptySet()

      return (isTheSameArchive || supportsThisSite) && boardDescriptor.boardCode in boards
    }

    private fun getSanitizedDomain(): String {
      if (realDomain.startsWith(WWW_PREFIX)) {
        return realDomain.removePrefix(WWW_PREFIX)
      }

      return realDomain
    }
  }

  companion object {
    // These archives are disabled for now
    private val disabledArchives = setOf(
      // Disabled because it's weird as hell. I can't even say whether it's working or not.
      "archive.b-stats.org",
      // Disable because it always returns 403 when sending requests via the OkHttpClient,
      // but works normally when opening in the browser. Apparently some kind of
      // authentication is required.
      "thebarchive.com"
    )

    private const val TAG = "ArchivesManager"
    private const val ARCHIVES_JSON_FILE_NAME = "archives.json"
    private const val FOOLFUUKA_THREAD_ENDPOINT_FORMAT = "https://%s/_/api/chan/thread/?board=%s&num=%d"

    // wakarimasen.moe uses a slightly different request url
    // https://archive.wakarimasen.moe/_/api/chan/thread/&board=a&num=216913439

    private const val WAKARIMASEN_THREAD_ENDPOINT_FORMAT = "https://%s/_/api/chan/thread/&board=%s&num=%d"

    private const val WWW_PREFIX = "www."
  }
}