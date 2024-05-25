package com.github.k1rakishou.chan.core.parser.repository

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.parser.usecase.CalculateParsedPostDataUseCase
import com.github.k1rakishou.chan.core.parser.ParsedPostDataContext
import com.github.k1rakishou.chan.core.parser.ParsedPostDataRaw
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.mutableSetWithCap
import com.github.k1rakishou.common.withLockNonCancellable
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import java.util.HashSet

interface ParsedPostDataRepository {
  suspend fun getOrParse(
    chanDescriptor: ChanDescriptor,
    chanPost: ChanPost,
    postIndex: Int,
    parsedPostDataContext: ParsedPostDataContext,
    chanTheme: ChanTheme,
    forced: Boolean
  ): ParsedPostDataRaw
}

class ParsedPostDataRepositoryImpl(
  private val appScope: CoroutineScope,
  private val calculateParsedPostDataUseCase: CalculateParsedPostDataUseCase
) : ParsedPostDataRepository {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val catalogParsedPostDataMap = mutableMapWithCap<PostDescriptor, ParsedPostDataRaw>(512)
  @GuardedBy("mutex")
  private val threadParsedPostDataMap = mutableMapWithCap<PostDescriptor, ParsedPostDataRaw>(2048)
  @GuardedBy("mutex")
  private val pendingPostUpdates = mutableMapOf<ChanDescriptor, HashSet<PostDescriptor>>()

  private val _parsedPostDataRawFlow = MutableSharedFlow<BatchOfParsedPostData>(extraBufferCapacity = Channel.UNLIMITED)
  val parsedPostDataRawFlow: SharedFlow<BatchOfParsedPostData>
    get() = _parsedPostDataRawFlow.asSharedFlow()

  private val updateNotifyDebouncer = DebouncingCoroutineExecutor(appScope)

  override suspend fun getOrParse(
    chanDescriptor: ChanDescriptor,
    chanPost: ChanPost,
    postIndex: Int,
    parsedPostDataContext: ParsedPostDataContext,
    chanTheme: ChanTheme,
    forced: Boolean
  ): ParsedPostDataRaw {
    val postDescriptor = chanPost.postDescriptor

    val oldParsedPostData = mutex.withLockNonCancellable {
      when (chanDescriptor) {
        is ChanDescriptor.ICatalogDescriptor -> catalogParsedPostDataMap[postDescriptor]
        is ChanDescriptor.ThreadDescriptor -> threadParsedPostDataMap[postDescriptor]
      }
    }

    if (!forced && oldParsedPostData != null) {
      return oldParsedPostData
    }

    val newParsedPostData = calculateParsedPostDataUseCase.calculate(
      postIndex = postIndex,
      chanPost = chanPost,
      parsedPostDataContext = parsedPostDataContext,
      chanTheme = chanTheme
    )

    mutex.withLockNonCancellable {
      when (chanDescriptor) {
        is ChanDescriptor.ICatalogDescriptor -> catalogParsedPostDataMap[postDescriptor] = newParsedPostData
        is ChanDescriptor.ThreadDescriptor -> threadParsedPostDataMap[postDescriptor] = newParsedPostData
      }
    }

    notifyListenersPostDataUpdated(chanDescriptor, chanPost.postDescriptor)
    return newParsedPostData
  }

  private suspend fun notifyListenersPostDataUpdated(chanDescriptor: ChanDescriptor, postDescriptor: PostDescriptor) {
    mutex.withLockNonCancellable {
      val pendingUpdatesSet = pendingPostUpdates.getOrPut(
        key = chanDescriptor,
        defaultValue = { mutableSetWithCap<PostDescriptor>(128) }
      )
      pendingUpdatesSet += postDescriptor
    }

    updateNotifyDebouncer.post(timeout = 32L) {
      mutex.withLockNonCancellable {
        val pendingPostUpdatesCopy = pendingPostUpdates.toMap()
        pendingPostUpdates.clear()

        pendingPostUpdatesCopy.entries.forEach { (chanDescriptor, postDescriptors) ->
          if (postDescriptors.isEmpty()) {
            return@withLockNonCancellable
          }

          val batchOfParsedPostData = BatchOfParsedPostData(
            chanDescriptor = chanDescriptor,
            postDescriptors = postDescriptors
          )

          Logger.debug(TAG) {
            "notifyListenersPostDataUpdated() chanDescriptor: ${chanDescriptor}, " +
              "postDescriptors: ${postDescriptors.size}"
          }

          _parsedPostDataRawFlow.emit(batchOfParsedPostData)
        }
      }
    }
  }

  data class BatchOfParsedPostData(
    val chanDescriptor: ChanDescriptor,
    val postDescriptors: Set<PostDescriptor>
  )

  companion object {
    private const val TAG = "ParsedPostDataRepositoryImpl"
  }

}