package com.github.k1rakishou.core_themes

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Ids must be unique!
 * Do not change the ids!
 * Do not remove old ids!
 * Only add new ids!
 * This thing is serialized in the DB, changing it may cause unexpected results!
 * */
@Parcelize
enum class ChanThemeColorId(val id: Int) : Parcelable {
  PostSubjectColor(0),
  PostNameColor(1),
  AccentColor(2),
  PostInlineQuoteColor(3),
  PostQuoteColor(4),
  BackColorSecondary(5),
  PostLinkColor(6),
  TextColorPrimary(7);

  companion object {
    fun byId(id: Int): ChanThemeColorId {
      return values()
        .firstOrNull { chanThemeColorId -> chanThemeColorId.id == id }
        ?: throw IllegalAccessException("Failed to find color by id: $id")
    }

    fun byName(name: String): ChanThemeColorId? {
      return values()
        .firstOrNull { chanThemeColorId -> chanThemeColorId.name.equals(other = name, ignoreCase = true) }
    }
  }
}