package com.github.k1rakishou.chan.features.setup

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.catalog.CompositeCatalog
import com.google.accompanist.insets.ProvideWindowInsets
import javax.inject.Inject

class CompositeCatalogsSetupController(
  context: Context
) : Controller(context) {
  @Inject
  lateinit var themeEngine: ThemeEngine

  private val viewModel by lazy {
    requireComponentActivity().viewModelByKey<CompositeCatalogsSetupControllerViewModel>()
  }
  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(mainScope)

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    navigation.title = getString(R.string.controller_composite_catalogs_setup_title)

    viewModel.reload()

    view = ComposeView(context).apply {
      setContent {
        ProvideChanTheme(themeEngine) {
          ProvideWindowInsets {
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
  }

  @Composable
  private fun BuildContent() {
    val chanTheme = LocalChanTheme.current
    val listState = rememberLazyListState()
    val compositeCatalogs = viewModel.compositeCatalogs

    Box(modifier = Modifier.fillMaxSize()) {
      if (compositeCatalogs.isNotEmpty()) {
        LazyColumn(
          contentPadding = PaddingValues(bottom = FAB_SIZE),
          modifier = Modifier
            .fillMaxSize()
            .simpleVerticalScrollbar(listState, chanTheme),
          content = {
            items(compositeCatalogs.size) { index ->
              val compositeCatalog = compositeCatalogs.get(index)

              BuildCompositeCatalogItem(
                index = index,
                totalCount = compositeCatalogs.size,
                chanTheme = chanTheme,
                compositeCatalog = compositeCatalog,
                onCompositeCatalogItemClicked = { clickedCompositeCatalog ->
                  showComposeBoardsController(compositeCatalog = clickedCompositeCatalog)
                },
                onDeleteCompositeCatalogItemClicked = { clickedCompositeCatalog ->
                  rendezvousCoroutineExecutor.post {
                    viewModel.delete(clickedCompositeCatalog)
                      .toastOnError(longToast = true)
                      .toastOnSuccess(message = {
                        return@toastOnSuccess getString(
                          R.string.controller_composite_catalogs_catalog_deleted,
                          clickedCompositeCatalog.name
                        )
                      })
                      .ignore()
                  }
                },
                onMoveUpClicked = { movedCompositeCatalog ->
                  rendezvousCoroutineExecutor.post {
                    viewModel.moveUp(movedCompositeCatalog)
                      .toastOnError(longToast = true)
                      .ignore()
                  }
                },
                onMoveDownClicked = { movedCompositeCatalog ->
                  rendezvousCoroutineExecutor.post {
                    viewModel.moveDown(movedCompositeCatalog)
                      .toastOnError(longToast = true)
                      .ignore()
                  }
                },
              )
            }
          })
      } else {
        KurobaComposeText(
          fontSize = 16.sp,
          modifier = Modifier.fillMaxSize(),
          textAlign = TextAlign.Center,
          text = stringResource(id = R.string.controller_composite_catalogs_empty_text)
        )
      }

      FloatingActionButton(
        modifier = Modifier
          .size(FAB_SIZE)
          .align(Alignment.BottomEnd)
          .offset(x = (-16).dp, y = (-16).dp),
        backgroundColor = chanTheme.accentColorCompose,
        contentColor = Color.White,
        onClick = { showComposeBoardsController(compositeCatalog = null) }
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_add_white_24dp),
          contentDescription = null
        )
      }
    }
  }

  private fun showComposeBoardsController(compositeCatalog: CompositeCatalog?) {
    val composeBoardsController = ComposeBoardsController(
      context = context,
      prevCompositeCatalog = compositeCatalog
    )

    presentController(composeBoardsController)
  }

  @Composable
  private fun BuildCompositeCatalogItem(
    index: Int,
    totalCount: Int,
    chanTheme: ChanTheme,
    compositeCatalog: CompositeCatalog,
    onCompositeCatalogItemClicked: (CompositeCatalog) -> Unit,
    onDeleteCompositeCatalogItemClicked: (CompositeCatalog) -> Unit,
    onMoveUpClicked: (CompositeCatalog) -> Unit,
    onMoveDownClicked: (CompositeCatalog) -> Unit
  ) {
    val onCompositeCatalogItemClickedRemembered = rememberUpdatedState(newValue = onCompositeCatalogItemClicked)
    val onDeleteCompositeCatalogItemClickedRemembered = rememberUpdatedState(newValue = onDeleteCompositeCatalogItemClicked)
    val onMoveUpClickedRemembered = rememberUpdatedState(newValue = onMoveUpClicked)
    val onMoveDownClickedRemembered = rememberUpdatedState(newValue = onMoveDownClicked)

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(8.dp)
        .kurobaClickable(
          bounded = true,
          onClick = { onCompositeCatalogItemClickedRemembered.value.invoke(compositeCatalog) }
        )
    ) {

      KurobaComposeIcon(
        modifier = Modifier
          .size(32.dp)
          .align(Alignment.CenterVertically)
          .kurobaClickable(
            bounded = false,
            onClick = { onDeleteCompositeCatalogItemClickedRemembered.value.invoke(compositeCatalog) }
          ),
        drawableId = R.drawable.ic_clear_white_24dp,
        themeEngine = themeEngine
      )

      Spacer(modifier = Modifier.width(8.dp))

      Column(
        modifier = Modifier
          .weight(1f)
          .wrapContentHeight()
      ) {
        KurobaComposeText(
          color = chanTheme.textColorPrimaryCompose,
          fontSize = 15.sp,
          modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
          text = compositeCatalog.name
        )

        Spacer(modifier = Modifier.height(4.dp))

        val text = remember(key1 = compositeCatalog.compositeCatalogDescriptor) {
          val catalogDescriptorsGrouped = compositeCatalog.compositeCatalogDescriptor.catalogDescriptors
            .groupBy { catalogDescriptor -> catalogDescriptor.siteDescriptor() }

          return@remember buildString {
            catalogDescriptorsGrouped.entries.forEach { (siteDescriptor, catalogDescriptors) ->
              if (isNotEmpty()) {
                append(" + ")
              }

              val boardsString = catalogDescriptors
                .joinToString(
                  separator = ",",
                  transform = { catalogDescriptor -> "/${catalogDescriptor.boardCode()}/" }
                )

              append(siteDescriptor.siteName)
              append(":")
              append(boardsString)
            }
          }
        }

        KurobaComposeText(
          color = chanTheme.textColorSecondaryCompose,
          fontSize = 12.sp,
          modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
          text = text
        )
      }

      if (index > 0) {
        KurobaComposeIcon(
          modifier = Modifier
            .size(32.dp)
            .align(Alignment.CenterVertically)
            .kurobaClickable(
              bounded = false,
              onClick = { onMoveUpClickedRemembered.value.invoke(compositeCatalog) }
            ),
          drawableId = R.drawable.ic_baseline_arrow_upward_24,
          themeEngine = themeEngine
        )
      } else {
        Spacer(modifier = Modifier.size(32.dp))
      }

      Spacer(modifier = Modifier.width(8.dp))

      if (index < totalCount - 1) {
        KurobaComposeIcon(
          modifier = Modifier
            .size(32.dp)
            .align(Alignment.CenterVertically)
            .kurobaClickable(
              bounded = false,
              onClick = { onMoveDownClickedRemembered.value.invoke(compositeCatalog) }
            ),
          drawableId = R.drawable.ic_baseline_arrow_downward_24,
          themeEngine = themeEngine
        )
      } else {
        Spacer(modifier = Modifier.size(32.dp))
      }

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  companion object {
    private val FAB_SIZE = 52.dp
  }

}