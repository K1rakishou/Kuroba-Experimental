package com.github.k1rakishou.model.data.descriptor

import android.os.Parcelable
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

interface DescriptorParcelable : Parcelable {
  val type: Int

  fun isThreadDescriptor(): Boolean
  fun isCatalogDescriptor(): Boolean
  fun isCompositeCatalogDescriptor(): Boolean

  fun toChanDescriptor(): ChanDescriptor

  companion object {
    const val THREAD = 0
    const val CATALOG = 1
    const val COMPOSITE_CATALOG = 2

    fun fromDescriptor(chanDescriptor: ChanDescriptor): DescriptorParcelable {
      when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> {
          return SingleDescriptorParcelable(
            type = THREAD,
            siteName = chanDescriptor.siteName(),
            boardCode = chanDescriptor.boardCode(),
            threadNo = chanDescriptor.threadNo
          )
        }
        is ChanDescriptor.CatalogDescriptor -> {
          return SingleDescriptorParcelable(
            type = CATALOG,
            siteName = chanDescriptor.siteName(),
            boardCode = chanDescriptor.boardCode(),
            threadNo = null
          )
        }
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          val catalogDescriptorParcelables = chanDescriptor.catalogDescriptors
            .map { catalogDescriptor ->
              return@map SingleDescriptorParcelable(
                type = CATALOG,
                siteName = catalogDescriptor.siteName(),
                boardCode = catalogDescriptor.boardCode(),
                threadNo = null
              )
            }


          return CompositeDescriptorParcelable(COMPOSITE_CATALOG, catalogDescriptorParcelables)
        }
      }
    }
  }
}

class DescriptorParcelableMoshiAdapter {

  @ToJson
  fun toJson(jsonWriter: JsonWriter, descriptorParcelable: DescriptorParcelable) {
    jsonWriter.beginObject()

    when (descriptorParcelable) {
      is SingleDescriptorParcelable -> {
        jsonWriter
          .name("type").value(descriptorParcelable.type)

        jsonWriter
          .storeSingleDescriptorParcelable(descriptorParcelable)
      }
      is CompositeDescriptorParcelable -> {
        jsonWriter
          .name("type").value(descriptorParcelable.type)

        jsonWriter.beginArray()

        descriptorParcelable.descriptorParcelables.forEach { childDescriptorParcelable ->
          jsonWriter.storeSingleDescriptorParcelable(childDescriptorParcelable)
        }

        jsonWriter.endArray()
      }
    }

    jsonWriter.endObject()
  }

  @FromJson
  fun fromJson(jsonReader: JsonReader): DescriptorParcelable? {
    var globalType: Int? = null

    jsonReader.beginObject()

    try {
      if (jsonReader.hasNext() && jsonReader.peek() == JsonReader.Token.NAME) {
        if (jsonReader.nextName() == "type") {
          globalType = jsonReader.nextInt()
        }
      }

      when (globalType) {
        DescriptorParcelable.CATALOG,
        DescriptorParcelable.THREAD -> {
          return jsonReader.readSingleDescriptorParcelable()
        }
        DescriptorParcelable.COMPOSITE_CATALOG -> {
          val childDescriptorParcelable = mutableListOf<SingleDescriptorParcelable>()

          jsonReader.beginArray()

          while (jsonReader.hasNext()) {
            val descriptorParcelable = jsonReader.readSingleDescriptorParcelable()
            if (descriptorParcelable == null) {
              return null
            }

            childDescriptorParcelable += descriptorParcelable
          }

          jsonReader.endArray()

          if (childDescriptorParcelable.isEmpty()) {
            return null
          }

          return CompositeDescriptorParcelable(globalType, childDescriptorParcelable)
        }
        else -> {
          // no-op
        }
      }
    } finally {
      jsonReader.endObject()
    }

    return null
  }

