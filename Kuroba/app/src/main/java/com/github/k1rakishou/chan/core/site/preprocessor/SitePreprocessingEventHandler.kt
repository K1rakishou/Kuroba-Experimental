package com.github.k1rakishou.chan.core.site.preprocessor

import com.github.k1rakishou.chan.core.helper.DialogFactory

class SitePreprocessingEventHandler(
  private val dialogFactory: DialogFactory
) {
  suspend fun handleEvent(event: SitePreprocessingEventQueue.Event) {
    // TODO:
  }
}