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
  var maxCommentChars: Int = -1
  var bumpLimit: Int = -1
  var imageLimit: Int = -1
  var cooldownThreads: Int = 0
  var cooldownReplies: Int = 0
  var cooldownImages: Int = 0
  var customSpoilers: Int = -1
  var description: String = ""
  var saved: Boolean = false
  var workSafe: Boolean = false
  var spoilers: Boolean = false
  var userIds: Boolean = false
  var codeTags: Boolean = false
  var preuploadCaptcha: Boolean = false
  var countryFlags: Boolean = false
  var mathTags: Boolean = false
  var archive: Boolean = false
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
      // We don't persist synthetic boards so we assume all boards coming from the DB are not synthetic
      synthetic = false,
      order = order,
      name = name,
      perPage = perPage,
      pages = pages,
      maxFileSize = maxFileSize,
      maxWebmSize = maxWebmSize,
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
      codeTags = codeTags,
      preuploadCaptcha = preuploadCaptcha,
      countryFlags = countryFlags,
      mathTags = mathTags,
      archive = archive,
      isUnlimitedCatalog = isUnlimitedCatalog,
    )
  }

}