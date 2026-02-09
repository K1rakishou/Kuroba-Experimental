package com.github.k1rakishou.chan.features.album

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.HapticFeedbackManager
import com.github.k1rakishou.chan.core.manager.RevealedSpoilerImagesManager
import com.github.k1rakishou.chan.core.usecase.FilterOutHiddenImagesUseCase
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2OptionsController
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2ServiceDelegate
import com.github.k1rakishou.chan.ui.compose.image.PostImageThumbnailKey
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.ui.compose.snackbar.manager.SnackbarManagerFactory
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.asKurobaMediaType
import com.github.k1rakishou.chan.utils.paramsOrNull
import com.github.k1rakishou.chan.utils.requireParams
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.toHashSetBy
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import com.github.k1rakishou.model.util.ChanPostUtils
import com.github.k1rakishou.persist_state.PersistableChanState
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

class AlbumViewControllerViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val appResources: AppResources,
  private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager,
  private val chanThreadManager: ChanThreadManager,
  private val chanCatalogSnapshotCache: ChanCatalogSnapshotCache,
  private val chanThreadsCache: ChanThreadsCache,
  private val snackbarManagerFactory: SnackbarManagerFactory,
  private val compositeCatalogManager: CompositeCatalogManager,
  private val filterOutHiddenImagesUseCase: FilterOutHiddenImagesUseCase,
  private val imageSaverV2: ImageSaverV2,
  private val imageSaverV2ServiceDelegate: ImageSaverV2ServiceDelegate,
  private val revealedSpoilerImagesManager: RevealedSpoilerImagesManager,
  private val hapticFeedbackManager: HapticFeedbackManager
) : BaseViewModel() {
  private val _albumItemIdCounter = AtomicLong(0)

  val currentListenMode: AlbumViewController.ListenMode
    get() = savedStateHandle.requireParams<AlbumViewController.Params>().listenMode

  private val _currentDescriptor = MutableStateFlow<ChanDescriptor?>(null)
  val currentDescriptor: StateFlow<ChanDescriptor?>
    get() = _currentDescriptor.asStateFlow()

  private val _albumItems = mutableStateListOf<AlbumItemData>()
  val albumItems: SnapshotStateList<AlbumItemData>
    get() = _albumItems

  private val _albumSelection = MutableStateFlow<AlbumSelection>(AlbumSelection())
  val albumSelection: StateFlow<AlbumSelection>
    get() = _albumSelection

  private val _downloadingAlbumItems = mutableStateMapOf<Long, DownloadingAlbumItem>()
  val downloadingAlbumItems: SnapshotStateMap<Long, DownloadingAlbumItem>
    get() = _downloadingAlbumItems

  private val _scrollToPositionRequests = MutableStateFlow<Int>(-1)
  val scrollToPositionRequests: SharedFlow<Int>
    get() = _scrollToPositionRequests.asSharedFlow()

  private val _presentController = MutableSharedFlow<PresentController>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_LATEST
  )
  val presentController: SharedFlow<PresentController>
    get() = _presentController.asSharedFlow()

  private val _lastScrollPosition = mutableIntStateOf(0)
  val lastScrollPosition: IntState
    get() = _lastScrollPosition

  private val _toolbarData = MutableStateFlow<ToolbarData>(ToolbarData())
  val toolbarData: StateFlow<ToolbarData>
    get() = _toolbarData

  val albumSpanCount = ChanSettings.albumSpanCount.listenForChangesDeprecated()
    .asFlow()
    .stateIn(viewModelScope, SharingStarted.Lazily, null)

  val albumLayoutGridMode = PersistableChanState.albumLayoutGridMode.listenForChangesDeprecated()
    .asFlow()
    .stateIn(viewModelScope, SharingStarted.Lazily, null)

  val showAlbumViewsImageDetails = PersistableChanState.showAlbumViewsImageDetails.listenForChangesDeprecated()
    .asFlow()
    .stateIn(viewModelScope, SharingStarted.Lazily, null)

  val globalNsfwMode = ChanSettings.globalNsfwMode.listenForChangesDeprecated()
    .asFlow()
    .stateIn(viewModelScope, SharingStarted.Lazily, null)

  private val snackbarManager by lazy {
    val snackbarScope = when (currentListenMode) {
      AlbumViewController.ListenMode.Catalog -> SnackbarScope.Album(SnackbarScope.MainLayoutAnchor.Catalog)
      AlbumViewController.ListenMode.Thread -> SnackbarScope.Album(SnackbarScope.MainLayoutAnchor.Thread)
    }

    snackbarManagerFactory.snackbarManager(snackbarScope)
  }

  private val downloadImagesHelper by lazy {
    DownloadImagesHelper(
      viewModelScope = viewModelScope,
      snackbarManager = snackbarManager,
      imageSaverV2 = imageSaverV2
    )
  }

  private val autoClearDownloadingAlbumItemState = AutoClearDownloadingAlbumItemState(
    viewModelScope = viewModelScope,
    clearDownloadingAlbumItemState = { toClear ->
      Snapshot.withMutableSnapshot {
        toClear.forEach { albumItemDataId -> clearDownloadingAlbumItemState(albumItemDataId) }
      }
    }
  )

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    viewModelScope.launch {
      listenForCurrentChanDescriptor()
    }

    viewModelScope.launch(Dispatchers.IO) {
      loadAndUpdateAlbumItems()
    }

    viewModelScope.launch {
      imageSaverV2ServiceDelegate.downloadingImagesFlow
        .onEach { downloadingImageState -> handleDownloadingImageState(downloadingImageState) }
        .collect()
    }

    viewModelScope.launch {
      revealedSpoilerImagesManager.spoilerImageRevealEventsFlow
        .onEach { revealedSpoilerImage -> handleRevealedSpoilerImageEvent(revealedSpoilerImage) }
        .collect()
    }
  }

  fun updateLastScrollPosition(newLastScrollPosition: Int) {
    _lastScrollPosition.intValue = newLastScrollPosition
  }

  fun mapPostImagesToPostDescriptors(): List<PostDescriptor> {
    val duplicateSet = mutableSetOf<PostDescriptor>()

    return albumItems.mapNotNull { postImage ->
      if (duplicateSet.add(postImage.postDescriptor)) {
        return@mapNotNull postImage.postDescriptor
      }

      return@mapNotNull null
    }
  }

  fun findAlbumItemData(chanPostImage: ChanPostImage): AlbumItemData? {
    return albumItems.firstOrNull { albumItemData -> albumItemData.isEqualToChanPostImage(chanPostImage) }
  }

  fun findChanPostImage(albumItemData: AlbumItemData): ChanPostImage? {
    val chanPost = when (val chanDescriptor = _currentDescriptor.value) {
      is ChanDescriptor.ICatalogDescriptor -> {
        chanThreadManager.getChanCatalog(chanDescriptor)?.getPost(albumItemData.postDescriptor)
      }
      is ChanDescriptor.ThreadDescriptor -> {
        chanThreadManager.getChanThread(chanDescriptor)?.getPost(albumItemData.postDescriptor)
      }
      null -> null
    }

    if (chanPost == null) {
      return null
    }

    return chanPost.firstPostImageOrNull { chanPostImage -> chanPostImage.imageUrl == albumItemData.fullImageUrl }
  }

  fun findChanPostImage(albumItemId: Long): ChanPostImage? {
    val albumItemData = _albumItems.firstOrNull { albumItemData -> albumItemData.id == albumItemId }
    if (albumItemData == null) {
      return null
    }

    return findChanPostImage(albumItemData)
  }

  suspend fun requestScrollToImage(chanPostImage: ChanPostImage) {
    val newPosition = albumItems.indexOfFirst { albumItemData ->
      if (albumItemData.postDescriptor != chanPostImage.ownerPostDescriptor) {
        return@indexOfFirst false
      }

      return@indexOfFirst albumItemData.fullImageUrl == chanPostImage.imageUrl
    }

    if (newPosition < 0) {
      return
    }

    _scrollToPositionRequests.value = newPosition
  }

  fun resetScrollToPositionRequests() {
    _scrollToPositionRequests.value = -1
  }

  fun enterSelectionMode(chanPostImage: ChanPostImage) {
    val albumItemData = findAlbumItemData(chanPostImage)
      ?: return

    _albumSelection.value = AlbumSelection(
      isInSelectionMode = true,
      selectedItems = persistentSetOf(albumItemData.id)
    )
  }

  fun enterSelectionMode() {
    hapticFeedbackManager.toggleOn()

    val selectedItemIds = _albumItems
      .filter { albumItemData -> albumItemData.downloadUniqueId == null }
      .map { albumItemData -> albumItemData.id }

    _albumSelection.value = AlbumSelection(
      isInSelectionMode = true,
      selectedItems = selectedItemIds.toPersistentSet()
    )
  }

  fun toggleSelection(albumItemData: AlbumItemData) {
    _albumSelection.value = if (_albumSelection.value.contains(albumItemData.id)) {
      hapticFeedbackManager.toggleOff()
      _albumSelection.value.remove(albumItemData.id)
    } else {
      hapticFeedbackManager.toggleOn()
      _albumSelection.value.add(albumItemData.id)
    }
  }

  fun toggleAlbumItemsSelection() {
    if (_albumSelection.value.size != albumItems.size) {
      hapticFeedbackManager.toggleOn()

      val albumItemIds = albumItems.map { albumItemData -> albumItemData.id }.toPersistentSet()
      _albumSelection.value = _albumSelection.value.addAll(albumItemIds)
    } else {
      hapticFeedbackManager.toggleOff()

      _albumSelection.value = _albumSelection.value.copy(selectedItems = persistentSetOf())
    }
  }

  fun isInSelectionMode(): Boolean {
    return _albumSelection.value.isInSelectionMode
  }

  fun exitSelectionMode() {
    _albumSelection.value = AlbumSelection()
  }

  fun clearDownloadingAlbumItemState(downloadingAlbumItem: DownloadingAlbumItem) {
    clearDownloadingAlbumItemState(downloadingAlbumItem.albumItemDataId)
  }

  fun clearDownloadingAlbumItemState(albumItemDataId: Long) {
    _downloadingAlbumItems.remove(albumItemDataId)

    val albumItemDataIndex = _albumItems
      .indexOfFirst { albumItemData -> albumItemData.id == albumItemDataId }
    if (albumItemDataIndex >= 0) {
      val albumItemData = _albumItems[albumItemDataIndex]
      _albumItems[albumItemDataIndex] = albumItemData.copy(downloadUniqueId = null)
    }
  }

  fun downloadImage(chanPostImage: ChanPostImage, showOptions: Boolean) {
    val albumItemData = findAlbumItemData(chanPostImage)
    if (albumItemData == null) {
      return
    }

    _albumSelection.value = _albumSelection.value.add(albumItemData.id)

    downloadImagesHelper.downloadImage(
      albumSelection = _albumSelection.value,
      albumItems = _albumItems,
      downloadingAlbumItems = _downloadingAlbumItems,
      chanPostImage = chanPostImage,
      showOptions = showOptions,
      presentImageSaverOptionsController = { options ->
        val controller = PresentController.ImageSaverOptionsController(options)
        _presentController.tryEmit(controller)
      },
      exitSelectionMode = { exitSelectionMode() }
    )
  }

  fun downloadSelectedItems() {
    downloadImagesHelper.downloadSelectedItems(
      albumSelection = _albumSelection.value,
      albumItems = _albumItems,
      downloadingAlbumItems = _downloadingAlbumItems,
      findChanPostImage = { albumItemId -> findChanPostImage(albumItemId) },
      presentImageSaverOptionsController = { options ->
        val controller = PresentController.ImageSaverOptionsController(options)
        _presentController.tryEmit(controller)
      },
      exitSelectionMode = { exitSelectionMode() }
    )
  }

  private suspend fun updateUi(
    initialLoad: Boolean,
    album: Album
  ) {
    val allAlbumItems = album.albumItemDataList
    val scrollToPosition = album.scrollToPosition
    val initialImageFullUrl = album.initialImageFullUrl

    Logger.debug(TAG) { "Got ${allAlbumItems.size} album items" }

    val (duplicateChecker, capacity) = withContext(Dispatchers.Main) {
      val duplicateChecker = _albumItems
        .toHashSetBy(capacity = _albumItems.size) { albumItemData -> albumItemData.fullImageUrl }

      val capacity = (allAlbumItems.size - _albumItems.size).coerceAtLeast(16)
      return@withContext duplicateChecker to capacity
    }

    val newAlbumItemsToAppendList = mutableListWithCap<AlbumItemData>(capacity)

    allAlbumItems.forEach { newAlbumItemData ->
      if (duplicateChecker.contains(newAlbumItemData.fullImageUrl)) {
        return@forEach
      }

      newAlbumItemsToAppendList += newAlbumItemData
    }

    Logger.debug(TAG) { "Got ${newAlbumItemsToAppendList.size} new album items" }

    withContext(Dispatchers.Main) {
      if (newAlbumItemsToAppendList.isNotEmpty()) {
        applyNewAlbumItems(initialLoad, newAlbumItemsToAppendList)
      }

      updateToolbarTitle(
        newAlbumItems = allAlbumItems,
        chanDescriptor = _currentDescriptor.value
      )

      if (scrollToPosition != null) {
        Logger.debug(TAG) { "updateScrollPosition() '${initialImageFullUrl}' found at ${scrollToPosition}, performing scroll" }
        _scrollToPositionRequests.value = scrollToPosition
        _lastScrollPosition.intValue = scrollToPosition
      } else {
        Logger.debug(TAG) { "updateScrollPosition() '${initialImageFullUrl}' failed to find scroll position" }
      }
    }
  }

  private suspend fun updateToolbarTitle(
    newAlbumItems: List<AlbumItemData>,
    chanDescriptor: ChanDescriptor?
  ) {
    BackgroundUtils.ensureMainThread()

    val toolbarTitle = when (chanDescriptor) {
      is ChanDescriptor.CompositeCatalogDescriptor,
      is ChanDescriptor.CatalogDescriptor -> {
        if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
          compositeCatalogManager.byCompositeCatalogDescriptor(chanDescriptor)?.name
            ?: ChanPostUtils.getTitle(null, chanDescriptor)
        } else {
          ChanPostUtils.getTitle(null, chanDescriptor)
        }
      }
      is ChanDescriptor.ThreadDescriptor -> {
        ChanPostUtils.getTitle(
          chanThreadManager.getChanThread(chanDescriptor)?.getOriginalPost(),
          chanDescriptor
        )
      }
      null -> ""
    }
    val toolbarSubTitle = appResources.quantityString(
      R.plurals.image,
      newAlbumItems.size,
      newAlbumItems.size
    )

    _toolbarData.value = ToolbarData(
      title = toolbarTitle,
      subtitle = toolbarSubTitle
    )
  }

  private fun applyNewAlbumItems(initialLoad: Boolean, newAlbumItemsToAppendList: List<AlbumItemData>) {
    BackgroundUtils.ensureMainThread()

    if (newAlbumItemsToAppendList.isEmpty()) {
      return
    }

    _albumItems.addAll(newAlbumItemsToAppendList)

    // Do not show the toast the first time we open AlbumViewController. Only show it for album item updates.
    if (!initialLoad) {
      snackbarManager.toast(
        appResources.string(
          R.string.album_screen_new_images,
          newAlbumItemsToAppendList.size
        )
      )
    }
  }

  private fun findInitialScrollPosition(initialImageFullUrl: String?, allAlbumItems: List<ChanPostImage>): Int? {
    if (initialImageFullUrl.isNullOrBlank()) {
      Logger.debug(TAG) { "updateScrollPosition() initialImageFullUrl is null or blank" }
      return null
    }

    val indexToScrollTo = allAlbumItems
      .indexOfFirst { albumItemData -> albumItemData.imageUrl?.toString() == initialImageFullUrl }
    if (indexToScrollTo < 0) {
      Logger.debug(TAG) { "updateScrollPosition() failed to find '${initialImageFullUrl}'" }
      return null
    }

    return indexToScrollTo
  }

  private suspend fun getAlbumItemsForChanDescriptor(
    initialLoad: Boolean,
    loadedChanDescriptor: ChanThreadManager.LoadedChanDescriptor
  ): Album? {
    BackgroundUtils.ensureBackgroundThread()
    Logger.debug(TAG) { "loadedChanDescriptor is '${loadedChanDescriptor}'" }

    val actualChanDescriptor = when (loadedChanDescriptor) {
      is ChanThreadManager.LoadedChanDescriptor.Composite -> {
        loadedChanDescriptor.compositeCatalogDescriptor
      }

      is ChanThreadManager.LoadedChanDescriptor.Regular -> {
        loadedChanDescriptor.loadedDescriptor
      }
    }

    val posts = kotlin.run {
      when (actualChanDescriptor) {
        is ChanDescriptor.ICatalogDescriptor -> {
          val catalogSnapshot = chanCatalogSnapshotCache.get(actualChanDescriptor)
          if (catalogSnapshot == null) {
            return@run emptyList()
          }

          return@run catalogSnapshot.catalogThreadDescriptorList
            .mapNotNull { threadDescriptor ->
              chanThreadsCache.getOriginalPostFromCache(threadDescriptor.toOriginalPostDescriptor())
            }
        }
        is ChanDescriptor.ThreadDescriptor -> {
          return@run chanThreadsCache.getThreadPosts(actualChanDescriptor)
        }
      }
    }

    if (posts.isEmpty()) {
      Logger.debug(TAG) { "Got no posts for '${loadedChanDescriptor}'" }
      return null
    }

    Logger.debug(TAG) { "Got ${posts.size} posts for '${loadedChanDescriptor}'" }

    val allImages = posts
      .flatMap { chanPost -> chanPost.postImages }

    val initialImageFullUrl = savedStateHandle
      .paramsOrNull<AlbumViewController.Params>()
      ?.initialImageFullUrl

    val scrollToPosition = if (initialLoad) {
      findInitialScrollPosition(initialImageFullUrl, allImages)
    } else {
      null
    }

    val input = FilterOutHiddenImagesUseCase.Input(
      images = allImages,
      index = scrollToPosition,
      isOpeningAlbum = true,
      postDescriptorSelector = { chanPostImage -> chanPostImage.ownerPostDescriptor },
      elementExistsInList = { image, images -> images.any { postImage -> postImage.equalUrl(image) } }
    )

    val output = filterOutHiddenImagesUseCase.filter(input)
    val filteredImages = output.images
    val postsMap = posts.associateBy { chanPost -> chanPost.postDescriptor }

    Logger.debug(TAG) { "Got ${filteredImages.size}/${allImages.size} images for '${loadedChanDescriptor}' after filtering" }

    val albumItemDataList = filteredImages.mapNotNull { chanPostImage ->
      BackgroundUtils.ensureBackgroundThread()

      val thumbnailUrl = chanPostImage.actualThumbnailUrl
      if (thumbnailUrl == null) {
        return@mapNotNull null
      }

      val spoilerThumbnailImageUrl = if (chanPostImage.spoiler) {
        if (revealedSpoilerImagesManager.isImageSpoilerImageRevealed(chanPostImage)) {
          null
        } else {
          chanPostImage.spoilerThumbnailUrl
        }
      } else {
        null
      }

      return@mapNotNull AlbumItemData(
        id = _albumItemIdCounter.getAndIncrement(),
        isCatalogMode = actualChanDescriptor.isCatalogDescriptor(),
        postDescriptor = chanPostImage.ownerPostDescriptor,
        thumbnailImageUrl = thumbnailUrl,
        spoilerThumbnailImageUrl = spoilerThumbnailImageUrl,
        fullImageUrl = chanPostImage.imageUrl,
        albumItemPostData = AlbumItemPostData(
          threadSubject = when (actualChanDescriptor) {
            is ChanDescriptor.ICatalogDescriptor -> {
              ChanPostUtils.getTitle(
                post = postsMap[chanPostImage.ownerPostDescriptor],
                chanDescriptor = actualChanDescriptor
              )
            }
            is ChanDescriptor.ThreadDescriptor -> null
          },
          mediaInfo = formatImageDetails(chanPostImage),
          aspectRatio = calculateAspectRatio(chanPostImage)
        ),
        mediaType = chanPostImage.extension.asKurobaMediaType(),
        downloadUniqueId = null
      )
    }

    return Album(
      initialImageFullUrl = initialImageFullUrl,
      scrollToPosition = output.index,
      albumItemDataList = albumItemDataList
    )
  }


  private suspend fun loadAndUpdateAlbumItems() {
    _currentDescriptor
      .flatMapLatest { currentDescriptor ->
        BackgroundUtils.ensureBackgroundThread()

        if (currentDescriptor == null) {
          return@flatMapLatest flowOf(null)
        }

        Logger.debug(TAG) { "Got new descriptor: '${currentDescriptor}'" }

        val loadedChanDescriptor = when (currentDescriptor) {
          is ChanDescriptor.CompositeCatalogDescriptor -> ChanThreadManager.LoadedChanDescriptor.Composite(
            currentDescriptor
          )

          is ChanDescriptor.CatalogDescriptor -> ChanThreadManager.LoadedChanDescriptor.Regular(currentDescriptor)
          is ChanDescriptor.ThreadDescriptor -> ChanThreadManager.LoadedChanDescriptor.Regular(currentDescriptor)
        }

        val album = getAlbumItemsForChanDescriptor(
          initialLoad = true,
          loadedChanDescriptor = loadedChanDescriptor
        )

        Logger.debug(TAG) { "Got ${album?.albumItemDataList?.size ?: 0} initial album items" }

        if (album != null && album.albumItemDataList.isNotEmpty()) {
          updateUi(initialLoad = true, album = album)
        }

        return@flatMapLatest chanThreadManager.chanDescriptorLoadFinishedEventsFlow
          .filter { loadedChanDescriptor ->
            when (currentDescriptor) {
              is ChanDescriptor.CompositeCatalogDescriptor -> {
                if (loadedChanDescriptor !is ChanThreadManager.LoadedChanDescriptor.Composite) {
                  return@filter false
                }

                if (loadedChanDescriptor.compositeCatalogDescriptor != currentDescriptor) {
                  return@filter false
                }

                return@filter true
              }

              is ChanDescriptor.CatalogDescriptor,
              is ChanDescriptor.ThreadDescriptor -> {
                if (loadedChanDescriptor !is ChanThreadManager.LoadedChanDescriptor.Regular) {
                  return@filter false
                }

                return@filter loadedChanDescriptor.loadedDescriptor == currentDescriptor
              }
            }
          }
      }
      .filterNotNull()
      .mapNotNull { loadedChanDescriptor ->
        return@mapNotNull getAlbumItemsForChanDescriptor(
          initialLoad = false,
          loadedChanDescriptor = loadedChanDescriptor
        )
      }
      .filter { album -> album.albumItemDataList.isNotEmpty() }
      .collectLatest { album ->
        updateUi(initialLoad = false, album = album)
      }
  }

  private suspend fun listenForCurrentChanDescriptor() {
    chanThreadManager.awaitUntilDependenciesInitialized()

    Logger.debug(TAG) { "listenForCurrentChanDescriptor() currentListenMode: ${currentListenMode}" }

    val currentDescriptorFlow = when (currentListenMode) {
      AlbumViewController.ListenMode.Catalog -> {
        currentOpenedDescriptorStateManager.currentCatalogDescriptorFlow
          .map { catalogDescriptor -> catalogDescriptor as ChanDescriptor? }
      }
      AlbumViewController.ListenMode.Thread -> {
        currentOpenedDescriptorStateManager.currentThreadDescriptorFlow
          .map { threadDescriptor -> threadDescriptor as ChanDescriptor? }
      }
    }

    currentDescriptorFlow
      .distinctUntilChanged()
      .collectLatest { currentDescriptor ->
        Logger.debug(TAG) { "listenForCurrentChanDescriptor() currentDescriptor changed to '${currentDescriptor}'" }

        _albumItems.clear()
        _albumSelection.value = AlbumSelection()
        _downloadingAlbumItems.clear()
        _lastScrollPosition.intValue = 0
        _currentDescriptor.value = currentDescriptor
      }
  }

  private fun handleRevealedSpoilerImageEvent(revealedSpoilerImage: RevealedSpoilerImagesManager.RevealedSpoilerImage) {
    val index = _albumItems.indexOfFirst { albumItemData ->
      return@indexOfFirst albumItemData.postDescriptor == revealedSpoilerImage.postDescriptor &&
        albumItemData.fullImageUrl == revealedSpoilerImage.fullImageUrl
    }

    if (index < 0) {
      return
    }

    _albumItems[index] = _albumItems[index].copy(spoilerThumbnailImageUrl = null)
  }

  private fun handleDownloadingImageState(downloadingImageState: ImageSaverV2ServiceDelegate.DownloadingImageState) {
    val uniqueId = downloadingImageState.uniqueId
    val imageFullUrl = downloadingImageState.imageFullUrl
    val postDescriptor = downloadingImageState.postDescriptor
    val state = downloadingImageState.state

    fun processAlbumItem(
      downloadingAlbumItemKey: Long,
      downloadingImageState: ImageSaverV2ServiceDelegate.DownloadingImageState
    ) {
      val newState = when (downloadingImageState.state) {
        ImageSaverV2ServiceDelegate.DownloadingImageState.State.Downloading,
        ImageSaverV2ServiceDelegate.DownloadingImageState.State.Downloaded,
        ImageSaverV2ServiceDelegate.DownloadingImageState.State.FailedToDownload,
        ImageSaverV2ServiceDelegate.DownloadingImageState.State.Canceled -> {
          _downloadingAlbumItems[downloadingAlbumItemKey]?.copy(state = DownloadingAlbumItem.State.from(state))
        }
        ImageSaverV2ServiceDelegate.DownloadingImageState.State.Deleted -> {
          null
        }
      }

      if (newState != null) {
        _downloadingAlbumItems[downloadingAlbumItemKey] = newState
      }

      when (downloadingImageState.state) {
        ImageSaverV2ServiceDelegate.DownloadingImageState.State.Downloading -> {
          // no-op
        }
        ImageSaverV2ServiceDelegate.DownloadingImageState.State.Downloaded,
        ImageSaverV2ServiceDelegate.DownloadingImageState.State.FailedToDownload -> {
          autoClearDownloadingAlbumItemState.enqueue(downloadingAlbumItemKey)
        }
        ImageSaverV2ServiceDelegate.DownloadingImageState.State.Canceled,
        ImageSaverV2ServiceDelegate.DownloadingImageState.State.Deleted -> {
          clearDownloadingAlbumItemState(downloadingAlbumItemKey)
        }
      }
    }

    if (imageFullUrl == null || postDescriptor == null) {
      val downloadingAlbumItemKeys = _downloadingAlbumItems.values
        .filter { downloadingAlbumItem -> downloadingAlbumItem.downloadUniqueId == uniqueId }
        .map { downloadingAlbumItem -> downloadingAlbumItem.albumItemDataId }

      Snapshot.withMutableSnapshot {
        downloadingAlbumItemKeys.forEach { downloadingAlbumItemKey ->
          processAlbumItem(
            downloadingAlbumItemKey = downloadingAlbumItemKey,
            downloadingImageState = downloadingImageState
          )
        }
      }
    } else {
      val albumItemData = _albumItems.firstOrNull { albumItemData ->
        return@firstOrNull albumItemData.postDescriptor == postDescriptor &&
          albumItemData.fullImageUrl == imageFullUrl
      }

      if (albumItemData == null) {
        return
      }

      processAlbumItem(
        downloadingAlbumItemKey = albumItemData.id,
        downloadingImageState = downloadingImageState
      )
    }
  }

  private fun calculateAspectRatio(chanPostImage: ChanPostImage): Float? {
    val imageWidth = chanPostImage.imageWidth
    val imageHeight = chanPostImage.imageHeight
    if (imageWidth <= 0 || imageHeight <= 0) {
      return null
    }

    return (imageWidth.toFloat() / imageHeight.toFloat()).coerceIn(MIN_RATIO, MAX_RATIO)
  }

  private fun formatImageDetails(postImage: ChanPostImage): String {
    if (postImage.isInlined) {
      return postImage.extension?.uppercase(Locale.ENGLISH) ?: ""
    }

    return buildString {
      append(postImage.extension?.uppercase(Locale.ENGLISH) ?: "")
      append(" ")
      if (postImage.imageWidth > 0 || postImage.imageHeight > 0) {
        append(postImage.imageWidth)
        append("x")
        append(postImage.imageHeight)
        append(" ")
      }
      append(ChanPostUtils.getReadableFileSize(postImage.size))
    }
  }

  sealed interface PresentController {
    class ImageSaverOptionsController(
      val options: ImageSaverV2OptionsController.Options
    ) : PresentController
  }

  @Immutable
  data class ToolbarData(
    val title: String = "",
    val subtitle: String = ""
  )

  data class Album(
    val initialImageFullUrl: String?,
    val scrollToPosition: Int?,
    val albumItemDataList: List<AlbumItemData>
  )

  @Immutable
  data class AlbumItemDataKey(
    override val postDescriptor: PostDescriptor,
    override val thumbnailImageUrl: HttpUrl,
    override val fullImageUrl: HttpUrl?
  ) : PostImageThumbnailKey

  @Immutable
  data class AlbumItemPostData(
    val threadSubject: String?,
    val mediaInfo: String?,
    val aspectRatio: Float?
  ) {
    fun isNotEmpty(): Boolean {
      return threadSubject.isNotNullNorBlank() || mediaInfo.isNotNullNorBlank()
    }
  }

  class ViewModelFactory @Inject constructor(
    private val appResources: AppResources,
    private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager,
    private val chanThreadManager: ChanThreadManager,
    private val chanCatalogSnapshotCache: ChanCatalogSnapshotCache,
    private val chanThreadsCache: ChanThreadsCache,
    private val snackbarManagerFactory: SnackbarManagerFactory,
    private val compositeCatalogManager: CompositeCatalogManager,
    private val filterOutHiddenImagesUseCase: FilterOutHiddenImagesUseCase,
    private val imageSaverV2: ImageSaverV2,
    private val imageSaverV2ServiceDelegate: ImageSaverV2ServiceDelegate,
    private val revealedSpoilerImagesManager: RevealedSpoilerImagesManager,
    private val hapticFeedbackManager: HapticFeedbackManager
  ) : ViewModelAssistedFactory<AlbumViewControllerViewModel> {
    override fun create(handle: SavedStateHandle): AlbumViewControllerViewModel {
      return AlbumViewControllerViewModel(
        savedStateHandle = handle,
        appResources = appResources,
        currentOpenedDescriptorStateManager = currentOpenedDescriptorStateManager,
        chanThreadManager = chanThreadManager,
        chanCatalogSnapshotCache = chanCatalogSnapshotCache,
        chanThreadsCache = chanThreadsCache,
        snackbarManagerFactory = snackbarManagerFactory,
        compositeCatalogManager = compositeCatalogManager,
        filterOutHiddenImagesUseCase = filterOutHiddenImagesUseCase,
        imageSaverV2 = imageSaverV2,
        imageSaverV2ServiceDelegate = imageSaverV2ServiceDelegate,
        revealedSpoilerImagesManager = revealedSpoilerImagesManager,
        hapticFeedbackManager = hapticFeedbackManager
      )
    }
  }

  companion object {
    private const val TAG = "AlbumViewControllerViewModel"

    private const val MAX_RATIO = 2f
    private const val MIN_RATIO = .4f
  }

}