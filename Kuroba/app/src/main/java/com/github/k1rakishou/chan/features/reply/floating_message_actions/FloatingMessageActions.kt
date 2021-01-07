package com.github.k1rakishou.chan.features.reply.floating_message_actions

import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils

interface IFloatingReplyMessageClickAction {
  fun execute()
}

class Chan4OpenBannedUrlClickAction() : IFloatingReplyMessageClickAction {
  private val url = "https://4chan.org/banned"

  override fun execute() {
    AppModuleAndroidUtils.openLink(url)
  }

}