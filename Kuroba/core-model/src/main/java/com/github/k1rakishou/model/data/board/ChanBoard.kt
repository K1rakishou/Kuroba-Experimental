package com.github.k1rakishou.model.data.board

import com.github.k1rakishou.model.data.descriptor.BoardDescriptor

class ChanBoard(
  val boardDescriptor: BoardDescriptor,
  var active: Boolean = false,
  var synthetic: Boolean = false,
  val order: Int? = null,
  val name: String? = null,
  val perPage: Int = 15,
  val pages: Int = 10,
  val maxFileSize: Int = -1,
  val maxWebmSize: Int = -1,
  val maxCommentChars: Int = -1,
  val bumpLimit: Int = -1,
  val imageLimit: Int = -1,
  val cooldownThreads: Int = 0,
  val cooldownReplies: Int = 0,
  val cooldownImages: Int = 0,
  val customSpoilers: Int = -1,
  val description: String = "",
  val workSafe: Boolean = false,
  val spoilers: Boolean = false,
  val userIds: Boolean = false,
  val codeTags: Boolean = false,
  @Deprecated("delete me")
  val preuploadCaptcha: Boolean = false,
  @Deprecated("delete me")
  val countryFlags: Boolean = false,
  val mathTags: Boolean = false,
  @Deprecated("delete me")
  val archive: Boolean = false,
  val isUnlimitedCatalog: Boolean = false
) {

  fun copy(
    boardDescriptor: BoardDescriptor? = null,
    active: Boolean? = null,
    synthetic: Boolean? = null,
    order: Int? = null,
    name: String? = null,
    perPage: Int? = null,
    pages: Int? = null,
    maxFileSize: Int? = null,
    maxWebmSize: Int? = null,
    maxCommentChars: Int? = null,
    bumpLimit: Int? = null,
    imageLimit: Int? = null,
    cooldownThreads: Int? = null,
    cooldownReplies: Int? = null,
    cooldownImages: Int? = null,
    customSpoilers: Int? = null,
    description: String? = null,
    workSafe: Boolean? = null,
    spoilers: Boolean? = null,
    userIds: Boolean? = null,
    codeTags: Boolean? = null,
    preuploadCaptcha: Boolean? = null,
    countryFlags: Boolean? = null,
    mathTags: Boolean? = null,
    archive: Boolean? = null,
    isUnlimitedCatalog: Boolean? = null,
    chanBoardMeta: ChanBoardMeta? = null
  ): ChanBoard {
    return ChanBoard(
      boardDescriptor ?: this.boardDescriptor,
      active ?: this.active,
      synthetic ?: this.synthetic,
      order ?: this.order,
      name ?: this.name,
      perPage ?: this.perPage,
      pages ?: this.pages,
      maxFileSize ?: this.maxFileSize,
      maxWebmSize ?: this.maxWebmSize,
      maxCommentChars ?: this.maxCommentChars,
      bumpLimit ?: this.bumpLimit,
      imageLimit ?: this.imageLimit,
      cooldownThreads ?: this.cooldownThreads,
      cooldownReplies ?: this.cooldownReplies,
      cooldownImages ?: this.cooldownImages,
      customSpoilers ?: this.customSpoilers,
      description ?: this.description,
      workSafe ?: this.workSafe,
      spoilers ?: this.spoilers,
      userIds ?: this.userIds,
      codeTags ?: this.codeTags,
      preuploadCaptcha ?: this.preuploadCaptcha,
      countryFlags ?: this.countryFlags,
      mathTags ?: this.mathTags,
      archive ?: this.archive,
      isUnlimitedCatalog ?: this.isUnlimitedCatalog,
    ).also { newChanBoard -> newChanBoard.updateChanBoardMeta<ChanBoardMeta> { chanBoardMeta ?: this.chanBoardMeta } }
  }

  // Do not persist on the disk!
  @get:Synchronized
  @set:Synchronized
  var chanBoardMeta: ChanBoardMeta? = null
    private set

  fun boardName(): String = name ?: ""
  fun siteName(): String =  boardDescriptor.siteName()
  fun boardCode(): String = boardDescriptor.boardCode
  fun formattedBoardCode(): String = "/${boardCode()}/"

  fun boardSupportsFlagSelection(): Boolean {
    val is4chan = boardDescriptor.siteDescriptor.is4chan()
    if (is4chan && boardCode() == "pol") {
      return true
    }

    if (is4chan && boardCode() == "mlp") {
      return true
    }

    return false
  }

  @Synchronized
  fun <T : ChanBoardMeta> updateChanBoardMeta(updater: (T?) -> T?) {
    chanBoardMeta = updater(chanBoardMeta as T?)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ChanBoard

    if (boardDescriptor != other.boardDescriptor) return false
    if (active != other.active) return false
    if (synthetic != other.synthetic) return false
    if (order != other.order) return false
    if (name != other.name) return false
    if (perPage != other.perPage) return false
    if (pages != other.pages) return false
    if (maxFileSize != other.maxFileSize) return false
    if (maxWebmSize != other.maxWebmSize) return false
    if (maxCommentChars != other.maxCommentChars) return false
    if (bumpLimit != other.bumpLimit) return false
    if (imageLimit != other.imageLimit) return false
    if (cooldownThreads != other.cooldownThreads) return false
    if (cooldownReplies != other.cooldownReplies) return false
    if (cooldownImages != other.cooldownImages) return false
    if (customSpoilers != other.customSpoilers) return false
    if (description != other.description) return false
    if (workSafe != other.workSafe) return false
    if (spoilers != other.spoilers) return false
    if (userIds != other.userIds) return false
    if (codeTags != other.codeTags) return false
    if (preuploadCaptcha != other.preuploadCaptcha) return false
    if (countryFlags != other.countryFlags) return false
    if (mathTags != other.mathTags) return false
    if (archive != other.archive) return false
    if (isUnlimitedCatalog != other.isUnlimitedCatalog) return false
    if (chanBoardMeta != other.chanBoardMeta) return false

    return true
  }

  override fun hashCode(): Int {
    var result = boardDescriptor.hashCode()
    result = 31 * result + active.hashCode()
    result = 31 * result + synthetic.hashCode()
    result = 31 * result + (order?.hashCode() ?: 0)
    result = 31 * result + (name?.hashCode() ?: 0)
    result = 31 * result + perPage
    result = 31 * result + pages
    result = 31 * result + maxFileSize
    result = 31 * result + maxWebmSize
    result = 31 * result + maxCommentChars
    result = 31 * result + bumpLimit
    result = 31 * result + imageLimit
    result = 31 * result + cooldownThreads
    result = 31 * result + cooldownReplies
    result = 31 * result + cooldownImages
    result = 31 * result + customSpoilers
    result = 31 * result + description.hashCode()
    result = 31 * result + workSafe.hashCode()
    result = 31 * result + spoilers.hashCode()
    result = 31 * result + userIds.hashCode()
    result = 31 * result + codeTags.hashCode()
    result = 31 * result + preuploadCaptcha.hashCode()
    result = 31 * result + countryFlags.hashCode()
    result = 31 * result + mathTags.hashCode()
    result = 31 * result + archive.hashCode()
    result = 31 * result + isUnlimitedCatalog.hashCode()
    result = 31 * result + (chanBoardMeta?.hashCode() ?: 0)

    return result
  }

  override fun toString(): String {
    return "ChanBoard(boardDescriptor=$boardDescriptor, active=$active, synthetic=$synthetic, order=$order, name=$name, " +
      "perPage=$perPage, pages=$pages, maxFileSize=$maxFileSize, maxWebmSize=$maxWebmSize, " +
      "maxCommentChars=$maxCommentChars, bumpLimit=$bumpLimit, imageLimit=$imageLimit, " +
      "cooldownThreads=$cooldownThreads, cooldownReplies=$cooldownReplies, cooldownImages=$cooldownImages, " +
      "customSpoilers=$customSpoilers, description='$description', workSafe=$workSafe, " +
      "spoilers=$spoilers, userIds=$userIds, codeTags=$codeTags, preuploadCaptcha=$preuploadCaptcha, " +
      "countryFlags=$countryFlags, mathTags=$mathTags, archive=$archive, isUnlimitedCatalog=$isUnlimitedCatalog, " +
      "chanBoardMeta=$chanBoardMeta)"
  }

  companion object {
    const val DEFAULT_CATALOG_SIZE = 150

    @JvmStatic
    fun create(boardDescriptor: BoardDescriptor, boardName: String?): ChanBoard {
      return ChanBoard(
        boardDescriptor = boardDescriptor,
        name = boardName
      )
    }
  }

}