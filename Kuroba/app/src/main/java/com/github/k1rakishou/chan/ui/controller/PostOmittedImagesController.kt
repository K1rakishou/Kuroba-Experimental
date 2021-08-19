package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.ui.cell.post_thumbnail.PostImageThumbnailViewsContainer
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequest
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequestData
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCardView
import com.github.k1rakishou.chan.ui.compose.KurobaComposeImage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.util.ChanPostUtils
import java.util.*
import javax.inject.Inject

class PostOmittedImagesController(
  private val postImages: List<ChanPostImage>,
  private val onImageClicked: (ChanPostImage) -> Unit,
  private val onImageLongClicked: (ChanPostImage) -> Unit,
  context: Context
) : BaseFloatingComposeController(context) {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  override fun BoxScope.BuildContent() {
    val thumbnailScaling = remember {
      ChanSettings.postThumbnailScaling.get()
    }

    LazyVerticalGrid(
      modifier = Modifier
        .widthIn(max = 600.dp)
        .wrapContentHeight()
        .align(Alignment.Center),
      verticalArrangement = Arrangement.Center,
      cells = GridCells.Fixed(2),
      content = {
        items(postImages.size) { index ->
          val postImage = postImages.get(index)
          BuildPostImage(postImage, thumbnailScaling)
        }
      })
  }

  @Composable
  private fun BuildPostImage(postImage: ChanPostImage, thumbnailScaling: ChanSettings.PostThumbnailScaling) {
    val thumbnailUrl = postImage.getThumbnailUrl()
      ?: return

    val request = remember(key1 = postImage) {
      ImageLoaderRequest(ImageLoaderRequestData.Url(thumbnailUrl))
    }
    val thumbnailSize = with(LocalDensity.current) {
      remember(key1 = postImage) {
        val size = PostImageThumbnailViewsContainer.calculatePostCellSingleThumbnailSize().toDp()

        if (isTablet()) {
          return@remember size * 1.5f
        }

        return@remember size
      }
    }
    val fontSize = remember {
      if (isTablet()) {
        13.sp
      } else {
        11.sp
      }
    }
    val shape = remember {
      RoundedCornerShape(2.dp)
    }

    KurobaComposeCardView(
      modifier = Modifier
        .wrapContentSize()
        .padding(4.dp)
        .kurobaClickable(
          bounded = true,
          onClick = { onImageClicked(postImage) },
          onLongClick = { onImageLongClicked(postImage) }
        ),
      shape = shape
    ) {
      Column(
        modifier = Modifier.padding(2.dp)
      ) {
        Row(modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
        ) {
          val contentScale = remember(key1 = thumbnailScaling) {
            when (thumbnailScaling) {
              ChanSettings.PostThumbnailScaling.FitCenter -> ContentScale.Fit
              ChanSettings.PostThumbnailScaling.CenterCrop -> ContentScale.Crop
            }
          }

          Box {
            KurobaComposeImage(
              request = request,
              contentScale = contentScale,
              modifier = Modifier
                .size(thumbnailSize)
                .background(Color.Black),
              imageLoaderV2 = imageLoaderV2
            )

            if (postImage.isPlayableType()) {
              Image(
                modifier = Modifier.align(Alignment.Center),
                painter = painterResource(id = R.drawable.ic_play_circle_outline_white_24dp),
                contentDescription = null
              )
            }
          }

          Spacer(modifier = Modifier.width(4.dp))

          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
          ) {
            val extension = remember(key1 = postImage) {
              postImage.extension?.uppercase(Locale.ENGLISH)
            }

            if (extension != null) {
              KurobaComposeText(text = extension, fontSize = fontSize)
            }

            val dimensions = remember(key1 = postImage) {
              "${postImage.imageWidth}x${postImage.imageHeight}"
            }

            KurobaComposeText(text = dimensions, fontSize = fontSize)

            val size = remember(key1 = postImage) {
              ChanPostUtils.getReadableFileSize(postImage.size)
            }

            KurobaComposeText(text = size, fontSize = fontSize)
          }
        }

        val filename = remember(key1 = postImage) {
          postImage.formatFullAvailableFileName(appendExtension = false)
        }

        KurobaComposeText(
          modifier = Modifier.padding(vertical = 4.dp),
          text = filename,
          maxLines = 4,
          overflow = TextOverflow.Ellipsis,
          fontSize = fontSize
        )
      }
    }
  }

}