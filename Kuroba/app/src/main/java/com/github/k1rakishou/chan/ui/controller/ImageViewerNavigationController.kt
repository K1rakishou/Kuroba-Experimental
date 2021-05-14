/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.view.View
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.ui.NavigationControllerContainerLayout
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.usecase.FilterOutHiddenImagesUseCase
import com.github.k1rakishou.chan.ui.controller.ImageViewerController.GoPostCallback
import com.github.k1rakishou.chan.ui.controller.ImageViewerController.ImageViewerCallback
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import javax.inject.Inject

class ImageViewerNavigationController(context: Context) : ToolbarNavigationController(context) {
  @Inject
  lateinit var filterOutHiddenImagesUseCase: FilterOutHiddenImagesUseCase

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    view = AppModuleAndroidUtils.inflate(context, R.layout.controller_navigation_image_viewer)
    container = view.findViewById<View>(R.id.navigation_image_viewer_container) as NavigationControllerContainerLayout

    setToolbar(view.findViewById(R.id.toolbar))
    requireToolbar().setCallback(this)
    requireToolbar().setIgnoreThemeChanges()
    requireToolbar().setCustomBackgroundColor(0x80000000L.toInt())
  }

  override fun onDestroy() {
    super.onDestroy()
    requireToolbar().removeCallback()
  }

  @JvmOverloads
  fun showImages(
    images: List<ChanPostImage>,
    index: Int,
    chanDescriptor: ChanDescriptor,
    imageViewerCallback: ImageViewerCallback?,
    goPostCallback: GoPostCallback? = null
  ) {
    val output = filterOutHiddenImagesUseCase.execute(
      FilterOutHiddenImagesUseCase.Input(images, index, false)
    )

    val filteredImages = output.images
    val newIndex = output.index

    if (filteredImages.isEmpty()) {
      showToast("No images left to show after filtering out images of hidden/removed posts");
      return
    }

    val imageViewerController = ImageViewerController(
      chanDescriptor,
      context,
      requireToolbar()
    )

    imageViewerController.setGoPostCallback(goPostCallback)
    pushController(imageViewerController, false)
    imageViewerController.setImageViewerCallback(imageViewerCallback)
    imageViewerController.presenter.showImages(filteredImages, newIndex, chanDescriptor)
  }

}