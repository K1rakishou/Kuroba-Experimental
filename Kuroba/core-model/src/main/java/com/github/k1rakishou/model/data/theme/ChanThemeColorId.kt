package com.github.k1rakishou.model.data.theme

/**
 * Ids must be unique!
 * Do not change the ids!
 * Do not remove old ids!
 * Only add new ids!
 * This thing will be serialized in the DB, changing it may cause unexpected results!
 * */
enum class ChanThemeColorId(val id: Int) {
  PostSubjectColor(0),
  PostNameColor(1),
  AccentColor(2),
  PostInlineQuoteColor(3),
  PostQuoteColor(4),
  BackColorSecondary(5),
  PostLinkColor(6)
}