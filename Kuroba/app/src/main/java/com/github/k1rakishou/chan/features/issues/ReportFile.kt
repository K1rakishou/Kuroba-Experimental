package com.github.k1rakishou.chan.features.issues

import java.io.File

data class ReportFile(
  val file: File,
  val fileName: String,
  var markedToSend: Boolean
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ReportFile

    if (fileName != other.fileName) return false
    if (markedToSend != other.markedToSend) return false

    return true
  }

  override fun hashCode(): Int {
    var result = fileName.hashCode()
    result = 31 * result + markedToSend.hashCode()
    return result
  }
}