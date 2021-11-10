package com.github.k1rakishou.chan.features.reply.data

import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReplyDataJson(
  @Json(name = "chan_descriptor_parcelable")
  val chanDescriptor: DescriptorParcelable? = null,
  @Json(name = "name")
  val name: String? = null,
  @Json(name = "options")
  val options: String? = null,
  @Json(name = "flag")
  val flag: String? = null,
  @Json(name = "subject")
  val subject: String? = null,
  @Json(name = "comment")
  val comment: String? = null
) {

  fun toReplyOrNull(): Reply? {
    val chanDescriptor = chanDescriptor?.toChanDescriptor()
      ?: return null

    val name = name ?: ""
    val options = options ?: ""
    val flag = flag ?: ""
    val subject = subject ?: ""
    val comment = comment ?: ""

    val basicReplyInfo = Reply.BasicReplyInfo(
      name = name,
      options = options,
      flag = flag,
      subject = subject,
      comment = comment
    )

    return Reply(chanDescriptor = chanDescriptor, basicReplyInfo = basicReplyInfo)
  }

  fun isEmpty(): Boolean {
    return chanDescriptor == null
      && name.isNullOrEmpty()
      && options.isNullOrEmpty()
      && flag.isNullOrEmpty()
      && subject.isNullOrEmpty()
      && comment.isNullOrEmpty()
  }
}