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
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.sites.Lainchan
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach
import com.github.k1rakishou.chan.features.drawer.MainController
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2Service
import com.github.k1rakishou.chan.ui.controller.BrowseController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.NotificationConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import dagger.Lazy


class StartActivityStartupHandlerHelper(
  private val historyNavigationManager: Lazy<HistoryNavigationManager>,
  private val siteManager: Lazy<SiteManager>,
  private val boardManager: Lazy<BoardManager>,
  private val bookmarksManager: Lazy<BookmarksManager>,
  private val chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>,
  private val siteResolver: Lazy<SiteResolver>
) {
  // We only want to load a board upon the application start when nothing is loaded yet. Afterward
  // we don't want to do that anymore so that we won't override the currently opened board when
  // opening threads from notifications.
  private var needToLoadBoard = true

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

    needToLoadBoard = false
  }

  private suspend fun restoreFresh(allowedToOpenThread: Boolean) {
    Logger.d(TAG, "restoreFresh(allowedToOpenThread=$allowedToOpenThread)")

    val sm = siteManager.get().apply { awaitUntilInitialized() }
    if (!sm.areSitesSetup()) {
      Logger.d(TAG, "restoreFresh() Sites are not setup, showSitesNotSetup()")
      browseController?.showSitesNotSetup()
      return
    }

    val catalogToOpen = getCatalogToOpen()
    val threadToOpen = if (allowedToOpenThread) {
      getThreadToOpen()
    } else {
      null
    }

    Logger.d(TAG, "restoreFresh() getBoardToOpen returned ${catalogToOpen}, " +
        "getThreadToOpen returned ${threadToOpen}")

    if (catalogToOpen != null) {
      browseController?.showCatalog(catalogToOpen, false)
    } else {
      browseController?.loadWithDefaultBoard()
    }

    if (threadToOpen != null) {
      startActivityCallbacks?.loadThread(threadToOpen, animated = false)
    }
  }

  private suspend fun getThreadToOpen(): ChanDescriptor.ThreadDescriptor? {
    val loadLastOpenedThreadUponAppStart = ChanSettings.loadLastOpenedThreadUponAppStart.get()
    Logger.d(TAG, "getThreadToOpen(), loadLastOpenedThreadUponAppStart=$loadLastOpenedThreadUponAppStart")

    if (loadLastOpenedThreadUponAppStart) {
      val threadDescriptor = historyNavigationManager.get().getNavElementAtTop()
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

  private suspend fun getCatalogToOpen(): ChanDescriptor.ICatalogDescriptor? {
    // TODO(KurobaEx): CompositeCatalogDescriptor
    if (true) {
      return ChanDescriptor.CompositeCatalogDescriptor(
        listOf(
          ChanDescriptor.CatalogDescriptor.create(BoardDescriptor.Companion.create(Chan4.SITE_DESCRIPTOR, "a")),
          ChanDescriptor.CatalogDescriptor.create(BoardDescriptor.Companion.create(Dvach.SITE_DESCRIPTOR, "a")),
          ChanDescriptor.CatalogDescriptor.create(BoardDescriptor.Companion.create(Lainchan.SITE_DESCRIPTOR, "lain")),
        )
      )
    }

    val loadLastOpenedBoardUponAppStart = ChanSettings.loadLastOpenedBoardUponAppStart.get()
    Logger.d(TAG, "getBoardToOpen(), loadLastOpenedBoardUponAppStart=$loadLastOpenedBoardUponAppStart")

    val boardDescriptor = if (loadLastOpenedBoardUponAppStart) {
      val boardDescriptor = historyNavigationManager.get().getFirstCatalogNavElement()
        ?.descriptor()
        ?.boardDescriptor()

      if (boardDescriptor == null) {
        Logger.d(TAG, "getBoardToOpen() -> historyNavigationManager.getFirstCatalogNavElement() -> null")
        return null
      }

      if (!checkSiteExistsAndActive("getBoardToOpen()", boardDescriptor)) {
        return null
      }

      boardDescriptor
    } else {
      val sm = siteManager.get().apply { awaitUntilInitialized() }
      val bm = boardManager.get().apply { awaitUntilInitialized() }

      sm.firstSiteDescriptor()?.let { firstSiteDescriptor -> bm.firstBoardDescriptor(firstSiteDescriptor) }
    }

    if (boardDescriptor == null) {
      return null
    }

    return ChanDescriptor.CatalogDescriptor.create(boardDescriptor)
  }

  private suspend fun checkSiteExistsAndActive(tag: String, boardDescriptor: BoardDescriptor): Boolean {
    val sm = siteManager.get().apply { awaitUntilInitialized() }

    val site = sm.bySiteDescriptor(boardDescriptor.siteDescriptor)
    if (site == null) {
      Logger.d(TAG, "$tag siteManager.bySiteDescriptor(${boardDescriptor.siteDescriptor}) -> null")
      return false
    }

    if (!sm.isSiteActive(boardDescriptor.siteDescriptor)) {
      Logger.d(TAG, "$tag siteManager.isSiteActive(${boardDescriptor.siteDescriptor}) -> false")
      return false
    }

    return true
  }

  suspend fun onNewIntentInternal(intent: Intent?): Boolean {
    if (intent == null || context == null) {
      return false
    }

    val action = intent.action
      ?: return false

    if (!isKnownAction(action)) {
      return false
    }

    Logger.d(TAG, "onNewIntentInternal called, action=${action}")

    bookmarksManager.get().awaitUntilInitialized()

    when {
      intent.hasExtra(NotificationConstants.ReplyNotifications.R_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY) -> {
        return replyNotificationClicked(intent)
      }
      intent.hasExtra(NotificationConstants.LastPageNotifications.LP_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY) -> {
        return lastPageNotificationClicked(intent)
      }
      intent.hasExtra(NotificationConstants.PostingServiceNotifications.NOTIFICATION_CLICK_CHAN_DESCRIPTOR_KEY) -> {
        return postingNotificationClicked(intent)
      }
      action == Intent.ACTION_VIEW -> {
        return restoreFromUrl(intent)
      }
      action == ImageSaverV2Service.ACTION_TYPE_NAVIGATE -> {
        return onImageSaverNotificationNavigateClicked(intent)
      }
      action == ImageSaverV2Service.ACTION_TYPE_RESOLVE_DUPLICATES -> {
        return onImageSaverNotificationResolveDuplicatesClicked(intent)
      }
      action == ImageSaverV2Service.ACTION_TYPE_SHOW_IMAGE_SAVER_SETTINGS -> {
        return onImageSaverNotificationSettingsClicked(intent)
      }
      else -> return false
    }
  }

  private fun onImageSaverNotificationSettingsClicked(intent: Intent): Boolean {
    val extras = intent.extras
      ?: return false

    val uniqueId = extras.getString(ImageSaverV2Service.UNIQUE_ID)
    if (uniqueId != null) {
      mainController?.showImageSaverV2OptionsController(uniqueId)
    }

    // Always return false here since we don't want to override the default "restore app"
    // mechanism here
    return false
  }

  private fun onImageSaverNotificationResolveDuplicatesClicked(intent: Intent): Boolean {
    val extras = intent.extras
      ?: return false

    val uniqueId = extras.getString(ImageSaverV2Service.UNIQUE_ID)
    val imageSaverOptionsJson = extras.getString(ImageSaverV2Service.IMAGE_SAVER_OPTIONS)

    if (uniqueId != null && imageSaverOptionsJson != null) {
      mainController?.showResolveDuplicateImagesController(uniqueId, imageSaverOptionsJson)
    }

    // Always return false here since we don't want to override the default "restore app"
    // mechanism here
    return false
  }

  private fun onImageSaverNotificationNavigateClicked(intent: Intent): Boolean {
    val extras = intent.extras
      ?: return false

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

  private fun hideImageSaverNotification(context: Context, uniqueId: String) {
    val notificationManagerCompat = NotificationManagerCompat.from(context)
    notificationManagerCompat.cancel(uniqueId, uniqueId.hashCode())
  }

  private suspend fun restoreFromUrl(intent: Intent?): Boolean {
    val data = intent?.data
      ?: return false

    Logger.d(TAG, "restoreFromUrl(), url = $data")

    val url = data.toString()
    val chanDescriptorResult = siteResolver.get().resolveChanDescriptorForUrl(url)

    if (chanDescriptorResult == null) {
      showToast(context, getString(R.string.open_link_not_matched, url), Toast.LENGTH_LONG)

      Logger.d(TAG, "restoreFromUrl() failure")
      return false
    }

    Logger.d(TAG, "chanDescriptorResult.descriptor = ${chanDescriptorResult.chanDescriptor}, " +
        "markedPostNo = ${chanDescriptorResult.markedPostNo}")

    val chanDescriptor = chanDescriptorResult.chanDescriptor
    val boardDescriptor = chanDescriptor.boardDescriptor()
    val catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(boardDescriptor)

    browseController?.setCatalog(catalogDescriptor)

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      if (chanDescriptorResult.markedPostNo > 0L) {
        chanThreadViewableInfoManager.get().update(chanDescriptor, true) { chanThreadViewableInfo ->
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

    val catalogThreadPair = resolveChanState(chanState)

    val catalogDescriptor = catalogThreadPair.first
    if (catalogDescriptor == null) {
      return false
    }

    browseController?.setCatalog(catalogDescriptor)

    val threadDescriptor = catalogThreadPair.second
    if (threadDescriptor != null) {
      browseController?.showThread(threadDescriptor, false)
    }

    return true
  }

  private suspend fun resolveChanState(
    state: ChanState
  ): Pair<ChanDescriptor.ICatalogDescriptor?, ChanDescriptor.ThreadDescriptor?> {
    val catalogDescriptor = resolveChanDescriptor(state.board) as? ChanDescriptor.ICatalogDescriptor
    val threadDescriptor = resolveChanDescriptor(state.thread) as? ChanDescriptor.ThreadDescriptor

    return Pair(catalogDescriptor, threadDescriptor)
  }

  private suspend fun resolveChanDescriptor(descriptorParcelable: DescriptorParcelable): ChanDescriptor? {
    val chanDescriptor = when {
      descriptorParcelable.isThreadDescriptor() -> {
        ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(descriptorParcelable)
      }
      descriptorParcelable.isCatalogDescriptor() -> {
        ChanDescriptor.CatalogDescriptor.fromDescriptorParcelable(descriptorParcelable)
      }
      descriptorParcelable.isCompositeCatalogDescriptor() -> {
        ChanDescriptor.CompositeCatalogDescriptor.fromDescriptorParcelable(descriptorParcelable)
      }
      else -> {
        error("Unknown descriptorParcelable type: ${descriptorParcelable.javaClass.simpleName}")
      }
    }

    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return chanDescriptor
    }

    val sm = siteManager.get().apply { awaitUntilInitialized() }

    sm.bySiteDescriptor(chanDescriptor.siteDescriptor())
      ?: return null

    val bm = boardManager.get().apply { awaitUntilInitialized() }

    bm.byBoardDescriptor(chanDescriptor.boardDescriptor())
      ?: return null

    return chanDescriptor
  }

  private fun isKnownAction(action: String): Boolean {
    return when (action) {
      NotificationConstants.LAST_PAGE_NOTIFICATION_ACTION -> true
      NotificationConstants.REPLY_NOTIFICATION_ACTION -> true
      NotificationConstants.POSTING_NOTIFICATION_ACTION -> true
      ImageSaverV2Service.ACTION_TYPE_NAVIGATE -> true
      ImageSaverV2Service.ACTION_TYPE_RESOLVE_DUPLICATES -> true
      ImageSaverV2Service.ACTION_TYPE_RETRY_FAILED -> true
      ImageSaverV2Service.ACTION_TYPE_SHOW_IMAGE_SAVER_SETTINGS -> true
      Intent.ACTION_VIEW -> true
      else -> false
    }
  }

  private suspend fun postingNotificationClicked(intent: Intent): Boolean {
    val extras = intent.extras
      ?: return false

    val descriptorParcelable = extras.getParcelable<DescriptorParcelable>(
      NotificationConstants.PostingServiceNotifications.NOTIFICATION_CLICK_CHAN_DESCRIPTOR_KEY
    )

    if (descriptorParcelable == null) {
      return false
    }

    val chanDescriptor = descriptorParcelable.toChanDescriptor()
    Logger.d(TAG, "onNewIntent() posting notification clicked, chanDescriptor=$chanDescriptor")

    when (chanDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> {
        val catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(chanDescriptor.boardDescriptor)
        browseController?.showCatalog(catalogDescriptor, animated = false)
      }
      is ChanDescriptor.ThreadDescriptor -> {
        if (needToLoadBoard) {
          val boardToOpen = getCatalogToOpen()
          if (boardToOpen != null) {
            browseController?.showCatalog(boardToOpen, animated = false)
          } else {
            browseController?.loadWithDefaultBoard()
          }
        }

        startActivityCallbacks?.loadThread(chanDescriptor, animated = false)
      }
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        browseController?.showCatalog(chanDescriptor, animated = false)
      }
    }

    return true
  }

  private suspend fun lastPageNotificationClicked(intent: Intent): Boolean {
    val extras = intent.extras
      ?: return false

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

  private suspend fun replyNotificationClicked(intent: Intent): Boolean {
    val extras = intent.extras
      ?: return false

    val threadDescriptors = extras.getParcelableArrayList<DescriptorParcelable>(
      NotificationConstants.ReplyNotifications.R_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY
    )?.map { it -> ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(it) }

    if (mainController == null || threadDescriptors.isNullOrEmpty()) {
      return false
    }

    Logger.d(TAG, "onNewIntent() reply notification clicked, " +
        "marking as seen ${threadDescriptors.size} bookmarks")

    openThreadFromNotificationOrBookmarksController(threadDescriptors)

    val updatedBookmarkDescriptors = bookmarksManager.get().updateBookmarksNoPersist(threadDescriptors) { threadBookmark ->
      threadBookmark.markAsSeenAllReplies()
    }
    bookmarksManager.get().persistBookmarksManually(updatedBookmarkDescriptors)

    return true
  }

  private suspend fun openThreadFromNotificationOrBookmarksController(
    threadDescriptors: List<ChanDescriptor.ThreadDescriptor>
  ) {
    if (needToLoadBoard) {
      val boardToOpen = getCatalogToOpen()
      if (boardToOpen != null) {
        browseController?.showCatalog(boardToOpen, false)
      } else {
        browseController?.loadWithDefaultBoard()
      }
    }

    if (threadDescriptors.size == 1) {
      startActivityCallbacks?.loadThread(threadDescriptors.first(), animated = false)
    } else {
      mainController?.openBookmarksController(threadDescriptors)
    }
  }

  interface StartActivityCallbacks {
    fun loadThreadAndMarkPost(postDescriptor: PostDescriptor, animated: Boolean)
    fun loadThread(threadDescriptor: ChanDescriptor.ThreadDescriptor, animated: Boolean)
  }

  companion object {
    private const val TAG = "StartActivityIntentHandlerHelper"
  }

}