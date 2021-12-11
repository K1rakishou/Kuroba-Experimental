package com.github.k1rakishou.chan.features.reply_image_search.searx

import android.content.Context
import android.text.SpannableString
import android.text.util.Linkify
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
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
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.viewModelByKey
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import javax.inject.Inject

class SearxImageSearchController(
  context: Context,
  private val onImageSelected: (HttpUrl) -> Unit
) : BaseFloatingComposeController(context) {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var dialogFactory: DialogFactory

  private val viewModel by lazy { requireComponentActivity().viewModelByKey<SearxImageSearchControllerViewModel>() }

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
      var baseSearchUrl by viewModel.baseSearchUrl
      var searchQuery by viewModel.searchQuery

      KurobaComposeTextField(
        value = baseSearchUrl,
        modifier = Modifier
          .wrapContentHeight()
          .fillMaxWidth(),
        onValueChange = { newValue -> baseSearchUrl = newValue }
      ) {
        val hint = stringResource(
          id = R.string.searx_image_search_controller_base_url_hint,
          SearxImageSearchControllerViewModel.HINT_URL
        )

        KurobaComposeText(text = hint)
      }

      Spacer(modifier = Modifier.height(8.dp))

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

      Box(modifier = Modifier
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
        Spacer(modifier = Modifier.height(8.dp))

        val drawableTintColor = themeEngine.resolveDrawableTintColor()
        val colorFilter = remember(key1 = drawableTintColor) { ColorFilter.tint(color = Color(drawableTintColor)) }

        Image(
          painter = painterResource(id = R.drawable.ic_help_outline_white_24dp),
          contentDescription = null,
          colorFilter = colorFilter,
          modifier = Modifier
            .padding(start = 16.dp)
            .width(24.dp)
            .height(24.dp)
            .align(Alignment.CenterVertically)
            .clickable { showHelp() }
        )

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
          onClick = { viewModel.search(page = 1) },
          text = stringResource(id = R.string.search_hint)
        )
      }
    }
  }

  @Composable
  private fun BuildSearxResults(onImageClicked: (SearxImage) -> Unit) {
    val searchResults by viewModel.searchResults

    val searxImages = when (val result = searchResults) {
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

    BuildSearxImages(searxImages, onImageClicked)
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun BuildSearxImages(searxImages: List<SearxImage>, onImageClicked: (SearxImage) -> Unit) {
    val chanTheme = LocalChanTheme.current

    val state = rememberLazyListState(
      initialFirstVisibleItemIndex = viewModel.rememberedFirstVisibleItemIndex,
      initialFirstVisibleItemScrollOffset = viewModel.rememberedFirstVisibleItemScrollOffset
    )

    DisposableEffect(key1 = Unit, effect = {
      onDispose {
        viewModel.updatePrevLazyListState(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
      }
    })

    LazyVerticalGrid(
      state = state,
      cells = GridCells.Adaptive(minSize = IMAGE_SIZE),
      modifier = Modifier
        .fillMaxSize()
        .simpleVerticalScrollbar(state = state, chanTheme = chanTheme)
    ) {
      items(searxImages.size) { index ->
        val searxImage = searxImages.get(index)
        BuildSearxImage(searxImage, onImageClicked)
      }

      item {
        Box(modifier = Modifier
          .size(IMAGE_SIZE)
        ) {
          KurobaComposeProgressIndicator(modifier = Modifier
            .wrapContentSize()
            .align(Alignment.Center))
        }

        LaunchedEffect(key1 = searxImages.lastIndex) {
          viewModel.search(page = viewModel.currentPage + 1)
        }
      }
    }
  }

  @Composable
  private fun BuildSearxImage(
    searxImage: SearxImage,
    onImageClicked: (SearxImage) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    val request = ImageLoaderRequest(
      data = ImageLoaderRequestData.Url(
        httpUrl = searxImage.thumbnailUrl,
        cacheFileType = CacheFileType.Other
      )
    )

    Box(
      modifier = Modifier
        .size(IMAGE_SIZE)
        .padding(4.dp)
        .background(chanTheme.backColorSecondaryCompose)
        .clickable { onImageClicked(searxImage) }
    ) {
      KurobaComposeImage(
        request = request,
        modifier = Modifier.fillMaxSize(),
        imageLoaderV2 = imageLoaderV2
      )
    }
  }

  private fun showHelp() {
    val message = SpannableString(getString(R.string.searx_image_search_controller_help_text))
    Linkify.addLinks(message, Linkify.WEB_URLS)

    dialogFactory.createSimpleInformationDialog(
      context = context,
      titleText = getString(R.string.help),
      descriptionText = message
    )
  }

  companion object {
    private val IMAGE_SIZE = 96.dp
  }

}