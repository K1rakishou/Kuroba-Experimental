package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.mapper.ChanPostMapper
import com.github.k1rakishou.model.util.ChanPostUtils

class ChanPostBuilder {
  var boardDescriptor: BoardDescriptor? = null
    private set
  var id: Long = -1
  var subId: Long = 0
  var opId: Long = -1
  var op: Boolean = false
  var totalRepliesCount: Int = -1
  var threadImagesCount: Int = -1
  var uniqueIps: Int = -1
  var sticky: Boolean = false
  var closed: Boolean = false
  var archived: Boolean = false
  var deleted: Boolean = false
  var endless: Boolean = false
  var sage: Boolean = false
  var lastModified: Long = 0
    private set
  var name: String? = null
  var postCommentBuilder = PostCommentBuilder.create()
  var unixTimestampSeconds: Long = -1L
  var postImages = ArrayList<ChanPostImage>()
  var httpIcons = ArrayList<ChanPostHttpIcon>()
  var posterId: String? = null
  var moderatorCapcode: String? = null
  var idColor: Int = 0
  var isSavedReply: Boolean = false
  var repliesToIds = HashSet<PostDescriptor>()
  var tripcode: CharSequence? = null
  var subject: CharSequence? = null
  private var postDescriptor: PostDescriptor? = null

  private val postHash = lazy { ChanPostUtils.getPostHash(this) }

  constructor()

  constructor(other: ChanPostBuilder) {
    this.boardDescriptor = other.boardDescriptor
    this.id = other.id
    this.subId = other.subId
    this.opId = other.opId
    this.op = other.op
    this.totalRepliesCount = other.totalRepliesCount
    this.threadImagesCount = other.threadImagesCount
    this.uniqueIps = other.uniqueIps
    this.sticky = other.sticky
    this.closed = other.closed
    this.archived = other.archived
    this.deleted = other.deleted
    this.lastModified = other.lastModified
    this.name = other.name
    this.postCommentBuilder = other.postCommentBuilder.copy()
    this.unixTimestampSeconds = other.unixTimestampSeconds
    this.posterId = other.posterId
    this.moderatorCapcode = other.moderatorCapcode
    this.idColor = other.idColor
    this.isSavedReply = other.isSavedReply
    this.tripcode = other.tripcode
    this.subject = other.subject
    this.postDescriptor = other.postDescriptor

    this.postImages.addAll(other.postImages)
    this.httpIcons.addAll(other.httpIcons)
    this.repliesToIds.addAll(other.repliesToIds)
  }

  @get:Synchronized
  val getPostHash: MurmurHashUtils.Murmur3Hash
    /**
     * This hash is calculated on a raw post comment/subject/name/tripcode etc, before we add or
     * remove any spans or other info into the comment or other stuff. Basically those values are
     * the same as we receive them from the server at the moment of the hash calculation.
     */
    get() {
      val commentUpdateCounter = postCommentBuilder.commentUpdateCounter
      check(commentUpdateCounter <= 1) { "Bad commentUpdateCounter: $commentUpdateCounter" }

      return postHash.value
    }

  fun requireBoardDescriptor(): BoardDescriptor {
    return requireNotNull(boardDescriptor) { "boardDescriptor is null" }
  }

  @Synchronized
  fun hasPostDescriptor(): Boolean {
    if (boardDescriptor == null) {
      return false
    }

    if (opId() < 0L) {
      return false
    }

    if (id < 0L) {
      return false
    }

    return true
  }

  @Synchronized
  fun postDescriptor(): PostDescriptor {
    if (postDescriptor != null) {
      return postDescriptor!!
    }

    val bd = checkNotNull(boardDescriptor) { "boardDescriptor is null" }

    val opId = opId()
    require(opId >= 0L) { "Bad opId: $opId" }
    require(id >= 0L) { "Bad post id: $id" }
    require(subId >= 0L) { "Bad post subId: $subId" }

    val pd = PostDescriptor.create(
      siteName = bd.siteName(),
      boardCode = bd.boardCode,
      threadNo = opId,
      postNo = id,
      postSubNo = subId
    )

    postDescriptor = pd
    return pd
  }

  fun boardDescriptor(boardDescriptor: BoardDescriptor): ChanPostBuilder {
    this.boardDescriptor = boardDescriptor
    return this
  }

  fun id(id: Long): ChanPostBuilder {
    this.id = id
    return this
  }

  fun subId(subId: Long): ChanPostBuilder {
    this.subId = subId
    return this
  }

  fun opId(opId: Long): ChanPostBuilder {
    this.opId = opId
    return this
  }

  fun op(op: Boolean): ChanPostBuilder {
    this.op = op
    return this
  }

  fun replies(replies: Int): ChanPostBuilder {
    this.totalRepliesCount = replies
    return this
  }

  fun threadImagesCount(imagesCount: Int): ChanPostBuilder {
    this.threadImagesCount = imagesCount
    return this
  }

  fun uniqueIps(uniqueIps: Int): ChanPostBuilder {
    this.uniqueIps = uniqueIps
    return this
  }

  fun sticky(sticky: Boolean): ChanPostBuilder {
    this.sticky = sticky
    return this
  }

  fun archived(archived: Boolean): ChanPostBuilder {
    this.archived = archived
    return this
  }

  fun deleted(deleted: Boolean): ChanPostBuilder {
    this.deleted = deleted
    return this
  }

