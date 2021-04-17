package com.github.k1rakishou.chan.core.helper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.util.Pair
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.activity.ChanState
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.features.drawer.MainController
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2Service
import com.github.k1rakishou.chan.ui.controller.BrowseController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.NotificationConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable


class StartActivityStartupHandlerHelper(
  private val historyNavigationManager: HistoryNavigationManager,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val bookmarksManager: BookmarksManager,
  private val chanFilterManager: ChanFilterManager,
  private val chanThreadViewableInfoManager: ChanThreadViewableInfoManager,
  private val siteResolver: SiteResolver
) {
  private var context: Context? = null
  private var browseController: BrowseController? = null
  private var mainController: MainController? = null
  private var startActivityCallbacks: StartActivityCallbacks? = null

  fun onCreate(
    context: Context,
    browseController: BrowseController,
    mainController: MainController,
    startActivityCallbacks: StartActivityCallbacks
  ) {
    this.context = context
    this.browseController = browseController
    this.mainController = mainController
    this.startActivityCallbacks = startActivityCallbacks
  }

  fun onDestroy() {
    this.context = null
    this.browseController = null
    this.mainController = null
    this.startActivityCallbacks = null
  }

  suspend fun setupFromStateOrFreshLaunch(intent: Intent?, savedInstanceState: Bundle?) {
    historyNavigationManager.awaitUntilInitialized()
    siteManager.awaitUntilInitialized()
    boardManager.awaitUntilInitialized()
    bookmarksManager.awaitUntilInitialized()
    chanFilterManager.awaitUntilInitialized()

    Logger.d(TAG, "setupFromStateOrFreshLaunch(intent==null: ${intent == null}, " +
        "savedInstanceState==null: ${savedInstanceState == null})")

    val newIntentHandled = onNewIntentInternal(intent)
    Logger.d(TAG, "onNewIntentInternal() -> $newIntentHandled")

    if (newIntentHandled) {
      //  This means that we handle the intent and either opened a thread from notification or opened
      //  bookmark controller.
      return
    }

    val handled = if (savedInstanceState != null) {
      restoreFromSavedState(savedInstanceState)
    } else {
      restoreFromUrl(intent)
    }

    // Not from a state or from an url, launch the setup controller if no boards are setup up yet,
    // otherwise load the default saved board.
    if (!handled) {
      restoreFresh(allowedToOpenThread = true)
    }
  }

  private suspend fun restoreFresh(allowedToOpenThread: Boolean) {
    Logger.d(TAG, "restoreFresh(allowedToOpenThread=$allowedToOpenThread)")

    if (!siteManager.areSitesSetup()) {
      Logger.d(TAG, "restoreFresh() Sites are not setup, showSitesNotSetup()")
      browseController?.showSitesNotSetup()
      return
    }

    val boardToOpen = getBoardToOpen()
    val threadToOpen = if (allowedToOpenThread) {
      getThreadToOpen()
    } else {
      null
    }

    Logger.d(TAG, "restoreFresh() getBoardToOpen returned ${boardToOpen}, " +
        "getThreadToOpen returned ${threadToOpen}")

    if (boardToOpen != null) {
      browseController?.showBoard(boardToOpen, false)
    } else {
      browseController?.loadWithDefaultBoard()
    }

    if (threadToOpen != null) {
      startActivityCallbacks?.loadThread(threadToOpen, animated = false)
    }
  }

  private fun getThreadToOpen(): ChanDescriptor.ThreadDescriptor? {
    val loadLastOpenedThreadUponAppStart = ChanSettings.loadLastOpenedThreadUponAppStart.get()
    Logger.d(TAG, "getThreadToOpen(), loadLastOpenedThreadUponAppStart=$loadLastOpenedThreadUponAppStart")

    if (loadLastOpenedThreadUponAppStart) {
      val threadDescriptor = historyNavigationManager.getNavElementAtTop()
        ?.descriptor()
        ?.threadDescriptorOrNull()

      if (threadDescriptor == null) {
        Logger.d(TAG, "getThreadToOpen() -> historyNavigationManager.getNavElementAtTop() -> null")
        return null
      }

      val boardDescriptor = threadDescriptor.boardDescriptor

      if (!checkSiteExistsAndActive("getThreadToOpen()", boardDescriptor)) {
        return null
      }

      return threadDescriptor
    }

    return null
  }

  private fun getBoardToOpen(): BoardDescriptor? {
    val loadLastOpenedBoardUponAppStart = ChanSettings.loadLastOpenedBoardUponAppStart.get()
    Logger.d(TAG, "getBoardToOpen(), loadLastOpenedBoardUponAppStart=$loadLastOpenedBoardUponAppStart")

    if (loadLastOpenedBoardUponAppStart) {
      val boardDescriptor = historyNavigationManager.getFirstCatalogNavElement()
        ?.descriptor()
        ?.boardDescriptor()

      if (boardDescriptor == null) {
        Logger.d(TAG, "getBoardToOpen() -> historyNavigationManager.getFirstCatalogNavElement() -> null")
        return null
      }

      if (!checkSiteExistsAndActive("getBoardToOpen()", boardDescriptor)) {
        return null
      }

      return boardDescriptor
    }

    return siteManager.firstSiteDescriptor()?.let { firstSiteDescriptor ->
      return@let boardManager.firstBoardDescriptor(firstSiteDescriptor)
    }
  }

  private fun checkSiteExistsAndActive(tag: String, boardDescriptor: BoardDescriptor): Boolean {
    val site = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
    if (site == null) {
      Logger.d(TAG, "$tag siteManager.bySiteDescriptor(${boardDescriptor.siteDescriptor}) -> null")
      return false
    }

    if (!siteManager.isSiteActive(boardDescriptor.siteDescriptor)) {
      Logger.d(TAG, "$tag siteManager.isSiteActive(${boardDescriptor.siteDescriptor}) -> false")
      return false
    }

    return true
  }

  suspend fun onNewIntentInternal(intent: Intent?): Boolean {
    if (intent == null || context == null) {
      return false
    }

    val extras = intent.extras
      ?: return false
    val action = intent.action
      ?: return false

    if (!isKnownAction(action)) {
      return false
    }

    Logger.d(TAG, "onNewIntentInternal called, action=${action}")

    siteManager.awaitUntilInitialized()
    boardManager.awaitUntilInitialized()
    bookmarksManager.awaitUntilInitialized()

    when {
      intent.hasExtra(NotificationConstants.ReplyNotifications.R_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY) -> {
        return replyNotificationClicked(extras)
      }
      intent.hasExtra(NotificationConstants.LastPageNotifications.LP_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY) -> {
        return lastPageNotificationClicked(extras)
      }
      action == Intent.ACTION_VIEW -> {
        return restoreFromUrl(intent)
      }
      action == ImageSaverV2Service.ACTION_TYPE_NAVIGATE -> {
        val outputDirUri = extras.getString(ImageSaverV2Service.OUTPUT_DIR_URI)
          ?.let { uriRaw -> Uri.parse(uriRaw) }

        extras.getString(ImageSaverV2Service.UNIQUE_ID)?.let { uniqueId ->
          hideImageSaverNotification(context!!, uniqueId)
        }

        if (outputDirUri != null) {
          val newIntent = Intent(Intent.ACTION_VIEW)
          newIntent.setDataAndType(outputDirUri, DocumentsContract.Document.MIME_TYPE_DIR)

          AppModuleAndroidUtils.openIntent(newIntent)
        }

        // Always return false here since we don't want to override the default "restore app"
        // mechanism here
        return false
      }
      action == ImageSaverV2Service.ACTION_TYPE_RESOLVE_DUPLICATES -> {
        val uniqueId = extras.getString(ImageSaverV2Service.UNIQUE_ID)
        val imageSaverOptionsJson = extras.getString(ImageSaverV2Service.IMAGE_SAVER_OPTIONS)

        if (uniqueId != null && imageSaverOptionsJson != null) {
          mainController?.showResolveDuplicateImagesController(uniqueId, imageSaverOptionsJson)
        }

        // Always return false here since we don't want to override the default "restore app"
        // mechanism here
        return false
      }
      action == ImageSaverV2Service.ACTION_TYPE_SHOW_IMAGE_SAVER_SETTINGS -> {
        val uniqueId = extras.getString(ImageSaverV2Service.UNIQUE_ID)
        if (uniqueId != null) {
          mainController?.showImageSaverV2OptionsController(uniqueId)
        }

        // Always return false here since we don't want to override the default "restore app"
        // mechanism here
        return false
      }
      else -> return false
    }
  }

  private fun hideImageSaverNotification(context: Context, uniqueId: String) {
    val notificationManagerCompat = NotificationManagerCompat.from(context)
    notificationManagerCompat.cancel(uniqueId, uniqueId.hashCode())
  }

  private suspend fun restoreFromUrl(intent: Intent?): Boolean {
    val data = intent?.data
      ?: return false

    Logger.d(TAG, "restoreFromUrl(), url = $data")

    val url = data.toString()
    val chanDescriptorResult = siteResolver.resolveChanDescriptorForUrl(url)

    if (chanDescriptorResult == null) {
      Toast.makeText(
        context,
        getString(R.string.open_link_not_matched, url),
        Toast.LENGTH_LONG
      ).show()

      Logger.d(TAG, "restoreFromUrl() failure")
      return false
    }

    Logger.d(TAG, "chanDescriptorResult.descriptor = ${chanDescriptorResult.chanDescriptor}, " +
        "markedPostNo = ${chanDescriptorResult.markedPostNo}")

    val chanDescriptor = chanDescriptorResult.chanDescriptor
    browseController?.setBoard(chanDescriptor.boardDescriptor())

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      if (chanDescriptorResult.markedPostNo > 0L) {
        chanThreadViewableInfoManager.update(chanDescriptor, true) { chanThreadViewableInfo ->
          chanThreadViewableInfo.markedPostNo = chanDescriptorResult.markedPostNo
        }
      }

      browseController?.showThread(chanDescriptor, false)
    }

    Logger.d(TAG, "restoreFromUrl() success")
    return true
  }

  private suspend fun restoreFromSavedState(savedInstanceState: Bundle): Boolean {
    Logger.d(TAG, "restoreFromSavedState()")

    // Restore the activity state from the previously saved state.
    val chanState = savedInstanceState.getParcelable<ChanState>(StartActivity.STATE_KEY)
    if (chanState == null) {
      Logger.w(TAG, "savedInstanceState was not null, but no ChanState was found!")
      return false
    }

    val boardThreadPair = resolveChanState(chanState)
    if (boardThreadPair.first == null) {
      return false
    }

    browseController?.setBoard(boardThreadPair.first!!)

    if (boardThreadPair.second != null) {
      browseController?.showThread(boardThreadPair.second!!, false)
    }

    return true
  }

  private fun resolveChanState(state: ChanState): Pair<BoardDescriptor?, ChanDescriptor.ThreadDescriptor?> {
    val boardDescriptor =
      (resolveChanDescriptor(state.board) as? ChanDescriptor.CatalogDescriptor)?.boardDescriptor
    val threadDescriptor =
      resolveChanDescriptor(state.thread) as? ChanDescriptor.ThreadDescriptor

    return Pair(boardDescriptor, threadDescriptor)
  }

  private fun resolveChanDescriptor(descriptorParcelable: DescriptorParcelable): ChanDescriptor? {
    val chanDescriptor = if (descriptorParcelable.isThreadDescriptor()) {
      ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(descriptorParcelable)
    } else {
      ChanDescriptor.CatalogDescriptor.fromDescriptorParcelable(descriptorParcelable)
    }

    siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
      ?: return null

    boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
      ?: return null

    return chanDescriptor
  }

  private fun isKnownAction(action: String): Boolean {
    return when (action) {
      NotificationConstants.LAST_PAGE_NOTIFICATION_ACTION -> true
      NotificationConstants.REPLY_NOTIFICATION_ACTION -> true
      ImageSaverV2Service.ACTION_TYPE_NAVIGATE -> true
      ImageSaverV2Service.ACTION_TYPE_RESOLVE_DUPLICATES -> true
      ImageSaverV2Service.ACTION_TYPE_RETRY_FAILED -> true
      ImageSaverV2Service.ACTION_TYPE_SHOW_IMAGE_SAVER_SETTINGS -> true
      Intent.ACTION_VIEW -> true
      else -> false
    }
  }

  private suspend fun lastPageNotificationClicked(extras: Bundle): Boolean {
    val threadDescriptors = extras.getParcelableArrayList<DescriptorParcelable>(
      NotificationConstants.LastPageNotifications.LP_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY
    )?.map { it -> ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(it) }

    if (mainController == null || threadDescriptors.isNullOrEmpty()) {
      return false
    }

    Logger.d(TAG, "onNewIntent() last page notification clicked, threads count = ${threadDescriptors.size}")

    openThreadFromNotificationOrBookmarksController(threadDescriptors)

    return true
  }

  private suspend fun replyNotificationClicked(extras: Bundle): Boolean {
    val threadDescriptors = extras.getParcelableArrayList<DescriptorParcelable>(
      NotificationConstants.ReplyNotifications.R_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY
    )?.map { it -> ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(it) }

    if (mainController == null || threadDescriptors.isNullOrEmpty()) {
      return false
    }

    Logger.d(TAG, "onNewIntent() reply notification clicked, " +
        "marking as seen ${threadDescriptors.size} bookmarks")

    openThreadFromNotificationOrBookmarksController(threadDescriptors)

    val updatedBookmarkDescriptors = bookmarksManager.updateBookmarksNoPersist(threadDescriptors) { threadBookmark ->
      threadBookmark.markAsSeenAllReplies()
    }
    bookmarksManager.persistBookmarksManually(updatedBookmarkDescriptors)

    return true
  }

  private suspend fun openThreadFromNotificationOrBookmarksController(
    threadDescriptors: List<ChanDescriptor.ThreadDescriptor>
  ) {
    val boardToOpen = getBoardToOpen()
    if (boardToOpen != null) {
      browseController?.showBoard(boardToOpen, false)
    } else {
      browseController?.loadWithDefaultBoard()
    }

    if (threadDescriptors.size == 1) {
      startActivityCallbacks?.loadThread(threadDescriptors.first(), animated = false)
    } else {
      mainController?.openBookmarksController(threadDescriptors)
    }
  }

  interface StartActivityCallbacks {
    suspend fun loadThread(threadDescriptor: ChanDescriptor.ThreadDescriptor, animated: Boolean)
  }

  companion object {
    private const val TAG = "StartActivityIntentHandlerHelper"
  }

}