package com.github.k1rakishou.model.data.board

import com.github.k1rakishou.model.data.descriptor.BoardDescriptor

data class ChanBoard(
  val boardDescriptor: BoardDescriptor,
  var active: Boolean = false,
  val order: Int = 0,
  val name: String? = null,
  val perPage: Int = 15,
  val pages: Int = 10,
  val maxFileSize: Int = -1,
  val maxWebmSize: Int = -1,
  val maxCommentChars: Int = -1,
  val bumpLimit: Int = -1,
  val imageLimit: Int = -1,
  val cooldownThreads: Int = 0,
  val cooldownReplies: Int = 0,
  val cooldownImages: Int = 0,
  val customSpoilers: Int = -1,
  val description: String = "",
  val workSafe: Boolean = false,
  val spoilers: Boolean = false,
  val userIds: Boolean = false,
  val codeTags: Boolean = false,
  @Deprecated("delete me")
  val preuploadCaptcha: Boolean = false,
  @Deprecated("delete me")
  val countryFlags: Boolean = false,
  val mathTags: Boolean = false,
  @Deprecated("delete me")
  val archive: Boolean = false
) {

  fun boardName(): String = name ?: ""
  fun siteName(): String =  boardDescriptor.siteName()
  fun boardCode(): String = boardDescriptor.boardCode
  fun formattedBoardCode(): String = "/${boardCode()}/"

  fun boardSupportsFlagSelection(): Boolean {
    val is4chan = boardDescriptor.siteDescriptor.is4chan()
    if (is4chan && boardCode() == "pol") {
      return true
    }

    return false
  }

  companion object {
    const val DEFAULT_CATALOG_SIZE = 150

    @JvmStatic
    fun create(boardDescriptor: BoardDescriptor, boardName: String?): ChanBoard {
      return ChanBoard(
        boardDescriptor = boardDescriptor,
        name = boardName
      )
    }
  }

}