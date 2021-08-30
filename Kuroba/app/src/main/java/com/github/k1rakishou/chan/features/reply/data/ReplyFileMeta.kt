package com.github.k1rakishou.chan.features.reply.data

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.google.gson.annotations.Since
import java.util.*

data class ReplyFileMeta(
  @Since(1.0)
  @SerializedName("meta_version")
  @Expose(serialize = true, deserialize = true)
  val metaVersion: Int = CURRENT_REPLY_FILE_META_VERSION,

  @Since(1.0)
  @SerializedName("file_uuid")
  @Expose(serialize = true, deserialize = true)
  val fileUuidStringNullable: String?,

  @Since(1.0)
  @SerializedName("file_name")
  @Expose(serialize = true, deserialize = true)
  var fileNameNullable: String?,

  @Since(1.0)
  @SerializedName("original_file_name")
  @Expose(serialize = true, deserialize = true)
  val originalFileNameNullable: String?,

  @Since(1.0)
  @SerializedName("spoiler")
  @Expose(serialize = true, deserialize = true)
  var spoiler: Boolean = false,

  @Since(1.0)
  @SerializedName("selected")
  @Expose(serialize = true, deserialize = true)
  var selected: Boolean = false,

  @Since(1.0)
  @SerializedName("added_on")
  @Expose(serialize = true, deserialize = true)
  var addedOnNullable: Long?,

  @Since(1.0)
  @SerializedName("file_taken_by")
  @Expose(serialize = true, deserialize = true)
  var fileTakenBy: ReplyChanDescriptor? = null
) {
  private var _fileUuid: UUID? = null

  val fileUuidString: String
    get() = fileUuidStringNullable!!
  val fileName: String
    get() = fileNameNullable!!
  val originalFileName: String
    get() = originalFileNameNullable!!
  val addedOn: Long
    get() = addedOnNullable!!

  val fileUuid: UUID
    get() {
      if (_fileUuid == null) {
        synchronized(this) {
          if (_fileUuid == null) {
            _fileUuid = UUID.fromString(fileUuidString)
          }
        }
      }

      return _fileUuid!!
    }

  fun isValidMeta(): Boolean {
    return metaVersion == CURRENT_REPLY_FILE_META_VERSION
      && fileUuidStringNullable != null
      && fileNameNullable != null
      && originalFileNameNullable != null
      && addedOnNullable != null
      && fileTakenBy?.isValid() ?: true
  }

  fun isTaken(): Boolean = fileTakenBy != null

  companion object {
    const val CURRENT_REPLY_FILE_META_VERSION = 1
  }
}

data class ReplyChanDescriptor(
  @Since(1.0)
  @SerializedName("is_thread_descriptor")
  @Expose(serialize = true, deserialize = true)
  val isThreadDescriptorNullable: Boolean?,

  @Since(1.0)
  @SerializedName("site_name")
  @Expose(serialize = true, deserialize = true)
  val siteNameNullable: String?,

  @Since(1.0)
  @SerializedName("board_code")
  @Expose(serialize = true, deserialize = true)
  val boardCodeNullable: String?,

  @Since(1.0)
  @SerializedName("thread_no")
  @Expose(serialize = true, deserialize = true)
  val threadNo: Long?
) {

  val isThreadDescriptor: Boolean
    get() = isThreadDescriptorNullable!!
  val siteName: String
    get() = siteNameNullable!!
  val boardCode: String
    get() = boardCodeNullable!!

  fun isValid(): Boolean {
    return isThreadDescriptorNullable != null
      && siteNameNullable != null
      && boardCodeNullable != null
  }

  fun chanDescriptor(): ChanDescriptor {
    if (isThreadDescriptor) {
      return ChanDescriptor.ThreadDescriptor.create(siteName, boardCode, threadNo!!)
    } else {
      return ChanDescriptor.CatalogDescriptor.create(siteName, boardCode)
    }
  }

  companion object {
    fun fromChanDescriptor(chanDescriptor: ChanDescriptor): ReplyChanDescriptor {
      return when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> {
          ReplyChanDescriptor(
            isThreadDescriptorNullable = true,
            siteNameNullable = chanDescriptor.siteName(),
            boardCodeNullable = chanDescriptor.boardCode(),
            threadNo = chanDescriptor.threadNo
          )
        }
        is ChanDescriptor.CatalogDescriptor -> {
          ReplyChanDescriptor(
            isThreadDescriptorNullable = false,
            siteNameNullable = chanDescriptor.siteName(),
            boardCodeNullable = chanDescriptor.boardCode(),
            threadNo = null
          )
        }
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          error("Cannot use CompositeCatalogDescriptor here")
        }
      }
    }
  }

}