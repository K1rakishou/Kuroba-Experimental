package com.github.k1rakishou.chan.ui.cell

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.concurrency.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.concurrency.ThrottleFirstCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.board.pages.BoardPage
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.thread.ChanThread
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.util.ChanPostUtils
import com.github.k1rakishou.v2.KurobaSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

class ThreadStatusCell(
  context: Context,
  attrs: AttributeSet
) : LinearLayout(context, attrs), View.OnClickListener, ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var kurobaSettings: KurobaSettings
  @Inject
  lateinit var appResources: AppResources
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var chanThreadManager: ChanThreadManager
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var threadDownloadManager: ThreadDownloadManager

  private lateinit var statusCellText: TextView

  private var callback: Callback? = null
  private var errorMessage: String? = null

  private val job = SupervisorJob()
  private val scope = CoroutineScope(job + Dispatchers.Main)
  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(scope)
  private val clickThrottler = ThrottleFirstCoroutineExecutor(scope)

  private var updateJob: Job? = null

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    setBackgroundResource(R.drawable.item_background)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    themeEngine.addListener(this)
    schedule()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    themeEngine.removeListener(this)
    unschedule()
  }

  override fun onFinishInflate() {
    super.onFinishInflate()

    statusCellText = findViewById(R.id.text)
    statusCellText.typeface = themeEngine.chanTheme.mainFont

    setOnClickListener(this)
    onThemeChanged()
  }

  override fun onThemeChanged() {
    statusCellText.setTextColor(themeEngine.chanTheme.textColorSecondary)
  }

  override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
    super.onWindowFocusChanged(hasWindowFocus)

    if (hasWindowFocus) {
      schedule()
    } else {
      unschedule()
    }
  }

  private fun schedule() {
    unschedule()

    updateJob = scope.launch {
      update()
    }
  }

  private fun unschedule() {
    updateJob?.cancel()
    updateJob = null
  }

  override fun onClick(v: View) {
    clickThrottler.post(
      timeout = 1_000L,
      func = {
        errorMessage = null

        if (callback?.currentChanDescriptor != null) {
          callback?.onListStatusClicked()
        }

        update()
      }
    )
  }

  fun setCallback(callback: Callback?) {
    this.callback = callback
  }

  fun setError(error: String?) {
    this.errorMessage = error

    if (error == null) {
      schedule()
    }
  }

  fun update() {
    rendezvousCoroutineExecutor.post {
      updateInternal()
    }
  }

  @SuppressLint("SetTextI18n")
  suspend fun updateInternal() {
    val chanDescriptor = callback?.currentChanDescriptor
      ?: return

    if (chanDescriptor.isCatalogDescriptor()) {
      unschedule()
      updateCatalogStatusCell()
    } else {
      updateThreadStatusCell(chanDescriptor, callback)
    }
  }

  private fun updateCatalogStatusCell() {
    if (errorMessage != null) {
      val actualMessage = errorMessage.takeIf { it.isNotNullNorBlank() }
        ?: appResources.string(R.string.thread_status_cell_unknown_error)

      statusCellText.text = buildString {
        appendLine(appResources.string(R.string.catalog_refresh_error_text_title))
        appendLine("\"${actualMessage}\"")
        appendLine(appResources.string(R.string.catalog_click_to_refresh))
      }

      return
    }

    statusCellText.text = buildString {
      appendLine(appResources.string(R.string.catalog_refresh_title))
      appendLine(appResources.string(R.string.catalog_click_to_refresh))
    }
  }

  private suspend fun updateThreadStatusCell(
    chanDescriptor: ChanDescriptor,
    callback: Callback?
  ) {
    if (errorMessage != null) {
      val actualMessage = errorMessage.takeIf { it.isNotNullNorBlank() }
        ?: appResources.string(R.string.thread_status_cell_unknown_error)

      statusCellText.text = buildString {
        appendLine(appResources.string(R.string.thread_refresh_error_text_title))
        appendLine("\"${actualMessage}\"")
        appendLine(appResources.string(R.string.thread_refresh_bar_inactive))
      }

      return
    }

    chanDescriptor as ChanDescriptor.ThreadDescriptor

    if (chanThreadManager.isThreadLockCurrentlyLocked(chanDescriptor)) {
      // Since we update ThreadStatusCell every second there might be times when the ChanThread object
      // is being updated with new posts and there are a lot of posts so it may hold the lock for
      // quite some time. So to avoid freezing the whole app because of that we need to first check
      // whether the ChanThread lock is locked and skip this update if it's locked.
      return
    }

    val chanThread = chanThreadManager.getChanThread(chanDescriptor)
      ?: return

    val canUpdate = kurobaSettings.application.autoRefreshThread.read() && chanThread.canUpdateThread()
    val builder = SpannableStringBuilder()
      .apply { appendLine() }

    if (appendThreadStatusPart(chanThread, builder)) {
      builder.appendLine()
    }

    if (appendThreadRefreshPart(chanThread, builder)) {
      builder.appendLine()
    }

    val op = chanThread.getOriginalPost()
    if (op != null) {
      val board = boardManager.byBoardDescriptor(op.postDescriptor.boardDescriptor())
      val threadStatistics = ChanPostUtils.getThreadStatistics(
        chanThread = chanThread,
        op = op,
        board = board,
        boardPage = callback?.getPage(op.postDescriptor)
      )

      builder
        .append(threadStatistics)
        .appendLine()
    }

    if (archivesManager.isSiteArchive(chanDescriptor.siteDescriptor())) {
      builder
        .append(appResources.string(R.string.controller_bookmarks_bookmark_of_archived_thread))
    }

    appendThreadDownloaderStats(chanDescriptor, builder)

    builder.appendLine()

    statusCellText.text = builder

    if (canUpdate) {
      delay(UPDATE_INTERVAL_MS)
      schedule()
    }
  }

  private suspend fun appendThreadDownloaderStats(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    builder: SpannableStringBuilder
  ) {
    when (threadDownloadManager.getStatus(threadDescriptor)) {
      ThreadDownload.Status.Running -> {
        builder
          .append(appResources.string(R.string.thread_status_downloading_running))
      }
      ThreadDownload.Status.Stopped -> {
        builder
          .append(appResources.string(R.string.thread_status_downloading_stopped))
      }
      ThreadDownload.Status.Completed -> {
        builder
          .append(appResources.string(R.string.thread_status_downloaded))
      }
      else -> {
        // no-op
      }
    }
  }

  private suspend fun appendThreadRefreshPart(
    chanThread: ChanThread,
    builder: SpannableStringBuilder
  ): Boolean {
    if (!chanThread.canUpdateThread()) {
      return false
    }

    val timeSeconds = callback?.timeUntilLoadMoreMs()?.div(1000L)
      ?: return false

    when {
      callback?.isWatching() == false -> {
        builder.append(appResources.string(R.string.thread_refresh_bar_inactive))
      }
      timeSeconds <= 0 -> {
        builder.append(appResources.string(R.string.loading))
      }
      else -> {
        builder.append(appResources.string(R.string.thread_refresh_countdown, timeSeconds))
      }
    }

    return true
  }

  private fun appendThreadStatusPart(
    chanThread: ChanThread,
    builder: SpannableStringBuilder
  ): Boolean {
    when {
      chanThread.isArchived() -> {
        builder.append(appResources.string(R.string.thread_archived))
        return true
      }
      chanThread.isClosed() -> {
        builder.append(appResources.string(R.string.thread_closed))
        return true
      }
      chanThread.isDeleted() -> {
        builder.append(appResources.string(R.string.thread_deleted))
        return true
      }
    }

    return false
  }

  interface Callback {
    val currentChanDescriptor: ChanDescriptor?

    suspend fun timeUntilLoadMoreMs(): Long
    fun isWatching(): Boolean
    suspend fun getPage(originalPostDescriptor: PostDescriptor): BoardPage?
    fun onListStatusClicked()
  }

  companion object {
    private const val UPDATE_INTERVAL_MS = 1000L
  }

}