package com.github.k1rakishou.chan.features.image_saver

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController

class ResolveDuplicateImagesController(
  context: Context,
  private val uniqueId: String
) : BaseFloatingController(context) {

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun getLayoutId(): Int = R.layout.controller_resolve_duplicate_images

}