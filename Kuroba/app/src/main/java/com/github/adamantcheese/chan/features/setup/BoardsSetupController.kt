package com.github.adamantcheese.chan.features.setup

import android.content.Context
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor

class BoardsSetupController(
  context: Context,
  private val siteDescriptor: SiteDescriptor
) : Controller(context), BoardsSetupView {
  private val presenter = BoardsSetupPresenter(siteDescriptor)

  override fun onCreate() {
    super.onCreate()

    presenter.onCreate(this)
  }

  override fun onShow() {
    super.onShow()

    presenter.showActiveBoards()
  }

  override fun onDestroy() {
    super.onDestroy()

    presenter.onDestroy()
  }
}