  fun lastModified(lastModified: Long): ChanPostBuilder {
    if (lastModified < -1 && this.lastModified >= 0) {
      return this
    }

    if (lastModified < -1) {
      this.lastModified = 0
      return this
    }

    this.lastModified = lastModified
    return this
  }

  fun closed(closed: Boolean): ChanPostBuilder {
    this.closed = closed
    return this
  }

  fun endless(endless: Boolean): ChanPostBuilder {
    this.endless = endless
    return this
  }

  fun sage(sage: Boolean): ChanPostBuilder {
    this.sage = sage
    return this
  }

  fun subject(subject: CharSequence?): ChanPostBuilder {
    this.subject = subject
    return this
  }

  fun name(name: String?): ChanPostBuilder {
    this.name = name ?: ""
    return this
  }

  fun comment(comment: String?): ChanPostBuilder {
    if (comment == null) {
      this.postCommentBuilder.setUnparsedComment("")
    } else {
      this.postCommentBuilder.setUnparsedComment(comment)
    }

    return this
  }

  fun tripcode(tripcode: CharSequence?): ChanPostBuilder {
    this.tripcode = tripcode
    return this
  }

  fun setUnixTimestampSeconds(unixTimestampSeconds: Long): ChanPostBuilder {
    this.unixTimestampSeconds = unixTimestampSeconds
    return this
  }

  fun postImages(images: List<ChanPostImage>, ownerPostDescriptor: PostDescriptor): ChanPostBuilder {
    synchronized(this) {
      this.postImages.addAll(images)
      for (postImage in this.postImages) {
        postImage.setPostDescriptor(ownerPostDescriptor)
      }
    }

    return this
  }

  fun posterId(posterId: String?): ChanPostBuilder {
    if (posterId == null) {
      return this
    }

    this.posterId = posterId

    // Only set the color if it's 0 to avoid overwriting it
    if (idColor == 0) {
      // Stolen from the 4chan extension
      val hash = this.posterId.hashCode()

      val r = (hash shr 24) and 0xff
      val g = (hash shr 16) and 0xff
      val b = (hash shr 8) and 0xff

      this.idColor = (0xff shl 24) + (r shl 16) + (g shl 8) + b
    }

    return this
  }

  fun posterIdColor(color: Int): ChanPostBuilder {
    this.idColor = color
    return this
  }

  fun moderatorCapcode(moderatorCapcode: String?): ChanPostBuilder {
    this.moderatorCapcode = moderatorCapcode ?: ""
    return this
  }

  fun addHttpIcon(httpIcon: ChanPostHttpIcon): ChanPostBuilder {
    httpIcons.add(httpIcon)
    return this
  }

  fun httpIcons(httpIcons: List<ChanPostHttpIcon>): ChanPostBuilder {
    this.httpIcons.clear()
    this.httpIcons.addAll(httpIcons)
    return this
  }

  fun opId(): Long {
    if (!op) {
      return opId
    }

    return id
  }

  fun isSavedReply(isSavedReply: Boolean): ChanPostBuilder {
    this.isSavedReply = isSavedReply
    return this
  }

  fun addLinkable(linkable: PostLinkable): ChanPostBuilder {
    synchronized(this) {
      this.postCommentBuilder.addPostLinkable(linkable)
      return this
    }
  }

  fun postLinkables(postLinkables: List<PostLinkable>): ChanPostBuilder {
    synchronized(this) {
      postCommentBuilder.setPostLinkables(postLinkables)
    }

    return this
  }

  fun addReplyTo(postId: Long, postSubNo: Long): ChanPostBuilder {
    val bd = boardDescriptor
    if (bd == null) {
      throw NullPointerException("boardDescriptor is not initialized yet")
    }

    val postDescriptor = PostDescriptor.create(
      siteName = bd.siteName(),
      boardCode = bd.boardCode,
      threadNo = opId(),
      postNo = postId,
      postSubNo = postSubNo
    )

    repliesToIds.add(postDescriptor)
    return this
  }

  fun repliesToIds(replyIds: MutableSet<PostDescriptor>): ChanPostBuilder {
    repliesToIds.clear()
    repliesToIds.addAll(replyIds)
    return this
  }

  fun build(): ChanPost {
    if (boardDescriptor == null
      || id < 0
      || subId < 0
      || opId < 0
      || unixTimestampSeconds < 0
      || !postCommentBuilder.hasUnparsedComment()
      || !postCommentBuilder.commentAlreadyParsed()
    ) {
      error("Post data not complete. " +
        "boardDescriptor: ${boardDescriptor}, id: ${id}, subId: ${subId}, opId: ${opId}, " +
        "unixTimestampSeconds: ${unixTimestampSeconds}, " +
        "hasUnparsedComment: ${postCommentBuilder.hasUnparsedComment()}, " +
        "commentAlreadyParsed: ${postCommentBuilder.commentAlreadyParsed()}")
    }

    return ChanPostMapper.fromPostBuilder(this)
  }

  override fun toString(): String {
    return "Builder{" +
      "id=" + id +
      ", subId=" + subId +
      ", opId=" + opId +
      ", op=" + op +
      ", postDescriptor=" + postDescriptor +
      ", unixTimestampSeconds=" + unixTimestampSeconds +
      ", subject='" + subject + '\'' +
      ", postCommentBuilder=" + postCommentBuilder +
      '}'
  }
}
