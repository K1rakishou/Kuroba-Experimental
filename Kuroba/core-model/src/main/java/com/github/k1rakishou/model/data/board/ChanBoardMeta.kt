package com.github.k1rakishou.model.data.board

// Non-persistable board info (stuff like per-board captcha info, reply message length, reply file
// count/size, etc) which is usually loaded when board catalog is loaded/updated.
// Only stored in the memory since it's always refreshed every time we load catalog.
sealed class ChanBoardMeta

data class LynxchanBoardMeta(
  // Indicates the captcha mode for the board.
  // 0 means no captcha,
  // 1 means only for new threads,
  // 2 means for all posts on the board.
  val boardCaptchaType: CaptchaType,
  val maxFileCount: Int
) : ChanBoardMeta() {

  enum class CaptchaType(val value: Int) {
    NoCaptcha(0),
    OnlyForThreadCreation(1),
    ForAllPosting(2);

    companion object {
      fun fromValue(value: Int): CaptchaType {
        return when (value) {
          0 -> NoCaptcha
          1 -> OnlyForThreadCreation
          else -> ForAllPosting
        }
      }
    }
  }

}