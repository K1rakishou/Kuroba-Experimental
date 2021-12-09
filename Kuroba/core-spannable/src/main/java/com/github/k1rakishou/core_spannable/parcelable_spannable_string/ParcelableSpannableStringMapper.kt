package com.github.k1rakishou.core_spannable.parcelable_spannable_string

import android.util.Log
import com.github.k1rakishou.core_spannable.ParcelableSpannableString
import com.github.k1rakishou.core_spannable.parcelable_spannable_string.v1.ParcelableSpannableStringMapperV1
import java.util.concurrent.ConcurrentHashMap

object ParcelableSpannableStringMapper {
  private const val TAG = "ParcelableStringMapper"
  const val CURRENT_MAPPER_VERSION = 1

  private val mappers = ConcurrentHashMap<Int, ParcelableStringMapper>()

  init {
    mappers[ParcelableSpannableStringMapperV1.version] = ParcelableSpannableStringMapperV1
    // Add new mappers here if ParcelableSpannableString internal structure ever changes
  }

  @JvmStatic
  fun toParcelableSpannableString(
    version: Int,
    charSequence: CharSequence?
  ): ParcelableSpannableString? {
    try {
      return mappers[version]?.toParcelableSpannableString(charSequence)
    } catch (error: Throwable) {
      Log.e(TAG, "KurobaEx toParcelableSpannableString() error", error)
      return null
    }
  }

  @JvmStatic
  fun toParcelableSpannableString(
    charSequence: CharSequence?
  ): ParcelableSpannableString? {
    try {
      return mappers[CURRENT_MAPPER_VERSION]?.toParcelableSpannableString(charSequence)
    } catch (error: Throwable) {
      Log.e(TAG, "KurobaEx toParcelableSpannableString() error", error)
      return null
    }
  }

  @JvmStatic
  fun fromParcelableSpannableString(
    parcelableSpannableString: ParcelableSpannableString?
  ): CharSequence {
    if (parcelableSpannableString == null) {
      return ""
    }

    if (!parcelableSpannableString.isValid()) {
      Log.e(TAG, "KurobaEx fromParcelableSpannableString() invalid, version=${parcelableSpannableString.version()}")
      return parcelableSpannableString.text
    }

    if (parcelableSpannableString.hasNoSpans()) {
      return parcelableSpannableString.text
    }

    val version = parcelableSpannableString.parcelableSpans.version
    val mapper = mappers[parcelableSpannableString.parcelableSpans.version]

    if (mapper == null) {
      Log.e(TAG, "KurobaEx fromParcelableSpannableString() failed to find mapper for version=${version}")
      return parcelableSpannableString.text
    }

    try {
      return mapper.fromParcelableSpannableString(parcelableSpannableString)
    } catch (error: Throwable) {
      Log.e(TAG, "KurobaEx fromParcelableSpannableString() error", error)
      return parcelableSpannableString.text
    }
  }


}