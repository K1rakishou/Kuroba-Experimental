package com.github.k1rakishou.chan.features.setup

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyItemScope
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
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.concurrency.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeDraggableElementContainer
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeErrorMessageNoInsets
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.compose_task.rememberCancellableCoroutineTask
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyColumnWithFastScroller
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalContentPaddings
import com.github.k1rakishou.chan.ui.compose.reorder.ReorderableItem
import com.github.k1rakishou.chan.ui.compose.reorder.ReorderableLazyListState
import com.github.k1rakishou.chan.ui.compose.reorder.detectReorder
import com.github.k1rakishou.chan.ui.compose.reorder.rememberReorderableLazyListState
import com.github.k1rakishou.chan.ui.compose.reorder.reorderable
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.catalog.CompositeCatalog
import javax.inject.Inject

class CompositeCatalogsSetupController(
  context: Context
) : Controller(context) {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val viewModel by viewModelByKey<CompositeCatalogsSetupControllerViewModel>()

  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(controllerScope)

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags()
    )

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.Id(R.string.controller_composite_catalogs_setup_title)
      )
    )

    viewModel.reload()

    view = ComposeView(context).apply {
      setContent {
        ComposeEntrypoint {
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

  @Composable
  private fun BuildContent() {
    val chanTheme = LocalChanTheme.current
    val contentPaddings = LocalContentPaddings.current

    val compositeCatalogs = viewModel.compositeCatalogs

    val reorderTask = rememberCancellableCoroutineTask()
    val reorderableState = rememberReorderableLazyListState(
      onMove = { from, to ->
        reorderTask.launch {
          viewModel
            .move(fromIndex = from.index, toIndex = to.index)
            .toastOnError(longToast = true)
            .ignore()
        }
      },
      onDragEnd = { _, _ ->
        reorderTask.launch {
          viewModel
            .onMoveEnd()
            .toastOnError(longToast = true)
            .ignore()
        }
      }
    )

    val paddingValues = remember(contentPaddings) {
      contentPaddings
        .asPaddingValues(controllerKey)
    }

    Box(
      modifier = Modifier.fillMaxSize()
    ) {
      LazyColumnWithFastScroller(
        state = reorderableState.listState,
        modifier = Modifier
          .fillMaxSize()
          .reorderable(reorderableState),
        contentPadding = paddingValues,
        draggableScrollbar = false,
        content = {
          if (compositeCatalogs.isEmpty()) {
            item {
              KurobaComposeErrorMessageNoInsets(
                modifier = Modifier.fillParentMaxSize(),
                errorMessage = stringResource(id = R.string.controller_composite_catalogs_empty_text)
              )
            }

            return@LazyColumnWithFastScroller
          }

          items(
            count = compositeCatalogs.size,
            key = { idx -> compositeCatalogs[idx].name },
            itemContent = { index ->
              val compositeCatalog = compositeCatalogs.get(index)

              BuildCompositeCatalogItem(
                key = compositeCatalog.name,
                chanTheme = chanTheme,
                reorderableState = reorderableState,
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
          )
        }
      )

      val fabBottomOffset = globalWindowInsetsManager.bottomDp() + 16.dp

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
  private fun LazyItemScope.BuildCompositeCatalogItem(
    key: String,
    chanTheme: ChanTheme,
    reorderableState: ReorderableLazyListState,
    compositeCatalog: CompositeCatalog,
    onCompositeCatalogItemClicked: (CompositeCatalog) -> Unit,
    onDeleteCompositeCatalogItemClicked: (CompositeCatalog) -> Unit
  ) {
    val onCompositeCatalogItemClickedRemembered = rememberUpdatedState(newValue = onCompositeCatalogItemClicked)
    val onDeleteCompositeCatalogItemClickedRemembered = rememberUpdatedState(newValue = onDeleteCompositeCatalogItemClicked)

    ReorderableItem(
      reorderableState = reorderableState,
      key = key,
    ) { isDragging ->
      KurobaComposeDraggableElementContainer(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .padding(horizontal = 8.dp, vertical = 4.dp)
          .kurobaClickable(
            bounded = true,
            onClick = { onCompositeCatalogItemClickedRemembered.value.invoke(compositeCatalog) }
          ),
        isDragging = isDragging
      ) {
        Row(
          modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
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
              modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth(),
              color = chanTheme.textColorPrimaryCompose,
              fontSize = 15.ktu,
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
              modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth(),
              color = chanTheme.textColorSecondaryCompose,
              fontSize = 12.ktu,
              text = text
            )
          }

          KurobaComposeIcon(
            modifier = Modifier
              .size(32.dp)
              .align(Alignment.CenterVertically)
              .detectReorder(reorderableState),
            drawableId = R.drawable.ic_baseline_reorder_24
          )

          Spacer(modifier = Modifier.width(8.dp))
        }
      }
    }
  }

  companion object {
    private val FAB_SIZE = 52.dp
  }

}