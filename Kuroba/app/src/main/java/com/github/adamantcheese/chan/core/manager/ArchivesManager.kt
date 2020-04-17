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

class ArchivesManager(
        private val appContext: Context,
        private val gson: Gson
) {
    private val archives by lazy { loadArchives() }

    fun getArchiveDescriptor(threadDescriptor: ChanDescriptor.ThreadDescriptor): ArchiveDescriptor? {
        if (!threadDescriptor.boardDescriptor.siteDescriptor.is4chan()) {
            // Only 4chan archives are supported
            return null
        }

        // TODO(archives): !!!!!!!!!!!!!!!!!!!!!
        val archiveData = archives.filter { archiveData ->
            archiveData.supportedBoards.contains(threadDescriptor.boardCode())
        }.firstOrNull { it.domain.contains("archived") }

        if (archiveData == null) {
            return null
        }

        return ArchiveDescriptor(
                archiveData.name,
                archiveData.domain
        )
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

    fun doesArchiveSupportsFilesForBoard(
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
        return archives.firstOrNull { archiveData ->
            return@firstOrNull archiveData.name == archiveDescriptor.name
                    && archiveData.domain == archiveDescriptor.domain
        }
    }

    private fun loadArchives(): Array<ArchiveData> {
        return appContext.assets.use { assetManager ->
            return@use assetManager.open(ARCHIVES_JSON_FILE_NAME).use { inputStream ->
                return@use gson.fromJson<Array<ArchiveData>>(
                        JsonReader(InputStreamReader(inputStream)),
                        Array<ArchiveData>::class.java
                )
            }
        }
    }

    data class ArchiveDescriptor(
            val name: String,
            val domain: String
    )

    data class ArchiveData(
            @SerializedName("name")
            val name: String,
            @SerializedName("domain")
            val domain: String,
            @SerializedName("boards")
            val supportedBoards: List<String>,
            @SerializedName("files")
            val supportedFiles: List<String>
    )

    companion object {
        private const val TAG = "ArchivesManager"
        private const val ARCHIVES_JSON_FILE_NAME = "archives.json"
        private const val FOOLFUUKA_THREAD_ENDPOINT_FORMAT = "https://%s/_/api/chan/thread/?board=%s&num=%d"
        private const val FOOLFUUKA_POST_ENDPOINT_FORMAT = "https://%s/_/api/chan/post/?board=%s&num=%d"
    }
}