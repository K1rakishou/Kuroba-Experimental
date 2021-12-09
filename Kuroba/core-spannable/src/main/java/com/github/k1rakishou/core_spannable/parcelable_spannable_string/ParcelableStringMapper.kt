package com.github.k1rakishou.core_spannable.parcelable_spannable_string

import com.github.k1rakishou.core_spannable.ParcelableSpannableString

interface ParcelableStringMapper {
  val version: Int

  fun toParcelableSpannableString(
    charSequence: CharSequence?
  ): ParcelableSpannableString?

  fun fromParcelableSpannableString(
    parcelableSpannableString: ParcelableSpannableString?
  ): CharSequence

}