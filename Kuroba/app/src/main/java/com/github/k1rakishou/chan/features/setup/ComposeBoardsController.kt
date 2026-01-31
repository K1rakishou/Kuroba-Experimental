package com.github.k1rakishou.chan.features.setup

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeDraggableElementContainer
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.consumeClicks
import com.github.k1rakishou.chan.ui.compose.image.ImageLoaderRequest
import com.github.k1rakishou.chan.ui.compose.image.ImageLoaderRequestData
import com.github.k1rakishou.chan.ui.compose.image.KurobaComposeImage
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyColumnWithFastScroller
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.reorder.ReorderableItem
import com.github.k1rakishou.chan.ui.compose.reorder.ReorderableLazyListState
import com.github.k1rakishou.chan.ui.compose.reorder.detectReorder
import com.github.k1rakishou.chan.ui.compose.reorder.rememberReorderableLazyListState
import com.github.k1rakishou.chan.ui.compose.reorder.reorderable
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.model.data.catalog.CompositeCatalog
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject

class ComposeBoardsController(
  context: Context,
  private val prevCompositeCatalog: CompositeCatalog?
) : BaseFloatingComposeController(context) {

  @Inject
  lateinit var imageLoaderDeprecated: ImageLoaderDeprecated
  @Inject
  lateinit var siteManager: SiteManager

  private val viewModel by viewModelByKey<ComposeBoardsControllerViewModel>()

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    viewModel.resetCompositionSlots(prevCompositeCatalog)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val chanTheme = LocalChanTheme.current
    val compositionSlots = viewModel.catalogCompositionSlots
    val focusManager = LocalFocusManager.current

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .consumeClicks()
        .align(Alignment.Center)
        .background(chanTheme.backColorCompose)
    ) {
      BuildHeader()

      val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to -> viewModel.move(from.index, to.index) }
      )

      LazyColumnWithFastScroller(
        state = reorderableState.listState,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, false)
          .reorderable(reorderableState),
        draggableScrollbar = false,
        content = {
          items(compositionSlots.size) { index ->
            val compositionSlot = compositionSlots[index]

            BuildCompositionSlot(
              index = index,
              reorderableState = reorderableState,
              catalogCompositionSlot = compositionSlot,
              onAddOrReplaceBoardClicked = { clickedIndex ->
                val controller = ComposeBoardsSelectorController(
                  context = context,
                  currentlyComposedBoards = viewModel.currentlyComposedBoards(),
                  onBoardSelected = { boardDescriptor -> viewModel.updateSlot(clickedIndex, boardDescriptor) }
                )

                presentController(controller)
              },
              removeBoardClicked = { clickedIndex -> viewModel.clearSlot(clickedIndex) }
            )
          }
        }
      )

      BuildFooter(
        compositionSlots = compositionSlots,
        onCancelClicked = {
          focusManager.clearFocus(force = true)
          pop()
        },
        onSaveClicked = {
          controllerScope.launch { createOrUpdateCompositeCatalog() }
        }
      )
    }
  }

  private suspend fun createOrUpdateCompositeCatalog() {
    val catalogDescriptors = viewModel.catalogCompositionSlots
      .toList()
      .mapNotNull { compositionSlot ->
        if (compositionSlot is ComposeBoardsControllerViewModel.CatalogCompositionSlot.Empty) {
          return@mapNotNull null
        }

        compositionSlot as ComposeBoardsControllerViewModel.CatalogCompositionSlot.Occupied
        return@mapNotNull compositionSlot.catalogDescriptor
      }

    val compositeCatalogDescriptor = ChanDescriptor.CompositeCatalogDescriptor.createSafe(catalogDescriptors)
      ?: return

    if (this.prevCompositeCatalog == null && viewModel.alreadyExists(compositeCatalogDescriptor)) {
      val boardsJoined = catalogDescriptors
        .map { catalogDescriptor -> catalogDescriptor.boardDescriptor }
        .joinToString(separator = ",", transform = { boardDescriptor -> boardDescriptor.userReadableString() })

      showToast(getString(R.string.controller_compose_boards_composite_catalog_already_exists, boardsJoined))
      return
    }

    val compositeCatalogName = suspendCancellableCoroutine<String> { cancellableContinuation ->
      val alertDialogHandle = dialogFactory.createSimpleDialogWithInput(
        context = context,
        titleText = getString(R.string.controller_compose_boards_enter_composite_catalog_name_title),
        descriptionText = getString(
          R.string.controller_compose_boards_enter_composite_catalog_name_descriptor,
          ComposeBoardsControllerViewModel.MAX_COMPOSITE_CATALOG_TITLE_LENGTH
        ),
        inputType = DialogFactory.DialogInputType.String,
        defaultValue = this.prevCompositeCatalog?.name ?: getString(R.string.controller_compose_boards_default_name),
        onValueEntered = { compositeCatalogName -> cancellableContinuation.resumeValueSafe(compositeCatalogName) },
        onDismissListener = { cancellableContinuation.cancel() }
      )

      cancellableContinuation.invokeOnCancellation { cause ->
        if (cause == null) {
          return@invokeOnCancellation
        }

        alertDialogHandle?.dismiss()
      }
    }

    if (compositeCatalogName.isBlank()) {
      showToast(getString(R.string.controller_compose_boards_cannot_use_blank_name))
      return
    }

    controllerScope.launch {
      val result = viewModel.createOrUpdateCompositeCatalog(
        newCompositeCatalogName = compositeCatalogName,
        prevCompositeCatalog = prevCompositeCatalog
      )

      when (result) {
        is ModularResult.Error -> {
          val message = getString(
            R.string.controller_compose_boards_failed_to_create_composite_catalog,
            result.error.errorMessageOrClassName()
          )

          showToast(message)
        }
        is ModularResult.Value -> {
          showToast(getString(R.string.controller_compose_boards_create_success))
          pop()
        }
      }
    }

  }

  @Composable
  private fun BuildHeader() {
    KurobaComposeText(
      textAlign = TextAlign.Center,
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(4.dp),
      text = stringResource(
        id = R.string.controller_compose_boards_title,
        ChanDescriptor.CompositeCatalogDescriptor.MIN_CATALOGS_COUNT,
        ChanDescriptor.CompositeCatalogDescriptor.MAX_CATALOGS_COUNT
      )
    )
  }

  @Composable
  private fun BuildCompositionSlot(
    index: Int,
    reorderableState: ReorderableLazyListState,
    catalogCompositionSlot: ComposeBoardsControllerViewModel.CatalogCompositionSlot,
    onAddOrReplaceBoardClicked: (Int) -> Unit,
    removeBoardClicked: (Int) -> Unit
  ) {
    val onAddOrReplaceBoardClickedRemembered = rememberUpdatedState(newValue = onAddOrReplaceBoardClicked)
    val removeBoardClickedRemembered = rememberUpdatedState(newValue = removeBoardClicked)

    ReorderableItem(
      state = reorderableState,
      key = null,
      index = index
    ) { isDragging ->
      KurobaComposeDraggableElementContainer(
        modifier = Modifier
          .fillMaxWidth()
          .height(COMPOSITION_SLOT_ITEM_HEIGHT)
          .padding(horizontal = 8.dp, vertical = 4.dp)
          .kurobaClickable(
            bounded = true,
            onClick = { onAddOrReplaceBoardClickedRemembered.value.invoke(index) }
          ),
        isDragging = isDragging
      ) {
        Box(modifier = Modifier.fillMaxSize()) {
          when (catalogCompositionSlot) {
            ComposeBoardsControllerViewModel.CatalogCompositionSlot.Empty -> {
              KurobaComposeText(
                modifier = Modifier
                  .fillMaxWidth()
                  .wrapContentHeight()
                  .align(Alignment.Center),
                fontSize = 16.ktu,
                textAlign = TextAlign.Center,
                text = stringResource(id = R.string.controller_compose_boards_click_to_add_board)
              )
            }
            is ComposeBoardsControllerViewModel.CatalogCompositionSlot.Occupied -> {
              Row(modifier = Modifier.fillMaxSize()) {

                KurobaComposeIcon(
                  modifier = Modifier
                    .size(32.dp)
                    .padding(start = 8.dp)
                    .align(Alignment.CenterVertically)
                    .kurobaClickable(
                      bounded = false,
                      onClick = { removeBoardClickedRemembered.value.invoke(index) }
                    ),
                  drawableId = R.drawable.ic_clear_white_24dp
                )

                Row(
                  modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                ) {
                  val imageLoaderRequest = remember(key1 = catalogCompositionSlot) {
                    val siteDescriptor = catalogCompositionSlot.catalogDescriptor.siteDescriptor()
                    val iconUrl = siteManager.bySiteDescriptorAndActive(siteDescriptor)?.icon()?.url!!

                    val data = ImageLoaderRequestData.Url(
                      httpUrl = iconUrl,
                      cacheFileType = CacheFileType.SiteIcon
                    )

                    return@remember ImageLoaderRequest(data)
                  }

                  KurobaComposeImage(
                    modifier = Modifier
                      .size(28.dp)
                      .align(Alignment.CenterVertically),
                    controllerKey = null,
                    request = imageLoaderRequest,
                    contentScale = ContentScale.Fit
                  )

                  val text = remember(key1 = catalogCompositionSlot) {
                    buildString {
                      append(catalogCompositionSlot.catalogDescriptor.siteDescriptor().siteName)
                      append("/")
                      append(catalogCompositionSlot.catalogDescriptor.boardDescriptor.boardCode)
                      append("/")
                    }
                  }

                  Spacer(modifier = Modifier.height(8.dp))

                  KurobaComposeText(
                    modifier = Modifier
                      .wrapContentSize()
                      .padding(horizontal = 4.dp)
                      .align(Alignment.CenterVertically),
                    fontSize = 16.ktu,
                    textAlign = TextAlign.Center,
                    text = text
                  )
                }

                KurobaComposeIcon(
                  modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.CenterVertically)
                    .detectReorder(reorderableState)
                    .padding(end = 8.dp),
                  drawableId = R.drawable.ic_baseline_reorder_24
                )
              }
            }
          }
        }
      }
    }
  }

  @Composable
  private fun BuildFooter(
    compositionSlots: List<ComposeBoardsControllerViewModel.CatalogCompositionSlot>,
    onCancelClicked: () -> Unit,
    onSaveClicked: () -> Unit
  ) {
    val onCancelClickedRemembered = rememberUpdatedState(newValue = onCancelClicked)
    val onSaveClickedRemembered = rememberUpdatedState(newValue = onSaveClicked)

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .padding(4.dp)
    ) {
      Spacer(modifier = Modifier.weight(1f))

      KurobaComposeTextBarButton(
        modifier = Modifier
          .wrapContentSize(),
        onClick = { onCancelClickedRemembered.value.invoke() },
        text = stringResource(id = R.string.cancel)
      )

      Spacer(modifier = Modifier.width(16.dp))

      val currentCatalogDescriptors = compositionSlots.mapNotNull { compositionSlot ->
        if (compositionSlot is ComposeBoardsControllerViewModel.CatalogCompositionSlot.Empty) {
          return@mapNotNull null
        }

        compositionSlot as ComposeBoardsControllerViewModel.CatalogCompositionSlot.Occupied
        return@mapNotNull compositionSlot.catalogDescriptor
      }

      val buttonText = if (prevCompositeCatalog != null) {
        stringResource(id = R.string.update)
      } else {
        stringResource(id = R.string.create)
      }

      KurobaComposeTextBarButton(
        enabled = currentCatalogDescriptors.size >= 2,
        modifier = Modifier.wrapContentSize(),
        onClick = { onSaveClickedRemembered.value.invoke() },
        text = buttonText
      )
    }
  }

  companion object {
    private val COMPOSITION_SLOT_ITEM_HEIGHT = 56.dp
  }

}