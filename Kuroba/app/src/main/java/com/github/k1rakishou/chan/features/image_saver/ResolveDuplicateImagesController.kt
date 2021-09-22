package com.github.k1rakishou.chan.features.image_saver

import android.content.Context
import android.widget.CompoundButton
import android.widget.FrameLayout
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.features.image_saver.epoxy.epoxyDuplicateImageView
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerActivity
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCheckBox
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.RecyclerUtils
import com.github.k1rakishou.chan.utils.RecyclerUtils.doOnRecyclerScrollStopped
import com.github.k1rakishou.chan.utils.RecyclerUtils.restoreScrollPosition
import com.github.k1rakishou.chan.utils.addOneshotModelBuildListener
import com.github.k1rakishou.chan.utils.setEnabledFast
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import com.github.k1rakishou.persist_state.IndexAndTop
import com.google.gson.Gson
import dagger.Lazy
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

class ResolveDuplicateImagesController(
  context: Context,
  private val uniqueId: String,
  private val imageSaverOptionsJson: String
) : BaseFloatingController(context), ResolveDuplicateImagesView {

  @Inject
  lateinit var gson: Gson
  @Inject
  lateinit var fileManager: FileManager
  @Inject
  lateinit var chanThreadManager: ChanThreadManager
  @Inject
  lateinit var imageDownloadRequestRepository: ImageDownloadRequestRepository
  @Inject
  lateinit var imageSaverV2: Lazy<ImageSaverV2>

  private lateinit var epoxyRecyclerView: EpoxyRecyclerView
  private lateinit var resolveButton: ColorizableBarButton
  private lateinit var selectAllFromServer: ColorizableCheckBox
  private lateinit var selectAllLocal: ColorizableCheckBox
  private lateinit var selectAllDuplicates: ColorizableCheckBox

  private val checkState = mutableMapOf<CompoundButton, Boolean>()
  private var indexAndTop: IndexAndTop? = null

  private val resolveDuplicateImagesPresenter by lazy {
    return@lazy ResolveDuplicateImagesPresenter(
      uniqueId,
      gson.fromJson(imageSaverOptionsJson, ImageSaverV2Options::class.java),
      fileManager,
      chanThreadManager,
      imageDownloadRequestRepository,
      imageSaverV2.get()
    )
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun getLayoutId(): Int = R.layout.controller_resolve_duplicate_images

  override fun onCreate() {
    super.onCreate()

    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    epoxyRecyclerView.doOnRecyclerScrollStopped { rv ->
      indexAndTop = RecyclerUtils.getIndexAndTop(rv)
    }

    resolveButton = view.findViewById(R.id.resolve_button)
    selectAllFromServer = view.findViewById(R.id.select_all_from_server)
    selectAllLocal = view.findViewById(R.id.select_all_local)
    selectAllDuplicates = view.findViewById(R.id.select_all_duplicates)

    checkState.clear()
    checkState[selectAllFromServer] = false
    checkState[selectAllLocal] = false
    checkState[selectAllDuplicates] = false

    val cancelButton = view.findViewById<ColorizableBarButton>(R.id.cancel_button)
    val outsideArea = view.findViewById<FrameLayout>(R.id.outside_area)

    resolveButton.setEnabledFast(false)

    selectAllFromServer.setOnCheckedChangeListener { buttonView, isChecked ->
      onGlobalCheckChanged(buttonView, isChecked)
    }
    selectAllLocal.setOnCheckedChangeListener { buttonView, isChecked ->
      onGlobalCheckChanged(buttonView, isChecked)
    }
    selectAllDuplicates.setOnCheckedChangeListener { buttonView, isChecked ->
      onGlobalCheckChanged(buttonView, isChecked)
    }

    cancelButton.setOnClickListener {
      pop()
    }
    outsideArea.setOnClickListener {
      pop()
    }
    resolveButton.setOnClickListener {
      resolveDuplicateImagesPresenter.resolve()
    }

    mainScope.launch {
      resolveDuplicateImagesPresenter.listenForStateUpdates()
        .collect { state -> renderState(state) }
    }

    resolveDuplicateImagesPresenter.onCreate(this)
  }

  private fun onGlobalCheckChanged(buttonView: CompoundButton, isChecked: Boolean) {
    val prevCheckState = checkState.values.toBooleanArray()

    if (isChecked) {
      checkState.forEach { (view, _) ->
        checkState[view] = view === buttonView
        view.isChecked = view === buttonView
      }
    } else {
      checkState[buttonView] = false
    }

    val newCheckState = checkState.values.toBooleanArray()

    if (prevCheckState.contentEquals(newCheckState)) {
      return
    }

    if (checkState[selectAllFromServer] == true) {
      resolveDuplicateImagesPresenter.updateManyDuplicateImages(
        batchUpdate = ResolveDuplicateImagesPresenter.BatchUpdate.SelectAllFromServer
      )

      return
    }

    if (checkState[selectAllLocal] == true) {
      resolveDuplicateImagesPresenter.updateManyDuplicateImages(
        batchUpdate = ResolveDuplicateImagesPresenter.BatchUpdate.SelectAllLocal
      )

      return
    }

    if (checkState[selectAllDuplicates] == true) {
      resolveDuplicateImagesPresenter.updateManyDuplicateImages(
        batchUpdate = ResolveDuplicateImagesPresenter.BatchUpdate.SelectAllDuplicates
      )

      return
    }

    resolveDuplicateImagesPresenter.updateManyDuplicateImages(
      batchUpdate = ResolveDuplicateImagesPresenter.BatchUpdate.SelectNone
    )
  }

  override fun onDestroy() {
    super.onDestroy()

    indexAndTop = null
    resolveDuplicateImagesPresenter.onDestroy()
  }

  override fun showToastMessage(message: String) {
    showToast(message)
  }

  override fun onDuplicateResolvingCompleted() {
    pop()
  }

  private fun renderState(state: ResolveDuplicateImagesState) {
    epoxyRecyclerView.withModels {
      if (state !is ResolveDuplicateImagesState.Data) {
        resolveButton.setEnabledFast(false)

        when (state) {
          ResolveDuplicateImagesState.Loading -> {
            epoxyLoadingView {
              id("resolve_duplicates_controller_loading_view")
            }
          }
          ResolveDuplicateImagesState.Empty -> {
            epoxyTextView {
              id("resolve_duplicates_controller_empty_view")
              message(getString(R.string.image_saver_no_unresolved_images_found))
            }
          }
          is ResolveDuplicateImagesState.Error -> {
            epoxyErrorView {
              id("resolve_duplicates_controller_error_view")
              errorMessage(state.throwable.errorMessageOrClassName())
            }
          }
          is ResolveDuplicateImagesState.Data -> throw IllegalStateException("Must not be handled here")
        }

        return@withModels
      }

      renderDataState(state)
    }
  }

  private fun EpoxyController.renderDataState(dataState: ResolveDuplicateImagesState.Data) {
    check(dataState.duplicateImages.isNotEmpty()) { "dataState.duplicateImages is empty!" }

    val canEnableResolveButton = dataState.duplicateImages
      .all { duplicateImage ->
        return@all duplicateImage.resolution != ImageSaverV2Options.DuplicatesResolution.AskWhatToDo
      }

    resolveButton.setEnabledFast(canEnableResolveButton)

    addOneshotModelBuildListener {
      epoxyRecyclerView.restoreScrollPosition(indexAndTop)
    }

    dataState.duplicateImages.forEach { duplicateImage ->
      epoxyDuplicateImageView {
        id("epoxy_duplicate_image_view_${duplicateImage.hashCode()}")
        serverImage(duplicateImage.serverImage)
        localImage(duplicateImage.localImage)
        dupImage(duplicateImage.dupImage)
        locked(duplicateImage.locked)
        duplicateResolution(duplicateImage.resolution)
        onImageCheckboxClickListener { clickedDuplicateImage ->
          when (clickedDuplicateImage) {
            is ServerImage -> {
              selectAllLocal.isChecked = false
              selectAllDuplicates.isChecked = false
            }
            is LocalImage -> {
              selectAllFromServer.isChecked = false
              selectAllDuplicates.isChecked = false
            }
            is DupImage -> {
              selectAllLocal.isChecked = false
              selectAllFromServer.isChecked = false
            }
          }

          resolveDuplicateImagesPresenter.onDuplicateImageClicked(clickedDuplicateImage)
        }
        onImageClickListener { serverImageUrl, localImageUri ->
          val mediaList = mutableListOf<MediaLocation>()

          if (serverImageUrl != null) {
            mediaList += MediaLocation.Remote(serverImageUrl.toString())
          }

          if (localImageUri != null) {
            mediaList += MediaLocation.Local(localImageUri.toString(), isUri = true)
          }

          if (mediaList.isEmpty()) {
            showToast("No valid media found")
            return@onImageClickListener
          }

          MediaViewerActivity.mixedMedia(context, mediaList)
        }
      }
    }
  }

}
