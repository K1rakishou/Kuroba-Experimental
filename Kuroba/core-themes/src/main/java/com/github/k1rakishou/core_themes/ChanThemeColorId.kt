package com.github.k1rakishou.core_themes

/**
 * Ids must be unique!
 * Do not change the ids!
 * Do not remove old ids!
 * Only add new ids!
 * This thing is serialized in the DB, changing it may cause unexpected results!
 *
 * When changing this DO NOT FORGET to also change ChanThemeColorId in the kuroba_ex_native library !!!
 * */
enum class ChanThemeColorId(val id: Int) {
  PostSubjectColor(0),
  PostNameColor(1),
  AccentColor(2),
  PostInlineQuoteColor(3),
  PostQuoteColor(4),
  BackColorSecondary(5),
  PostLinkColor(6),
  TextColorPrimary(7),
}