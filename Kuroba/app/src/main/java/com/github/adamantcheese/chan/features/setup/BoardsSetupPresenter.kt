package com.github.adamantcheese.chan.features.setup

import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.core.base.BasePresenter
import com.github.adamantcheese.chan.core.manager.BoardManager
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.launch
import javax.inject.Inject

class BoardsSetupPresenter(
  private val siteDescriptor: SiteDescriptor
) : BasePresenter<BoardsSetupView>() {

  @Inject
  lateinit var boardManager: BoardManager

  override fun onCreate(view: BoardsSetupView) {
    super.onCreate(view)
    Chan.inject(this)

  }

  fun showActiveBoards() {
    scope.launch {
      boardManager.awaitUntilInitialized()


    }
  }
}