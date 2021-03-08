package com.github.k1rakishou.chan.core.site.sites.search

import android.text.SpannableStringBuilder
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import okhttp3.HttpUrl
import org.joda.time.DateTime

class SearchEntryPostBuilder(
  val verboseLogs: Boolean
) {
  var siteName: String? = null
  var boardCode: String? = null
  var threadNo: Long? = null
  var postNo: Long? = null

  var isOp: Boolean? = null
  var name: String? = null
  var tripcode: String? = null
  var subject: String? = null
  var dateTime: DateTime? = null
  var commentRaw: String? = null

  val postImageUrlRawList = mutableListOf<HttpUrl>()

  val postDescriptor: PostDescriptor?
    get() {
      if (siteName == null || boardCode == null || threadNo == null || postNo == null) {
        return null
      }

      return PostDescriptor.Companion.create(siteName!!, boardCode!!, threadNo!!, postNo!!)
    }

  fun threadDescriptor(): ChanDescriptor.ThreadDescriptor {
    checkNotNull(isOp) { "isOp is null!" }
    checkNotNull(postDescriptor) { "postDescriptor is null!" }
    check(isOp!!) { "Must be OP!" }

    return postDescriptor!!.threadDescriptor()
  }

  fun hasMissingInfo(): Boolean {
    if (isOp == null || postDescriptor == null || dateTime == null) {
      if (verboseLogs) {
        Logger.e("SearchEntryPostBuilder", "hasMissingInfo() isOP: $isOp, siteName=$siteName, " +
          "boardCode=$boardCode, threadNo=$threadNo, postNo=$postNo, dateTime=$dateTime")
      }

      return true
    }

    return false
  }

  fun toSearchEntryPost(): SearchEntryPost {
    if (hasMissingInfo()) {
      throw IllegalStateException("Some info is missing! isOp=$isOp, postDescriptor=$postDescriptor, " +
        "dateTime=$dateTime, commentRaw=$commentRaw")
    }

    return SearchEntryPost(
      isOp!!,
      buildFullName(name, tripcode),
      subject?.let { SpannableStringBuilder(it) },
      postDescriptor!!,
      dateTime!!,
      postImageUrlRawList,
      commentRaw?.let { SpannableStringBuilder(it) }
    )
  }

  private fun buildFullName(name: String?, tripcode: String?): SpannableStringBuilder? {
    if (name.isNullOrEmpty() && tripcode.isNullOrEmpty()) {
      return null
    }

    val ssb = SpannableStringBuilder()

    if (name != null) {
      ssb.append(name)
    }

    if (tripcode != null) {
      if (ssb.isNotEmpty()) {
        ssb.append(" ")
      }

      ssb.append("'")
        .append(tripcode)
        .append("'")
    }

    return ssb
  }

  override fun toString(): String {
    return "SearchEntryPostBuilder(isOp=$isOp, postDescriptor=$postDescriptor, dateTime=${dateTime?.millis}, " +
      "postImageUrlRawList=$postImageUrlRawList, commentRaw=$commentRaw)"
  }

}