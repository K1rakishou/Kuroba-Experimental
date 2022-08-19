package com.github.k1rakishou.chan.features.reply_image_search

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.features.bypass.CookieResult
import com.github.k1rakishou.chan.features.bypass.SiteFirewallBypassController
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequest
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequestData
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeImage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextField
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.HeaderFloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.findControllerOrNull
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import javax.inject.Inject

class ImageSearchController(
  context: Context,
  private val boundChanDescriptor: ChanDescriptor,
  private val onImageSelected: (HttpUrl) -> Unit
) : Controller(context), WindowInsetsListener {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val topPadding = mutableStateOf(0)
  private val bottomPadding = mutableStateOf(0)

  private val viewModel by lazy { requireComponentActivity().viewModelByKey<ImageSearchControllerViewModel>() }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    navigation.setTitle(R.string.image_search_controller_title)
    navigation.swipeable = false

    navigation
      .buildMenu(context)
      .withItem(ACTION_RELOAD, R.drawable.ic_refresh_white_24dp) {
        val currentCaptchaController = findControllerOrNull { controller ->
          return@findControllerOrNull controller is SiteFirewallBypassController &&
            controller.firewallType == FirewallType.YandexSmartCaptcha
        }

        currentCaptchaController?.stopPresenting()
        viewModel.reload()
      }
      .build()

    mainScope.launch {
      viewModel.searchErrorToastFlow
        .debounce(350L)
        .collect { errorMessage -> showToast(errorMessage) }
    }

    mainScope.launch {
      viewModel.solvingCaptcha.collect { urlToOpen ->
        if (urlToOpen == null) {
          return@collect
        }

        val alreadyPresenting = isAlreadyPresenting { controller -> controller is SiteFirewallBypassController }
        if (alreadyPresenting) {
          return@collect
        }

        try {
          val cookieResult = suspendCancellableCoroutine<CookieResult> { continuation ->
            val controller = SiteFirewallBypassController(
              context = context,
              firewallType = FirewallType.YandexSmartCaptcha,
              urlToOpen = urlToOpen,
              onResult = { cookieResult -> continuation.resumeValueSafe(cookieResult) }
            )

            presentController(controller)
          }

          // Wait a second for the controller to get closed so that we don't end up in a loop
          delay(1000)

          if (cookieResult !is CookieResult.CookieValue) {
            Logger.e(TAG, "Failed to bypass YandexSmartCaptcha, cookieResult: ${cookieResult}")
            viewModel.reloadCurrentPage()
            return@collect
          }

          Logger.d(TAG, "Get YandexSmartCaptcha cookies, cookieResult: ${cookieResult}")
          viewModel.updateYandexSmartCaptchaCookies(cookieResult.cookie)
          viewModel.reloadCurrentPage()
        } finally {
          viewModel.finishedSolvingCaptcha()
        }
      }
    }

    onInsetsChanged()
    globalWindowInsetsManager.addInsetsUpdatesListener(this)

    view = ComposeView(context).apply {
      setContent {
        ProvideChanTheme(themeEngine) {
          val chanTheme = LocalChanTheme.current

          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(chanTheme.backColorCompose)
          ) {
            BuildContent()
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    viewModel.cleanup()
  }

  override fun onInsetsChanged() {
    val toolbarHeight = requireToolbarNavController().toolbar?.toolbarHeight
      ?: AppModuleAndroidUtils.getDimen(R.dimen.toolbar_height)

    topPadding.value = AppModuleAndroidUtils.pxToDp(toolbarHeight)

    val bottomPaddingDp = calculateBottomPaddingForRecyclerInDp(
      globalWindowInsetsManager = globalWindowInsetsManager,
      mainControllerCallbacks = null
    )

    bottomPadding.value = bottomPaddingDp
  }

  @Composable
  private fun BuildContent() {
    val chanTheme = LocalChanTheme.current
    val focusManager = LocalFocusManager.current

    val lastUsedSearchInstanceMut by viewModel.lastUsedSearchInstance
    val lastUsedSearchInstance = lastUsedSearchInstanceMut
    if (lastUsedSearchInstance == null) {
      return
    }

    val searchInstanceMut = viewModel.searchInstances[lastUsedSearchInstance]
    val searchInstance = searchInstanceMut
    if (searchInstance == null) {
      return
    }

    var searchQuery by viewModel.searchQuery
    val topPd by topPadding

    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(chanTheme.backColorCompose)
        .padding(horizontal = 8.dp)
    ) {
      Spacer(modifier = Modifier.height(topPd.dp))

      Spacer(modifier = Modifier.height(4.dp))

      SearchInstanceSelector(
        searchInstance = searchInstance,
        onSelectorItemClicked = { showImageSearchInstances() }
      )

      Spacer(modifier = Modifier.height(8.dp))

      KurobaComposeTextField(
        value = searchQuery,
        modifier = Modifier
          .wrapContentHeight()
          .fillMaxWidth(),
        onValueChange = { newValue ->
          searchQuery = newValue
          viewModel.onSearchQueryChanged(newValue)
        },
        singleLine = true,
        maxLines = 1,
        label = {
          KurobaComposeText(
            text = stringResource(id = R.string.search_query_hint),
            color = chanTheme.textColorHintCompose
          )
        }
      )

      Spacer(modifier = Modifier.height(8.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        BuildImageSearchResults(
          lastUsedSearchInstance = lastUsedSearchInstance,
          onImageClicked = { searxImage ->
            focusManager.clearFocus(force = true)

            if (searxImage.fullImageUrls.isEmpty()) {
              return@BuildImageSearchResults
            }

            if (searxImage.fullImageUrls.size == 1) {
              onImageSelected(searxImage.fullImageUrls.first())
              popFromNavController(boundChanDescriptor)

              return@BuildImageSearchResults
            }

            showOptions(searxImage.fullImageUrls)
          }
        )
      }
    }
  }

  @Composable
  private fun SearchInstanceSelector(
    searchInstance: ImageSearchInstance,
    onSelectorItemClicked: () -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    KurobaComposeText(
      text = stringResource(id = R.string.image_search_controller_current_instance),
      fontSize = 12.sp,
      color = chanTheme.textColorHintCompose
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .kurobaClickable(
          bounded = true,
          onClick = { onSelectorItemClicked() }
        )
        .padding(vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Image(
        modifier = Modifier.size(24.dp),
        painter = painterResource(id = searchInstance.icon),
        contentDescription = null
      )

      Spacer(modifier = Modifier.width(12.dp))

      KurobaComposeText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        text = searchInstance.type.name
      )
    }
  }

  @Composable
  private fun BuildImageSearchResults(
    lastUsedSearchInstance: ImageSearchInstanceType,
    onImageClicked: (ImageSearchResult) -> Unit
  ) {
    val searchInstance = viewModel.searchInstances[lastUsedSearchInstance]
      ?: return
    val searchResults = viewModel.searchResults[lastUsedSearchInstance]
      ?: return

    val imageSearchResults = when (val result = searchResults) {
      AsyncData.NotInitialized -> {
        return
      }
      AsyncData.Loading -> {
        KurobaComposeProgressIndicator()
        return
      }
      is AsyncData.Error -> {
        KurobaComposeErrorMessage(error = result.throwable)
        return
      }
      is AsyncData.Data -> result.data
    }

    val chanTheme = LocalChanTheme.current
    val bottomPd by bottomPadding

    val state = rememberLazyGridState(
      initialFirstVisibleItemIndex = searchInstance.rememberedFirstVisibleItemIndex,
      initialFirstVisibleItemScrollOffset = searchInstance.rememberedFirstVisibleItemScrollOffset
    )

    val contentPadding = remember(
      key1 = bottomPd,
    ) { PaddingValues(bottom = bottomPd.dp) }

    DisposableEffect(
      key1 = Unit,
      effect = {
        onDispose {
          viewModel.updatePrevLazyListState(
            firstVisibleItemIndex = state.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset
          )
        }
      }
    )

    LazyVerticalGrid(
      modifier = Modifier
        .fillMaxSize()
        .simpleVerticalScrollbar(
          state = state,
          chanTheme = chanTheme,
          contentPadding = contentPadding
        ),
      state = state,
      columns = GridCells.Adaptive(minSize = IMAGE_SIZE),
      contentPadding = contentPadding
    ) {
      val images = imageSearchResults.results

      items(
        count = images.size,
        contentType = { "image_item" }
      ) { index ->
        val imageSearchResult = images.get(index)

        BuildImageSearchResult(
          imageSearchResult = imageSearchResult,
          onImageClicked = onImageClicked
        )
      }

      if (!imageSearchResults.endReached) {
        item(
          span = { GridItemSpan(maxLineSpan) },
          contentType = { "loading_indicator" }
        ) {
          Box(
            modifier = Modifier.size(IMAGE_SIZE)
          ) {
            KurobaComposeProgressIndicator(
              modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .align(Alignment.Center)
            )
          }

          LaunchedEffect(key1 = images.lastIndex) {
            viewModel.onNewPageRequested(page = searchInstance.currentPage + 1)
          }
        }
      } else {
        item(
          span = { GridItemSpan(maxLineSpan) },
          contentType = { "end_reached_indicator" }
        ) {
          KurobaComposeText(
            modifier = Modifier
              .wrapContentSize()
              .padding(horizontal = 32.dp, vertical = 16.dp),
            text = "End reached"
          )
        }
      }
    }
  }

  @Composable
  private fun BuildImageSearchResult(
    imageSearchResult: ImageSearchResult,
    onImageClicked: (ImageSearchResult) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    val request = ImageLoaderRequest(
      data = ImageLoaderRequestData.Url(
        httpUrl = imageSearchResult.thumbnailUrl,
        cacheFileType = CacheFileType.Other
      )
    )

    val imageInfo = remember(key1 = imageSearchResult) {
      if (!imageSearchResult.hasImageInfo()) {
        return@remember null
      }

      return@remember buildString {
        if (imageSearchResult.extension.isNotNullNorEmpty()) {
          append(imageSearchResult.extension.uppercase())
        }

        if (imageSearchResult.width != null && imageSearchResult.height != null) {
          if (length > 0) {
            append(" ")
          }

          append(imageSearchResult.width)
          append("x")
          append(imageSearchResult.height)
        }

        if (imageSearchResult.sizeInByte != null) {
          if (length > 0) {
            append(" ")
          }

          append(ChanPostUtils.getReadableFileSize(imageSearchResult.sizeInByte))
        }
      }
    }

    val bgColor = remember { Color.Black.copy(alpha = 0.6f) }

    Box(
      modifier = Modifier
        .size(IMAGE_SIZE)
        .padding(4.dp)
        .background(chanTheme.backColorSecondaryCompose)
        .clickable { onImageClicked(imageSearchResult) }
    ) {
      KurobaComposeImage(
        request = request,
        modifier = Modifier.fillMaxSize(),
        imageLoaderV2 = imageLoaderV2
      )

      if (imageInfo != null) {
        Text(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(bgColor)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .align(Alignment.BottomEnd),
          text = imageInfo,
          fontSize = 11.sp,
          color = Color.White
        )
      }
    }
  }

  private fun showImageSearchInstances() {
    val menuItems = mutableListOf<FloatingListMenuItem>()

    menuItems += HeaderFloatingListMenuItem("header", "Select image search instance")

    ImageSearchInstanceType.values().forEach { imageSearchInstanceType ->
      menuItems += FloatingListMenuItem(
        key = imageSearchInstanceType,
        name = imageSearchInstanceType.name,
        value = imageSearchInstanceType
      )
    }

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = menuItems,
      itemClickListener = { clickedItem ->
        val selectedImageSearchInstanceType = (clickedItem.value as? ImageSearchInstanceType)
          ?: return@FloatingListMenuController

        viewModel.changeSearchInstance(selectedImageSearchInstanceType)
      }
    )

    presentController(floatingListMenuController)
  }

  private fun showOptions(fullImageUrls: List<HttpUrl>) {
    val menuItems = mutableListOf<FloatingListMenuItem>()

    menuItems += HeaderFloatingListMenuItem("header", "Select source url")

    fullImageUrls.forEach { httpUrl ->
      menuItems += FloatingListMenuItem(httpUrl, httpUrl.toString(), httpUrl)
    }

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = menuItems,
      itemClickListener = { clickedItem ->
        val clickedItemUrl = (clickedItem.value as? HttpUrl)
          ?: return@FloatingListMenuController

        onImageSelected(clickedItemUrl)
        popFromNavController(boundChanDescriptor)
      }
    )

    presentController(floatingListMenuController)
  }

  companion object {
    private const val TAG = "ImageSearchController"

    private const val ACTION_RELOAD = 0

    private val IMAGE_SIZE = 128.dp
  }

}