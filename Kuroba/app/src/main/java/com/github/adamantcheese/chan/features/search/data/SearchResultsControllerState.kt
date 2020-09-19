package com.github.adamantcheese.chan.features.search.data

import com.github.adamantcheese.chan.core.site.sites.search.PageCursor
import com.github.adamantcheese.common.MurmurHashUtils
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import okhttp3.HttpUrl

internal sealed class SearchResultsControllerState {
  object InitialLoading : SearchResultsControllerState()
  class NothingFound(val query: String) : SearchResultsControllerState()
  data class Data(val data: SearchResultsControllerStateData) : SearchResultsControllerState()
}

internal data class SearchResultsControllerStateData(
  val searchPostInfoList: List<SearchPostInfo> = emptyList(),
  val nextPageCursor: PageCursor = PageCursor.End,
  val errorInfo: ErrorInfo? = null,
  val currentQueryInfo: CurrentQueryInfo? = null
)

internal data class ErrorInfo(
  val errorText: String
)

internal data class CurrentQueryInfo(
  val query: String,
  val totalFoundEntries: Int?
)

internal class SearchPostInfo(
  val postDescriptor: PostDescriptor,
  val opInfo: CharSequenceMurMur?,
  val postInfo: CharSequenceMurMur,
  val thumbnail: ThumbnailInfo?,
  val postComment: CharSequenceMurMur
) {

  fun combinedHash(): String {
    return "${postDescriptor.hashCode()}_${opInfo?.hashString()}" +
      "_${postInfo.hashString()}_${thumbnail.hashCode()}_${postInfo.hashString()}"
  }

}

internal data class ThumbnailInfo(
  val thumbnailUrl: HttpUrl
)

internal class CharSequenceMurMur(
  val spannedText: CharSequence,
  private val hash: MurmurHashUtils.Murmur3Hash
) {

  fun hashString(): String = "${hash.val1}_${hash.val2}"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CharSequenceMurMur

    if (hash != other.hash) return false

    return true
  }

  override fun hashCode(): Int {
    return hash.hashCode()
  }

  companion object {
    private val EMPTY = create("")

    fun create(spannedText: CharSequence): CharSequenceMurMur {
      val hash = MurmurHashUtils.murmurhash3_x64_128(spannedText)
      return CharSequenceMurMur(spannedText, hash)
    }

    fun empty(): CharSequenceMurMur = EMPTY
  }

}