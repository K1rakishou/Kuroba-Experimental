package com.github.adamantcheese.chan.core.manager

import android.content.Context
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.safeRun
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveInfo
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.repository.ThirdPartyArchiveInfoRepository
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import java.io.InputStreamReader
import java.util.*

class ArchivesManager(
        private val appContext: Context,
        private val thirdPartyArchiveInfoRepository: ThirdPartyArchiveInfoRepository,
        private val gson: Gson,
        private val appConstants: AppConstants
) {
    private val archiveFetchHistoryChangeSubject = PublishProcessor.create<FetchHistoryChange>()
    private val archives by lazy { loadArchives() }

    fun getAllArchiveData(): List<ArchiveData> {
        return archives
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

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun getArchiveDescriptor(
            threadDescriptor: ChanDescriptor.ThreadDescriptor,
            forced: Boolean
    ): ModularResult<ArchiveDescriptor?> {
        return safeRun {
            Logger.d(TAG, "getArchiveDescriptor(threadDescriptor=$threadDescriptor, forced=$forced)")

            if (!threadDescriptor.boardDescriptor.siteDescriptor.is4chan()) {
                // Only 4chan archives are supported
                return@safeRun null
            }

            val suitableArchives = archives.filter { archiveData ->
                archiveData.supportedBoards.contains(threadDescriptor.boardCode())
            }

            if (suitableArchives.isEmpty()) {
                return@safeRun null
            }

            val enabledSuitableArchives = suitableArchives.filter { suitableArchive ->
                return@filter thirdPartyArchiveInfoRepository.isArchiveEnabled(
                        suitableArchive.getArchiveDescriptor()
                ).unwrap()
            }

            if (enabledSuitableArchives.isEmpty()) {
                return@safeRun null
            }

            return@safeRun getBestPossibleArchiveOrNull(
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
        Logger.d(TAG, "getBestPossibleArchiveOrNull($threadDescriptor, $suitableArchives, $forced)")

        val fetchHistoryMap = thirdPartyArchiveInfoRepository.selectLatestFetchHistory(
                suitableArchives.map { it.getArchiveDescriptor() }
        ).unwrap()

        if (fetchHistoryMap.isEmpty()) {
            // No history means we haven't fetched anything yet, so every archive is suitable
            return suitableArchives.firstOrNull { archiveData ->
                archiveData.supportedFiles.contains(threadDescriptor.boardCode())
            }?.getArchiveDescriptor()
        }

        val sortedFetchHistoryList = fetchHistoryMap.map { (archiveDescriptor, fetchHistoryList) ->
            archiveDescriptor to calculateSuccessFetches(fetchHistoryList)
        }.sortedByDescending { (_, successfulFetchesCount) -> successfulFetchesCount }

        Logger.d(TAG, "$sortedFetchHistoryList")
        check(sortedFetchHistoryList.isNotEmpty()) { "sortedFetchHistoryList is empty" }

        if (!forced) {
            val allArchivesAreBad = sortedFetchHistoryList.all { (_, successfulFetchesCount) ->
                successfulFetchesCount == 0
            }

            // There are no archives with at least one successful fetch over then last N fetches
            if (allArchivesAreBad) {
                return null
            }
        }

        // Try to find an archive that supports files for this board
        for ((archiveDescriptor, successFetches) in sortedFetchHistoryList) {
            if (successFetches <= 0) {
                // No success fetches for the current archive. We either had no internet connection
                // while trying to fetch posts from this archive and now there are no more attempts
                // left or the archive site is actually dead. In any case we can't use it.
                continue
            }

            val suitableArchive = suitableArchives.firstOrNull { archiveData ->
                archiveData.getArchiveDescriptor() == archiveDescriptor
            }

            checkNotNull(suitableArchive) {
                "Couldn't find suitable archive by archiveDescriptor ($archiveDescriptor)"
            }

            if (suitableArchive.supportedFiles.contains(threadDescriptor.boardCode())) {
                return suitableArchive.getArchiveDescriptor()
            }
        }

        // If we couldn't find an archive that supports files for this board then just return the
        // first archive with the best success ration
        return sortedFetchHistoryList.first().first
    }

    fun calculateSuccessFetches(fetchHistoryList: List<ThirdPartyArchiveFetchResult>): Int {
        val totalFetches = fetchHistoryList.size

        check(totalFetches <= appConstants.archiveFetchHistoryMaxEntries) {
            "Too many totalFetches: $totalFetches"
        }

        if (totalFetches < appConstants.archiveFetchHistoryMaxEntries) {
            // When we haven't sent at least archiveFetchHistoryMaxEntries fetches, we assume that
            // all unsent yet fetches are success fetches, so basically unsentFetchesCount are
            // all success fetches
            val unsentFetchesCount = appConstants.archiveFetchHistoryMaxEntries - totalFetches
            val successFetchesCount = fetchHistoryList.count { fetchHistory -> fetchHistory.success }

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

    fun archiveStoreThumbnails(archiveDescriptor: ArchiveDescriptor): Boolean {
        return when (archiveDescriptor.domain) {
            // Archived.moe stores only thumbnails
            "archived.moe" -> true
            else -> false
        }
    }

    private fun getArchiveDataByArchiveDescriptor(archiveDescriptor: ArchiveDescriptor): ArchiveData? {
        return archives.firstOrNull { archiveData ->
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
        return thirdPartyArchiveInfoRepository.selectLatestFetchHistory(allArchives)
    }

    suspend fun archiveExists(archiveDescriptor: ArchiveDescriptor): ModularResult<Boolean> {
        return thirdPartyArchiveInfoRepository.archiveExists(archiveDescriptor)
    }

    suspend fun insertThirdPartyArchiveInfo(
            thirdPartyArchiveInfo: ThirdPartyArchiveInfo
    ): ModularResult<Unit> {
        return thirdPartyArchiveInfoRepository.insertThirdPartyArchiveInfo(thirdPartyArchiveInfo)
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
            @SerializedName("name")
            val name: String,
            @SerializedName("domain")
            val domain: String,
            @SerializedName("boards")
            val supportedBoards: List<String>,
            @SerializedName("files")
            val supportedFiles: List<String>
    ) {
        fun getArchiveDescriptor(): ArchiveDescriptor = ArchiveDescriptor(name, domain)
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
        private const val TAG = "ArchivesManager"
        private const val ARCHIVES_JSON_FILE_NAME = "archives.json"
        private const val FOOLFUUKA_THREAD_ENDPOINT_FORMAT = "https://%s/_/api/chan/thread/?board=%s&num=%d"
        private const val FOOLFUUKA_POST_ENDPOINT_FORMAT = "https://%s/_/api/chan/post/?board=%s&num=%d"

        @JvmStatic
        val allArchives = listOf(
                ArchiveDescriptor("4plebs", "archive.4plebs.org"),
                ArchiveDescriptor("Nyafuu Archive", "archive.nyafuu.org"),
                ArchiveDescriptor("Rebecca Black Tech", "archive.rebeccablacktech.com"),
                ArchiveDescriptor("warosu", "warosu.org"),
                ArchiveDescriptor("Desuarchive", "desuarchive.org"),
                ArchiveDescriptor("fireden.net", "boards.fireden.net"),
                ArchiveDescriptor("arch.b4k.co", "arch.b4k.co"),
                ArchiveDescriptor("bstats", "archive.b-stats.org"),
                ArchiveDescriptor("Archived.Moe", "archived.moe"),
                ArchiveDescriptor("TheBArchive.com", "thebarchive.com"),
                ArchiveDescriptor("Archive Of Sins", "archiveofsins.com")
        )
    }
}