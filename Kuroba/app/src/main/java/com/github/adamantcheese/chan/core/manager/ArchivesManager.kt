package com.github.adamantcheese.chan.core.manager

import android.content.Context
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.repository.ThirdPartyArchiveInfoRepository
import com.google.gson.Gson
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.joda.time.DateTime
import org.joda.time.Duration
import java.io.InputStreamReader
import java.util.*
import kotlin.coroutines.CoroutineContext

class ArchivesManager(
  private val appContext: Context,
  private val thirdPartyArchiveInfoRepository: ThirdPartyArchiveInfoRepository,
  private val gson: Gson,
  private val appConstants: AppConstants,
  private val verboseLogsEnabled: Boolean
) : CoroutineScope {
  private val supervisorJob = SupervisorJob()

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Default + supervisorJob

  private val archiveFetchHistoryChangeSubject = PublishProcessor.create<FetchHistoryChange>()
  private val mutex = Mutex()

  private lateinit var allArchivesData: List<ArchiveData>
  private lateinit var allArchiveDescriptors: List<ArchiveDescriptor>
  private lateinit var allArchiveDescriptorsMap: Map<Long, ArchiveDescriptor>

  init {
    launch {
      val allArchives = loadArchives()

      val archiveDescriptors = allArchives.map { archive ->
        return@map ArchiveDescriptor(
          -1L,
          archive.name,
          archive.domain,
          ArchiveDescriptor.ArchiveType.byDomain(archive.domain)
        )
      }

      val archiveInfoMap = thirdPartyArchiveInfoRepository.init(archiveDescriptors)

      archiveDescriptors.forEach { descriptor ->
        descriptor.setArchiveDatabaseId(archiveInfoMap[descriptor.domain]!!.databaseId)

        for (archive in allArchives) {
          if (descriptor.domain == archive.domain) {
            archive.setArchiveDescriptor(descriptor)
            break
          }
        }
      }

      allArchivesData = allArchives
      allArchiveDescriptors = archiveDescriptors
      allArchiveDescriptorsMap = archiveDescriptors.associateBy { it.getArchiveDatabaseId() }
    }
  }

  fun getAllArchiveData(): List<ArchiveData> {
    require(::allArchivesData.isInitialized) {
      "allArchivesData was not initialized yet"
    }

    return allArchivesData
  }

  fun getAllArchivesDescriptors(): List<ArchiveDescriptor> {
    require(::allArchiveDescriptors.isInitialized) {
      "allArchiveDescriptors was not initialized yet"
    }

    return allArchiveDescriptors
  }

  suspend fun getArchiveDescriptorByDatabaseId(archiveId: Long?): ArchiveDescriptor? {
    return mutex.withLock {
      if (archiveId == null) {
        return@withLock null
      }

      require(::allArchiveDescriptorsMap.isInitialized) {
        "allArchiveDescriptorsMap was not initialized yet"
      }

      return@withLock allArchiveDescriptorsMap[archiveId]
    }
  }

  /**
   * This flowable is only for notifying that something has changed in the fetch history. You have
   * to reload the history by yourself after receiving a notification
   * */
  fun listenForFetchHistoryChanges(): Flowable<FetchHistoryChange> {
    return archiveFetchHistoryChangeSubject
      .observeOn(AndroidSchedulers.mainThread())
      .hide()
  }

  fun getArchiveDescriptorId(archiveDescriptor: ArchiveDescriptor?): Long {
    if (archiveDescriptor == null) {
      return ArchiveDescriptor.NO_ARCHIVE_ID
    }

    return archiveDescriptor.getArchiveDatabaseId()
  }

  @OptIn(ExperimentalStdlibApi::class)
  suspend fun getArchiveDescriptor(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    forced: Boolean
  ): ModularResult<ArchiveDescriptor?> {
    Logger.d(TAG, "getArchiveDescriptor(threadDescriptor=$threadDescriptor, forced=$forced)")

    return Try {
      if (!threadDescriptor.boardDescriptor.siteDescriptor.is4chan()) {
        // Only 4chan archives are supported
        return@Try null
      }

      val suitableArchives = allArchivesData.filter { archiveData ->
        archiveData.supportedBoards.contains(threadDescriptor.boardCode())
      }

      if (suitableArchives.isEmpty()) {
        if (verboseLogsEnabled) {
          Logger.d(TAG, "No archives for board (${threadDescriptor.boardCode()})")
        }

        return@Try null
      }

      val enabledSuitableArchives = suitableArchives.filter { suitableArchive ->
        return@filter thirdPartyArchiveInfoRepository.isArchiveEnabled(
          suitableArchive.getArchiveDescriptor()
        ).unwrap()
      }

      if (enabledSuitableArchives.isEmpty()) {
        if (verboseLogsEnabled) {
          Logger.d(TAG, "All archives are disabled")
        }

        return@Try null
      }

      return@Try getBestPossibleArchiveOrNull(
        threadDescriptor,
        enabledSuitableArchives,
        forced
      )
    }
  }

  private suspend fun getBestPossibleArchiveOrNull(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    suitableArchives: List<ArchiveData>,
    forced: Boolean
  ): ArchiveDescriptor? {
    Logger.d(TAG, "getBestPossibleArchiveOrNull(threadDescriptor=$threadDescriptor, " +
      "suitableArchivesSize=${suitableArchives.size}, forced=$forced)")

    // Get fetch history (last N fetch results) for this thread for every suitable archive
    val fetchHistoryMap = thirdPartyArchiveInfoRepository.selectLatestFetchHistoryForThread(
      suitableArchives.map { it.getArchiveDescriptor() },
      threadDescriptor
    ).unwrap()

    if (fetchHistoryMap.isEmpty()) {
      // No history means we haven't fetched anything yet, so every archive is suitable
      return suitableArchives.firstOrNull { archiveData ->
        return@firstOrNull archiveData.supportedFiles.contains(threadDescriptor.boardCode())
      }?.getArchiveDescriptor()
    }

    val fetchIsFreshTimeThreshold = if (forced) {
      DateTime.now().minus(FORCED_ARCHIVE_UPDATE_INTERVAL)
    } else {
      DateTime.now().minus(NORMAL_ARCHIVE_UPDATE_INTERVAL)
    }

    val sortedFetchHistoryList = fetchHistoryMap.mapNotNull { (archiveDescriptor, fetchHistoryList) ->
      // If fetch history contains at least one fetch that was executed later than
      // [fetchIsFreshTimeThreshold] that means the whole history is still fresh and we don't
      // need to fetch posts from this archive right now. So we just need to filter out this
      // archive.
      if (hasFreshSuccessfulFetchResult(fetchHistoryList, fetchIsFreshTimeThreshold)) {
        return@mapNotNull null
      }

      // Otherwise calculate score for archive
      return@mapNotNull archiveDescriptor to calculateFetchResultsScore(fetchHistoryList)
    }.sortedByDescending { (_, successfulFetchesCount) -> successfulFetchesCount }

    if (sortedFetchHistoryList.isEmpty()) {
      if (verboseLogsEnabled) {
        Logger.d(TAG, "sortedFetchHistoryList is empty")
      }

      return null
    }

    if (verboseLogsEnabled) {
      sortedFetchHistoryList.forEachIndexed { index, pair ->
        val (archiveDescriptor, score) = pair

        Logger.d(TAG, "sortedFetchHistoryList[$index]: archiveDescriptor=$archiveDescriptor, score=$score")
      }
    }

    if (!forced) {
      val allArchivesAreBad = sortedFetchHistoryList.all { (_, successfulFetchesCount) ->
        successfulFetchesCount == 0
      }

      // If there are no archive with positive score and we are not forced to fetch new posts
      // then do not fetch anything.
      if (allArchivesAreBad) {
        return null
      }
    }

    // Try to find an archive that supports files for this board
    for ((archiveDescriptor, successFetches) in sortedFetchHistoryList) {
      if (successFetches <= 0) {
        // No success fetches for the current archive. We can't use it.
        continue
      }

      val suitableArchive = suitableArchives.firstOrNull { archiveData ->
        archiveData.getArchiveDescriptor() == archiveDescriptor
      }

      checkNotNull(suitableArchive) {
        "Couldn't find suitable archive by archiveDescriptor ($archiveDescriptor)"
      }

      if (suitableArchive.supportedFiles.contains(threadDescriptor.boardCode())) {
        // Archive has a positive score and it even supports files for this board. We found
        // the most suitable archive for the next fetch.
        return suitableArchive.getArchiveDescriptor()
      }
    }

    // If we couldn't find an archive that supports files for this board, but we are forced
    // to do a fetch in any case, then just return the first archive with the highest score
    return sortedFetchHistoryList.first().first
  }

  /**
   * Checks whether fetch result history contains at least one successful fetch result and that
   * fetch was executed later than [fetchIsFreshTimeThreshold]. If we managed to find such fetch
   * result that means we don't need to do a fetch to this archive for now.
   * */
  private fun hasFreshSuccessfulFetchResult(
    fetchHistoryList: List<ThirdPartyArchiveFetchResult>,
    fetchIsFreshTimeThreshold: DateTime
  ): Boolean {
    return fetchHistoryList.any { fetchHistory ->
      fetchHistory.success && fetchHistory.insertedOn > fetchIsFreshTimeThreshold
    }
  }

  fun calculateFetchResultsScore(fetchHistoryList: List<ThirdPartyArchiveFetchResult>): Int {
    val totalFetches = fetchHistoryList.size

    check(totalFetches <= appConstants.archiveFetchHistoryMaxEntries) {
      "Too many totalFetches: $totalFetches"
    }

    if (totalFetches < appConstants.archiveFetchHistoryMaxEntries) {
      // When we haven't sent at least archiveFetchHistoryMaxEntries fetches, we assume that
      // all unsent yet fetches are success fetches, so basically unsentFetchesCount are
      // all success fetches
      val unsentFetchesCount = appConstants.archiveFetchHistoryMaxEntries - totalFetches
      val successFetchesCount = fetchHistoryList.count { fetchHistory ->
        fetchHistory.success
      }

      return unsentFetchesCount + successFetchesCount
    } else {
      return fetchHistoryList.count { fetchHistory -> fetchHistory.success }
    }
  }

  fun getRequestLinkForThread(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    archiveDescriptor: ArchiveDescriptor
  ): String? {
    val archiveData = getArchiveDataByArchiveDescriptor(archiveDescriptor)
      ?: return null

    return String.format(
      Locale.ENGLISH,
      FOOLFUUKA_THREAD_ENDPOINT_FORMAT,
      archiveData.domain,
      threadDescriptor.boardCode(),
      threadDescriptor.opNo
    )
  }

  fun getRequestLinkForPost(
    postDescriptor: PostDescriptor,
    archiveDescriptor: ArchiveDescriptor
  ): String? {
    val threadDescriptor = postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
    if (threadDescriptor == null) {
      // Not a thread, catalogs are not supported
      return null
    }

    if (!threadDescriptor.boardDescriptor.siteDescriptor.is4chan()) {
      return null
    }

    val archiveData = getArchiveDataByArchiveDescriptor(archiveDescriptor)
      ?: return null

    return String.format(
      Locale.ENGLISH,
      FOOLFUUKA_POST_ENDPOINT_FORMAT,
      archiveData.domain,
      threadDescriptor.boardCode(),
      postDescriptor.postNo
    )
  }

  fun doesArchiveStoreMedia(
    archiveDescriptor: ArchiveDescriptor,
    boardDescriptor: BoardDescriptor
  ): Boolean {
    if (!boardDescriptor.siteDescriptor.is4chan()) {
      return false
    }

    val archiveData = getArchiveDataByArchiveDescriptor(archiveDescriptor)
      ?: return false

    return archiveData.supportedFiles.contains(boardDescriptor.boardCode)
  }

  private fun getArchiveDataByArchiveDescriptor(archiveDescriptor: ArchiveDescriptor): ArchiveData? {
    return allArchivesData.firstOrNull { archiveData ->
      return@firstOrNull archiveData.name == archiveDescriptor.name
        && archiveData.domain == archiveDescriptor.domain
    }
  }

  private fun loadArchives(): List<ArchiveData> {
    return appContext.assets.open(ARCHIVES_JSON_FILE_NAME).use { inputStream ->
      return@use gson.fromJson<Array<ArchiveData>>(
        JsonReader(InputStreamReader(inputStream)),
        Array<ArchiveData>::class.java
      ).toList()
    }
  }

  suspend fun isArchiveEnabled(archiveDescriptor: ArchiveDescriptor): ModularResult<Boolean> {
    return thirdPartyArchiveInfoRepository.isArchiveEnabled(archiveDescriptor)
  }

  suspend fun setArchiveEnabled(archiveDescriptor: ArchiveDescriptor, isEnabled: Boolean): ModularResult<Unit> {
    return thirdPartyArchiveInfoRepository.setArchiveEnabled(archiveDescriptor, isEnabled)
  }

  suspend fun selectLatestFetchHistory(
    archiveDescriptor: ArchiveDescriptor
  ): ModularResult<List<ThirdPartyArchiveFetchResult>> {
    return thirdPartyArchiveInfoRepository.selectLatestFetchHistory(archiveDescriptor)
  }

  suspend fun selectLatestFetchHistoryForAllArchives(): ModularResult<Map<ArchiveDescriptor, List<ThirdPartyArchiveFetchResult>>> {
    return thirdPartyArchiveInfoRepository.selectLatestFetchHistory(allArchiveDescriptors)
  }

  suspend fun insertFetchHistory(
    fetchResult: ThirdPartyArchiveFetchResult
  ): ModularResult<ThirdPartyArchiveFetchResult?> {
    return thirdPartyArchiveInfoRepository.insertFetchResult(fetchResult)
      .peekValue { value ->
        if (value != null) {
          val fetchHistoryChange = FetchHistoryChange(
            value.databaseId,
            fetchResult.archiveDescriptor,
            FetchHistoryChangeType.Insert
          )

          archiveFetchHistoryChangeSubject.onNext(fetchHistoryChange)
        }
      }
  }

  suspend fun deleteFetchResult(fetchResult: ThirdPartyArchiveFetchResult): ModularResult<Unit> {
    return thirdPartyArchiveInfoRepository.deleteFetchResult(fetchResult)
      .peekValue {
        val fetchHistoryChange = FetchHistoryChange(
          fetchResult.databaseId,
          fetchResult.archiveDescriptor,
          FetchHistoryChangeType.Delete
        )

        archiveFetchHistoryChangeSubject.onNext(fetchHistoryChange)
      }
  }

  data class ArchiveData(
    @Expose(serialize = false, deserialize = false)
    private var archiveDescriptor: ArchiveDescriptor? = null,
    @SerializedName("name")
    val name: String,
    @SerializedName("domain")
    val domain: String,
    @SerializedName("boards")
    val supportedBoards: List<String>,
    @SerializedName("files")
    val supportedFiles: List<String>
  ) {
    fun isEnabled(): Boolean = domain !in disabledArchives

    fun setArchiveDescriptor(archiveDescriptor: ArchiveDescriptor) {
      require(this.archiveDescriptor == null) { "Double initialization!" }

      this.archiveDescriptor = archiveDescriptor
    }

    fun getArchiveDescriptor(): ArchiveDescriptor {
      return requireNotNull(archiveDescriptor) {
        "Attempt to access archiveDescriptor before ArchiveData was fully initialized"
      }
    }
  }

  data class FetchHistoryChange(
    val databaseId: Long,
    val archiveDescriptor: ArchiveDescriptor,
    val changeType: FetchHistoryChangeType
  )

  enum class FetchHistoryChangeType {
    Insert,
    Delete
  }

  companion object {
    // These archives are disabled for now
    private val disabledArchives = setOf(
      // Disabled because it's weird as hell. I can't even say whether it's working or not.
      "archive.b-stats.org",

      // Disabled because it requires Cloudflare authentication which is not supported for
      // now.
      "warosu.org",

      // Disable because it always returns 403 when sending requests via the OkHttpClient,
      // but works normally when opening in the browser. Apparently some kind of
      // authentication is required.
      "thebarchive.com"
    )

    // We can forcefully fetch posts from an archive no more than once in 5 minutes
    private val FORCED_ARCHIVE_UPDATE_INTERVAL = Duration.standardMinutes(5)

    // We can normally fetch posts from an archive no more than once in four hours
    private val NORMAL_ARCHIVE_UPDATE_INTERVAL = Duration.standardHours(4)

    private const val TAG = "ArchivesManager"
    private const val ARCHIVES_JSON_FILE_NAME = "archives.json"
    private const val FOOLFUUKA_THREAD_ENDPOINT_FORMAT = "https://%s/_/api/chan/thread/?board=%s&num=%d"
    private const val FOOLFUUKA_POST_ENDPOINT_FORMAT = "https://%s/_/api/chan/post/?board=%s&num=%d"
  }
}