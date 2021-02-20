package com.github.k1rakishou.model.converter

import android.net.Uri
import androidx.room.TypeConverter

class UriTypeConverter {

  @TypeConverter
  fun toUri(uriRaw: String?): Uri? {
    return uriRaw?.let { Uri.parse(it) }
  }

  @TypeConverter
  fun fromUri(uri: Uri?): String? {
    return uri?.toString()
  }

}