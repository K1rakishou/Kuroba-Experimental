package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.descriptor.ThreadDescriptor
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import java.io.InputStreamReader
import java.util.*
import kotlin.random.Random

class ArchivesManager(
        private val gson: Gson
) {
    private val random = Random(System.currentTimeMillis())
    private val archives by lazy { loadArchives() }

    fun getRequestLinkForThread(threadDescriptor: ThreadDescriptor): String? {
        require(threadDescriptor.boardDescriptor.siteDescriptor.is4chan())

        val archiveData = getArchiveDataForThreadDescriptor(threadDescriptor)
                ?: return null

        return String.format(
                Locale.ENGLISH,
                FOOLFUUKA_THREAD_ENDPOINT_FORMAT,
                archiveData.domain,
                threadDescriptor.boardCode(),
                threadDescriptor.opNo
        )
    }

    fun getRequestLinkForPost(postDescriptor: PostDescriptor): String? {
        require(postDescriptor.threadDescriptor.boardDescriptor.siteDescriptor.is4chan())

        val archiveData = getArchiveDataForThreadDescriptor(postDescriptor.threadDescriptor)
                ?: return null

        return String.format(
                Locale.ENGLISH,
                FOOLFUUKA_POST_ENDPOINT_FORMAT,
                archiveData.domain,
                postDescriptor.threadDescriptor.boardCode(),
                postDescriptor.postNo
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun getArchiveDataForThreadDescriptor(threadDescriptor: ThreadDescriptor): ArchiveData? {
        val archiveData = archives.filter { archiveData ->
            archiveData.supportedBoards.contains(threadDescriptor.boardCode())
        }.randomOrNull(random)

        if (archiveData == null) {
            Logger.d(TAG, "No archive found for board (${threadDescriptor.boardCode()})")
            return null
        }

        return archiveData
    }

    private fun loadArchives(): Array<ArchiveData> {
        return AndroidUtils.getAppContext().assets.use { assetManager ->
            return@use assetManager.open(ARCHIVES_JSON_FILE_NAME).use { inputStream ->
                return@use gson.fromJson<Array<ArchiveData>>(
                        JsonReader(InputStreamReader(inputStream)),
                        Array<ArchiveData>::class.java
                )
            }
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
            val supportedBoardsWithFiles: List<String>
    )

    companion object {
        private const val TAG = "ArchivesManager2"
        private const val ARCHIVES_JSON_FILE_NAME = "archives.json"
        private const val FOOLFUUKA_THREAD_ENDPOINT_FORMAT = "https://%s/_/api/chan/thread/?board=%s&num=%d"
        private const val FOOLFUUKA_POST_ENDPOINT_FORMAT = "https://%s/_/api/chan/post/?board=%s&num=%d"
    }
}