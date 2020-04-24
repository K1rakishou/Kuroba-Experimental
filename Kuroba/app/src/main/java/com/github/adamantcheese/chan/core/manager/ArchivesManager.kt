package com.github.adamantcheese.chan.core.manager

import android.content.Context
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import java.io.InputStreamReader
import java.util.*
import kotlin.random.Random

class ArchivesManager(
        private val appContext: Context,
        private val gson: Gson
) {
    val allArchives = arrayOf(
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

    private val archives by lazy { loadArchives() }
    private val random = Random(System.currentTimeMillis())

    fun getAllArchiveData(): List<ArchiveData> {
        return archives
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getArchiveDescriptor(threadDescriptor: ChanDescriptor.ThreadDescriptor): ArchiveDescriptor? {
        if (!threadDescriptor.boardDescriptor.siteDescriptor.is4chan()) {
            // Only 4chan archives are supported
            return null
        }

        val suitableArchives = archives
                .filter { archiveData ->
                    archiveData.supportedBoards.contains(threadDescriptor.boardCode())
                }

        if (suitableArchives.isEmpty()) {
            return null
        }

        // Best possible archive is the one that stores media files
        val bestPossibleArchiveData = suitableArchives.firstOrNull { archiveData ->
            archiveData.supportedFiles.contains(threadDescriptor.boardCode())
        }

        if (bestPossibleArchiveData != null) {
            return ArchiveDescriptor(
                    bestPossibleArchiveData.name,
                    bestPossibleArchiveData.domain
            )
        }

        // If there are no archives that store media for this board then select one at random
        val randomArchive = suitableArchives.randomOrNull(random)
        if (randomArchive != null) {
            return ArchiveDescriptor(
                    randomArchive.name,
                    randomArchive.domain
            )
        }

        // For some reason we couldn't find archive for this board at all
        return null
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

    fun doesArchiveStoreThumbnails(archiveDescriptor: ArchiveDescriptor): Boolean {
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
        return appContext.assets.use { assetManager ->
            return@use assetManager.open(ARCHIVES_JSON_FILE_NAME).use { inputStream ->
                return@use gson.fromJson<Array<ArchiveData>>(
                        JsonReader(InputStreamReader(inputStream)),
                        Array<ArchiveData>::class.java
                ).toList()
            }
        }
    }

    class ArchiveDescriptor(
            val name: String,
            val domain: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ArchiveDescriptor) return false

            if (domain != other.domain) return false

            return true
        }

        override fun hashCode(): Int {
            return domain.hashCode()
        }

        override fun toString(): String {
            return "ArchiveDescriptor(name='$name', domain='$domain')"
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

    companion object {
        private const val TAG = "ArchivesManager"
        private const val ARCHIVES_JSON_FILE_NAME = "archives.json"
        private const val FOOLFUUKA_THREAD_ENDPOINT_FORMAT = "https://%s/_/api/chan/thread/?board=%s&num=%d"
        private const val FOOLFUUKA_POST_ENDPOINT_FORMAT = "https://%s/_/api/chan/post/?board=%s&num=%d"
    }
}