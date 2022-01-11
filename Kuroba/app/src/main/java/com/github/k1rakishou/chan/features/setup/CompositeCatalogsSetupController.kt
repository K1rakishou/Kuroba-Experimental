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
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.reorder.ReorderableState
import com.github.k1rakishou.chan.ui.compose.reorder.detectReorder
import com.github.k1rakishou.chan.ui.compose.reorder.draggedItem
import com.github.k1rakishou.chan.ui.compose.reorder.rememberReorderState
import com.github.k1rakishou.chan.ui.compose.reorder.reorderable
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.catalog.CompositeCatalog
import javax.inject.Inject

class CompositeCatalogsSetupController(
  context: Context
) : Controller(context), WindowInsetsListener {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val viewModel by lazy {
    requireComponentActivity().viewModelByKey<CompositeCatalogsSetupControllerViewModel>()
  }
  private val bottomPadding = mutableStateOf(0)
  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(mainScope)

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    navigation.title = getString(R.string.controller_composite_catalogs_setup_title)

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
    onInsetsChanged()
    viewModel.reload()

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
  }

  override fun onInsetsChanged() {
    bottomPadding.value = calculateBottomPaddingForRecyclerInDp(
      globalWindowInsetsManager = globalWindowInsetsManager,
      mainControllerCallbacks = null
    )
  }

  @Composable
  private fun BuildContent() {
    val chanTheme = LocalChanTheme.current
    val reorderState = rememberReorderState()
    val compositeCatalogs = viewModel.compositeCatalogs

    val padding by bottomPadding
    val bottomPadding = remember(key1 = padding) { PaddingValues(bottom = padding.dp) }

    Box(modifier = Modifier.fillMaxSize()) {
      if (compositeCatalogs.isNotEmpty()) {
        LazyColumn(
          state = reorderState.listState,
          contentPadding = bottomPadding,
          modifier = Modifier
            .fillMaxSize()
            .simpleVerticalScrollbar(reorderState.listState, chanTheme, bottomPadding)
            .reorderable(
              state = reorderState,
              onMove = { from, to ->
                viewModel
                  .move(fromIndex = from, toIndex = to)
                  .toastOnError(longToast = true)
                  .ignore()
              },
              onDragEnd = { _, _ ->
                viewModel
                  .onMoveEnd()
                  .toastOnError(longToast = true)
                  .ignore()
              }
            ),
          content = {
            items(compositeCatalogs.size) { index ->
              val compositeCatalog = compositeCatalogs.get(index)

              BuildCompositeCatalogItem(
                index = index,
                chanTheme = chanTheme,
                reorderState = reorderState,
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

      val fabBottomOffset = if (ChanSettings.isSplitLayoutMode()) {
        globalWindowInsetsManager.bottomDp()
      } else {
        16.dp
      }

      FloatingActionButton(
        modifier = Modifier
          .size(FAB_SIZE)
          .align(Alignment.BottomEnd)
          .offset(x = (-16).dp, y = -fabBottomOffset),
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
    chanTheme: ChanTheme,
    reorderState: ReorderableState,
    compositeCatalog: CompositeCatalog,
    onCompositeCatalogItemClicked: (CompositeCatalog) -> Unit,
    onDeleteCompositeCatalogItemClicked: (CompositeCatalog) -> Unit
  ) {
    val onCompositeCatalogItemClickedRemembered =
      rememberUpdatedState(newValue = onCompositeCatalogItemClicked)
    val onDeleteCompositeCatalogItemClickedRemembered =
      rememberUpdatedState(newValue = onDeleteCompositeCatalogItemClicked)

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(8.dp)
        .kurobaClickable(
          bounded = true,
          onClick = { onCompositeCatalogItemClickedRemembered.value.invoke(compositeCatalog) }
        )
        .draggedItem(reorderState.offsetByIndex(index))
        .background(chanTheme.backColorCompose)
    ) {

      KurobaComposeIcon(
        modifier = Modifier
          .size(32.dp)
          .align(Alignment.CenterVertically)
          .kurobaClickable(
            bounded = false,
            onClick = { onDeleteCompositeCatalogItemClickedRemembered.value.invoke(compositeCatalog) }
          ),
        drawableId = R.drawable.ic_clear_white_24dp
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

        val text = remember(key1 = compositeCatalog.compositeCatalogDescriptor.catalogDescriptors) {
          return@remember buildString {
            compositeCatalog.compositeCatalogDescriptor.catalogDescriptors.forEach { catalogDescriptor ->
              if (isNotEmpty()) {
                append(" + ")
              }

              append(catalogDescriptor.siteDescriptor().siteName)
              append("/${catalogDescriptor.boardCode()}/")
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

      KurobaComposeIcon(
        modifier = Modifier
          .size(32.dp)
          .align(Alignment.CenterVertically)
          .detectReorder(reorderState),
        drawableId = R.drawable.ic_baseline_reorder_24
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  companion object {
    private val FAB_SIZE = 52.dp
  }

}