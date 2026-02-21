package com.github.k1rakishou.chan.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

@Composable
fun SiteIconElement(
  siteDescriptor: SiteDescriptor
) {
  val context = LocalContext.current
  val chanTheme = LocalChanTheme.current

  val siteManager = appDependencies().siteManager

  val imageRequest by produceState<ImageRequest?>(
    initialValue = null,
    key1 = siteDescriptor,
    producer = {
      val site = siteManager.bySiteDescriptorAndActive(siteDescriptor)
      if (site == null) {
        value = null
        return@produceState
      }

      val siteIcon = site.icon()
        .getIconSuspend(context.applicationContext)

      value = ImageRequest.Builder(context)
        .data(siteIcon.bitmap)
        .size(Size.ORIGINAL)
        .build()
    }
  )

  if (imageRequest != null) {
    AsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = imageRequest,
      contentDescription = "Site icon"
    )
  } else {
    Shimmer(
      modifier = Modifier.fillMaxSize(),
      mainShimmerColor = chanTheme.toolbarBackgroundComposeColor,
      secondaryShimmerColor = chanTheme.onToolbarBackgroundComposeColor
    )
  }
}