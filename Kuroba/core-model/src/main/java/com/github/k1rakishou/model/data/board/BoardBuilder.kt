package com.github.k1rakishou.model.data.board

import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

class BoardBuilder(
  private val siteDescriptor: SiteDescriptor
) {
  var code: String? = null
  var order: Int = 0
  var name: String? = null
  var perPage: Int = 15
  var pages: Int = 10
  var maxFileSize: Int = -1
  var maxWebmSize: Int = -1
  var maxMediaWidth: Int = -1
  var maxMediaHeight: Int = -1
  var maxCommentChars: Int = -1
  var bumpLimit: Int = -1
  var imageLimit: Int = -1
  var cooldownThreads: Int = -1
  var cooldownReplies: Int = -1
  var cooldownImages: Int = -1
  var customSpoilers: Int = -1
  var description: String = ""
  var saved: Boolean = false
  var workSafe: Boolean = false
  var spoilers: Boolean = false
  var userIds: Boolean = false
  var countryFlags: Boolean = false
  var isUnlimitedCatalog: Boolean = false

  fun hasMissingInfo(): Boolean {
    return name.isNullOrEmpty() || code.isNullOrEmpty() || perPage < 0 || pages < 0
  }

  fun boardDescriptor(): BoardDescriptor = BoardDescriptor.create(siteDescriptor, code!!)

  fun toChanBoard(prevChanBoard: ChanBoard?): ChanBoard {
    val active = prevChanBoard?.active ?: false
    val order = prevChanBoard?.order ?: this.order

    return ChanBoard(
      boardDescriptor = boardDescriptor(),
      active = active,
      order = order,
      name = name,
      perPage = perPage,
      pages = pages,
      maxFileSize = maxFileSize,
      maxWebmSize = maxWebmSize,
      maxMediaWidth = maxMediaWidth,
      maxMediaHeight = maxMediaHeight,
      maxCommentChars = maxCommentChars,
      bumpLimit = bumpLimit,
      imageLimit = imageLimit,
      cooldownThreads = cooldownThreads,
      cooldownReplies = cooldownReplies,
      cooldownImages = cooldownImages,
      customSpoilers = customSpoilers,
      description = description,
      workSafe = workSafe,
      spoilers = spoilers,
      userIds = userIds,
      countryFlags = countryFlags,
      isUnlimitedCatalog = isUnlimitedCatalog,
    )
  }

}