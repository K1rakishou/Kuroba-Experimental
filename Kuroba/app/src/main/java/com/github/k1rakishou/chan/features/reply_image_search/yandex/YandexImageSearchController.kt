package com.github.k1rakishou.chan.features.reply_image_search.yandex

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequest
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequestData
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeImage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextField
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
import com.github.k1rakishou.chan.utils.viewModelByKey
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import javax.inject.Inject

class YandexImageSearchController(
  context: Context,
  private val onImageSelected: (HttpUrl) -> Unit
) : BaseFloatingComposeController(context) {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var dialogFactory: DialogFactory

  private val viewModel by lazy { requireComponentActivity().viewModelByKey<YandexImageSearchControllerViewModel>() }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    mainScope.launch {
      viewModel.searchErrorToastFlow
        .debounce(350L)
        .collect { errorMessage -> showToast(errorMessage) }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    viewModel.cleanup()
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val chanTheme = LocalChanTheme.current
    val focusManager = LocalFocusManager.current

    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(chanTheme.backColorCompose)
    ) {
      var searchQuery by viewModel.searchQuery

      KurobaComposeTextField(
        value = searchQuery,
        modifier = Modifier
          .wrapContentHeight()
          .fillMaxWidth(),
        onValueChange = { newValue -> searchQuery = newValue }
      ) {
        KurobaComposeText(text = stringResource(id = R.string.search_query_hint))
      }

      Spacer(modifier = Modifier.height(8.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        BuildSearxResults(
          onImageClicked = { searxImage ->
            focusManager.clearFocus(force = true)

            onImageSelected(searxImage.fullImageUrl)
            pop()
          }
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(modifier = Modifier
        .wrapContentHeight()
        .fillMaxWidth()
        .padding(bottom = 8.dp, end = 8.dp)
      ) {
        Spacer(modifier = Modifier.weight(1f))

        KurobaComposeTextBarButton(
          onClick = {
            focusManager.clearFocus(force = true)
            pop()
          },
          text = stringResource(id = R.string.close)
        )

        Spacer(modifier = Modifier.width(8.dp))

        KurobaComposeTextBarButton(
          onClick = { viewModel.search() },
          text = stringResource(id = R.string.search_hint)
        )
      }
    }
  }

  @Composable
  private fun BuildSearxResults(onImageClicked: (YandexImage) -> Unit) {
    val searchResults by viewModel.searchResults

    val yandexImages = when (val result = searchResults) {
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

    BuildYandexImages(yandexImages, onImageClicked)
  }

  @Composable
  private fun BuildYandexImages(yandexImages: List<YandexImage>, onImageClicked: (YandexImage) -> Unit) {
    val chanTheme = LocalChanTheme.current

    val state = rememberLazyGridState(
      initialFirstVisibleItemIndex = viewModel.rememberedFirstVisibleItemIndex,
      initialFirstVisibleItemScrollOffset = viewModel.rememberedFirstVisibleItemScrollOffset
    )

    DisposableEffect(
      key1 = Unit,
      effect = {
        onDispose {
          viewModel.updatePrevLazyListState(
            firstVisibleItemIndex = state.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset
          )
        }
      })

    LazyVerticalGrid(
      state = state,
      columns = GridCells.Adaptive(minSize = IMAGE_SIZE),
      modifier = Modifier
        .fillMaxSize()
        .simpleVerticalScrollbar(state = state, chanTheme = chanTheme)
    ) {
      items(
        count = yandexImages.size,
        itemContent = { index ->
          val yandexImage = yandexImages.get(index)
          BuildYandexImage(yandexImage, onImageClicked)
        }
      )
    }
  }

  @Composable
  private fun BuildYandexImage(
    yandexImage: YandexImage,
    onImageClicked: (YandexImage) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    val request = ImageLoaderRequest(
      data = ImageLoaderRequestData.Url(
        httpUrl = yandexImage.thumbnailUrl,
        cacheFileType = CacheFileType.Other
      )
    )

    Box(
      modifier = Modifier
        .size(IMAGE_SIZE)
        .padding(4.dp)
        .background(chanTheme.backColorSecondaryCompose)
        .clickable { onImageClicked(yandexImage) }
    ) {
      KurobaComposeImage(
        request = request,
        modifier = Modifier.fillMaxSize(),
        imageLoaderV2 = imageLoaderV2
      )
    }
  }

  companion object {
    private val IMAGE_SIZE = 96.dp
  }

}