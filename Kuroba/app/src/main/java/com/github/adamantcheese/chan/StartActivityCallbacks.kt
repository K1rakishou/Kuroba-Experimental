package com.github.adamantcheese.chan

import com.github.adamantcheese.chan.controller.Controller

interface StartActivityCallbacks {
  fun openControllerWrappedIntoBottomNavAwareController(controller: Controller)
}