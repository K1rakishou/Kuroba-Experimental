package com.github.k1rakishou.chan.ui.controller

import android.content.Context
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.ui.cell.post_thumbnail.PostImageThumbnailView
import com.github.k1rakishou.chan.ui.cell.post_thumbnail.PostImageThumbnailViewsContainer
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCardView
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.ui.view.ThumbnailView
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

  @Composable
  override fun BoxScope.BuildContent() {
    LazyVerticalGrid(
      modifier = Modifier
        .widthIn(max = 600.dp)
        .wrapContentHeight()
        .align(Alignment.Center),
      columns = GridCells.Fixed(2),
      content = {
        items(postImages.size) { index ->
          val postImage = postImages.get(index)
          BuildPostImage(postImage)
        }
      })
  }

  @Composable
  private fun BuildPostImage(postImage: ChanPostImage) {
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
    val shape = remember { RoundedCornerShape(2.dp) }

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
          val postImageThumbnailView = with(LocalContext.current) {
            // Apparently it's legal to create an Android view inside of a @Composable function.
            // https://github.com/android/compose-samples/blob/4079e96bd1e0bb38d682b285da9add1c23373503/Crane/app/src/main/java/androidx/compose/samples/crane/details/MapViewUtils.kt#L38
            remember(key1 = postImage) { PostImageThumbnailView(this) }
          }

          AndroidView(
            modifier = Modifier.size(thumbnailSize),
            factory = {
              postImageThumbnailView.apply {
                bindPostImage(
                  postImage = postImage,
                  canUseHighResCells = true,
                  thumbnailViewOptions = ThumbnailView.ThumbnailViewOptions(drawRipple = false)
                )
              }
            },
          )

          DisposableEffect(key1 = postImage, effect = {
            onDispose { postImageThumbnailView.unbindPostImage() }
          })

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