  private fun JsonReader.readSingleDescriptorParcelable(): SingleDescriptorParcelable? {
    if (nextName() != "single_descriptor_parcelable") {
      return null
    }

    var type: Int? = null
    var siteName: String? = null
    var boardCode: String? = null
    var threadNo: Long? = null

    beginObject()

    while (hasNext()) {
      when (nextName()) {
        "type" -> type = nextInt()
        "site_name" -> siteName = nextString()
        "board_code" -> boardCode = nextString()
        "thread_no" -> threadNo = nextLong()
        else -> skipValue()
      }
    }

    endObject()

    if (type == null || siteName == null || boardCode == null) {
      return null
    }

    return SingleDescriptorParcelable(type, siteName, boardCode, threadNo)
  }

  private fun JsonWriter.storeSingleDescriptorParcelable(descriptorParcelable: SingleDescriptorParcelable): JsonWriter {
    return this
      .name("single_descriptor_parcelable").beginObject()
      .name("type").value(descriptorParcelable.type)
      .name("site_name").value(descriptorParcelable.siteName)
      .name("board_code").value(descriptorParcelable.boardCode)
      .name("thread_no").value(descriptorParcelable.threadNo)
      .endObject()
  }

}

@Parcelize
data class SingleDescriptorParcelable(
  override val type: Int,
  val siteName: String,
  val boardCode: String,
  val threadNo: Long?
) : DescriptorParcelable {

  override fun isThreadDescriptor(): Boolean = type == DescriptorParcelable.THREAD
  override fun isCatalogDescriptor(): Boolean = type == DescriptorParcelable.CATALOG
  override fun isCompositeCatalogDescriptor(): Boolean = false

  override fun toChanDescriptor(): ChanDescriptor {
    return if (isThreadDescriptor()) {
      ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(this)
    } else {
      ChanDescriptor.CatalogDescriptor.fromDescriptorParcelable(this)
    }
  }

}

@Parcelize
data class CompositeDescriptorParcelable(
  override val type: Int,
  val descriptorParcelables: List<SingleDescriptorParcelable>
) : DescriptorParcelable {

  override fun isThreadDescriptor(): Boolean = false
  override fun isCatalogDescriptor(): Boolean = false
  override fun isCompositeCatalogDescriptor(): Boolean = true

  override fun toChanDescriptor(): ChanDescriptor {
    return ChanDescriptor.CompositeCatalogDescriptor.create(toCatalogDescriptors())
  }

  fun toCatalogDescriptors(): List<ChanDescriptor.CatalogDescriptor> {
    return descriptorParcelables
      .map { descriptorParcelable -> descriptorParcelable.toChanDescriptor() as ChanDescriptor.CatalogDescriptor }
  }

}

@Parcelize
data class PostDescriptorParcelable(
  val descriptorParcelable: DescriptorParcelable,
  val postNo: Long,
  val postSubNo: Long
) : Parcelable {
  @IgnoredOnParcel
  val postDescriptor by lazy {
    return@lazy when (val chanDescriptor = descriptorParcelable.toChanDescriptor()) {
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        error("Cannot use CompositeCatalogDescriptor here")
      }
      is ChanDescriptor.CatalogDescriptor -> {
        PostDescriptor.create(chanDescriptor, postNo, postNo, postSubNo)
      }
      is ChanDescriptor.ThreadDescriptor -> {
        PostDescriptor.create(chanDescriptor, chanDescriptor.threadNo, postNo, postSubNo)
      }
    }
  }

  companion object {
    fun fromPostDescriptor(postDescriptor: PostDescriptor): PostDescriptorParcelable {
      return PostDescriptorParcelable(
        DescriptorParcelable.fromDescriptor(postDescriptor.descriptor),
        postDescriptor.postNo,
        postDescriptor.postSubNo
      )
    }

    fun fromDescriptor(descriptor: ChanDescriptor, postNo: Long, postSubNo: Long): PostDescriptorParcelable {
      return PostDescriptorParcelable(
        DescriptorParcelable.fromDescriptor(descriptor),
        postNo,
        postSubNo
      )
    }
  }

}