package com.github.k1rakishou.chan.ui.compose

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SiteIconElement(
  siteDescriptor: SiteDescriptor
) {
  val context = LocalContext.current
  val chanTheme = LocalChanTheme.current

  val siteManager = appDependencies().siteManager

  var siteIsNotActive by remember { mutableStateOf(false) }
  var showShimmer by remember { mutableStateOf(false) }

  var imageRequestMut by remember { mutableStateOf<ImageRequest?>(null) }
  val imageRequest = imageRequestMut

  LaunchedEffect(key1 = siteDescriptor) {
    imageRequestMut = null

    val site = siteManager.bySiteDescriptorAndActive(siteDescriptor)
    siteIsNotActive = site == null

    if (site == null) {
      return@LaunchedEffect
    }

    val job = launch {
      delay(200)
      showShimmer = true
    }

    val siteIcon = site.configuration.icon
      .getIconSuspend(context.applicationContext)

    imageRequestMut = ImageRequest.Builder(context)
      .data(siteIcon.bitmap)
      .size(Size.ORIGINAL)
      .build()

    job.cancel()
  }

  if (siteIsNotActive) {
    return
  }

  if (imageRequest == null) {
    if (showShimmer) {
      Shimmer(
        modifier = Modifier.fillMaxSize(),
        mainShimmerColor = chanTheme.toolbarBackgroundComposeColor,
        secondaryShimmerColor = chanTheme.onToolbarBackgroundComposeColor
      )
    } else {
      Spacer(modifier = Modifier.fillMaxSize())
    }

    return
  }

  AsyncImage(
    modifier = Modifier.fillMaxSize(),
    model = imageRequest,
    contentDescription = "Site icon"
  )
}