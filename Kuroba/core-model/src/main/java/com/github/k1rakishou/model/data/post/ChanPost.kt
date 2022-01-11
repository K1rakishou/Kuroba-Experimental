package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.common.copy
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

open class ChanPost(
  val chanPostId: Long,
  val postDescriptor: PostDescriptor,
  private val _postImages: MutableList<ChanPostImage>,
  val postIcons: List<ChanPostHttpIcon>,
  val repliesTo: Set<PostDescriptor>,
  val timestamp: Long = -1L,
  val postComment: PostComment,
  val subject: CharSequence?,
  val tripcode: CharSequence?,
  val name: String? = null,
  val posterId: String? = null,
  val moderatorCapcode: String? = null,
  val isSavedReply: Boolean,
  val isSage: Boolean,
  repliesFrom: Set<PostDescriptor>? = null,
  deleted: Boolean,
  posterIdColor: Int
) {
  /**
   * We use this array to avoid infinite loops when binding posts since after all post content
   * loaders have done their jobs we update the post via notifyItemChange, which triggers
   * onPostBind() again.
   */
  private val onDemandContentLoadedArray = Array<Boolean>(LoaderType.COUNT) { false }

  @get:Synchronized
  @set:Synchronized
  var isDeleted: Boolean = deleted

  @get:Synchronized
  @set:Synchronized
  var posterIdColor: Int = 0

  @get:Synchronized
  val repliesFrom = mutableSetOf<PostDescriptor>()

  @get:Synchronized
  val repliesFromCopy: Set<PostDescriptor>
    get() = repliesFrom.toSet()

  fun postNo(): Long = postDescriptor.postNo
  fun postSubNo(): Long = postDescriptor.postSubNo
  @Synchronized
  fun firstImage(): ChanPostImage? = _postImages.firstOrNull()

  fun isOP(): Boolean = postDescriptor.isOP()

  @get:Synchronized
  val repliesFromCount: Int
    get() = repliesFrom.size

  @get:Synchronized
  val postImages: List<ChanPostImage>
    get() = _postImages
  @get:Synchronized
  val postImagesCount: Int
    get() = _postImages.size

  open val catalogRepliesCount: Int
    get() = 0
  open val catalogImagesCount: Int
    get() = 0
  open val uniqueIps: Int
    get() = 0

  val boardDescriptor: BoardDescriptor
    get() = postDescriptor.boardDescriptor()

  init {
    this.posterIdColor = posterIdColor

    for (loaderType in LoaderType.values()) {
      onDemandContentLoadedArray[loaderType.arrayIndex] = false
    }

    repliesFrom?.let { replies -> this.repliesFrom.addAll(replies) }
  }

  open fun deepCopy(overrideDeleted: Boolean? = null): ChanPost {
    return ChanPost(
      chanPostId = chanPostId,
      postDescriptor = postDescriptor,
      _postImages = _postImages,
      postIcons = postIcons,
      repliesTo = repliesTo,
      timestamp = timestamp,
      postComment = postComment.copy(),
      subject = subject.copy(),
      tripcode = tripcode.copy(),
      name = name,
      posterId = posterId,
      posterIdColor = posterIdColor,
      moderatorCapcode = moderatorCapcode,
      isSavedReply = isSavedReply,
      repliesFrom = repliesFrom,
      isSage = isSage,
      deleted = overrideDeleted ?: isDeleted
    ).also { newPost ->
      newPost.replaceOnDemandContentLoadedArray(this.copyOnDemandContentLoadedArray())
    }
  }

  @Synchronized
  open fun isContentLoadedForLoader(loaderType: LoaderType): Boolean {
    return onDemandContentLoadedArray[loaderType.arrayIndex]
  }

  @Synchronized
  open fun setContentLoadedForLoader(
    loaderType: LoaderType,
    loaded: Boolean = true
  ) {
    onDemandContentLoadedArray[loaderType.arrayIndex] = loaded
  }

  @Synchronized
  open fun allLoadersCompletedLoading(): Boolean {
    return onDemandContentLoadedArray
      .all { loaderContentLoadState -> loaderContentLoadState }
  }

  @Synchronized
  fun copyOnDemandContentLoadedArray(): Array<Boolean> {
    val newArray = Array<Boolean>(LoaderType.COUNT) { false }

    onDemandContentLoadedArray.forEachIndexed { index, loaderContentLoadState ->
      newArray[index] = loaderContentLoadState
    }

    return newArray
  }

  @Synchronized
  fun replaceOnDemandContentLoadedArray(newArray: Array<Boolean>) {
    for (index in onDemandContentLoadedArray.indices) {
      onDemandContentLoadedArray[index] = newArray[index]
    }
  }

  @Synchronized
  fun onDemandContentLoadedMapsDiffer(
    thisArray: Array<Boolean>,
    otherArray: Array<Boolean>
  ): Boolean {
    if (thisArray.size != otherArray.size) {
      return true
    }

    for (index in thisArray.indices) {
      val thisLoaderState = thisArray[index]
      val otherLoaderState = otherArray[index]

      if (thisLoaderState != otherLoaderState) {
        return true
      }
    }

    return false
  }

  @Synchronized
  fun firstPostImageOrNull(predicate: (ChanPostImage) -> Boolean): ChanPostImage? {
    for (postImage in _postImages) {
      if (predicate.invoke(postImage)) {
        return postImage
      }
    }

    return null
  }

  @Synchronized
  fun iteratePostImages(iterator: (ChanPostImage) -> Unit) {
    for (postImage in _postImages) {
      iterator.invoke(postImage)
    }
  }

  @Synchronized
  internal fun addImage(chanPostImage: ChanPostImage): Boolean {
    val alreadyAdded = _postImages
      .any { postImage -> postImage.serverFilename == chanPostImage.serverFilename }

    if (alreadyAdded) {
      return false
    }

    _postImages += chanPostImage
    return true
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ChanPost) return false

    if (postDescriptor != other.postDescriptor) {
      return false
    }
    if (!arePostImagesTheSame(other)) {
      return false
    }
    if (!arePostIconsTheSame(other)) {
      return false
    }
    if (postComment != other.postComment) {
      return false
    }
    if (subject != other.subject) {
      return false
    }
    if (name != other.name) {
      return false
    }
    if (tripcode != other.tripcode) {
      return false
    }
    if (posterId != other.posterId) {
      return false
    }
    if (posterIdColor != other.posterIdColor) {
      return false
    }
    if (moderatorCapcode != other.moderatorCapcode) {
      return false
    }
    if (isSavedReply != other.isSavedReply) {
      return false
    }

    if (!onDemandContentLoadedMapsDiffer(onDemandContentLoadedArray, other.onDemandContentLoadedArray)) {
      return false
    }

    return true
  }

  private fun arePostIconsTheSame(other: ChanPost): Boolean {
    if (postIcons.size != other.postIcons.size) {
      return false
    }

    return postIcons.indices.none { postIcons[it] != other.postIcons[it] }
  }

  private fun arePostImagesTheSame(other: ChanPost): Boolean {
    if (_postImages.size != other._postImages.size) {
      return false
    }

    return _postImages.indices.none { _postImages[it] != other._postImages[it] }
  }

  override fun hashCode(): Int {
    var result = chanPostId.hashCode()
    result = 31 * result + postDescriptor.hashCode()
    result = 31 * result + repliesTo.hashCode()
    result = 31 * result + _postImages.hashCode()
    result = 31 * result + postComment.hashCode()
    result = 31 * result + subject.hashCode()
    result = 31 * result + (name?.hashCode() ?: 0)
    result = 31 * result + tripcode.hashCode()
    result = 31 * result + (posterId?.hashCode() ?: 0)
    result = 31 * result + posterIdColor.hashCode()
    result = 31 * result + (moderatorCapcode?.hashCode() ?: 0)
    result = 31 * result + isSavedReply.hashCode()
    result = 31 * result + isSage.hashCode()
    return result
  }

  override fun toString(): String {
    return "ChanPost{" +
      "chanPostId=" + chanPostId +
      ", postDescriptor=" + postDescriptor +
      ", postImages=" + _postImages.size +
      ", subject='" + subject + '\'' +
      ", postComment=" + postComment.originalComment().take(64) +
      '}'
  }

}