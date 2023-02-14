/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.features.reply.data

import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelable
import java.util.*
import java.util.regex.Pattern

/**
 * The data needed to send a reply.
 */
class Reply(
  @JvmField
  val chanDescriptor: ChanDescriptor,
  private val basicReplyInfo: BasicReplyInfo = BasicReplyInfo()
) {
  private val captchaInfo: CaptchaInfo = CaptchaInfo()
  private val filesTakenForThisReply: MutableList<ReplyFile> = mutableListOf<ReplyFile>()

  private var _lastUpdatedAt = System.currentTimeMillis()
  val lastUpdatedAt: Long
    get() = _lastUpdatedAt

  private var dirty = false

  @get:Synchronized
  @set:Synchronized
  var postName: String = basicReplyInfo.name
    get() = basicReplyInfo.name
    set(value) {
      onReplyUpdated()

      basicReplyInfo.name = value
      field = value
    }

  @get:Synchronized
  @set:Synchronized
  var options: String = basicReplyInfo.options
    get() = basicReplyInfo.options
    set(value) {
      onReplyUpdated()

      basicReplyInfo.options = value
      field = value
    }

  @get:Synchronized
  @set:Synchronized
  var subject: String = basicReplyInfo.subject
    get() = basicReplyInfo.subject
    set(value) {
      onReplyUpdated()

      basicReplyInfo.subject = value
      field = value
    }

  @get:Synchronized
  @set:Synchronized
  var comment: String = basicReplyInfo.comment
    get() = basicReplyInfo.comment
    set(value) {
      onReplyUpdated()

      basicReplyInfo.comment = value
      field = value
    }

  @get:Synchronized
  @set:Synchronized
  var flag: String = basicReplyInfo.flag
    get() = basicReplyInfo.flag
    set(value) {
      onReplyUpdated()

      basicReplyInfo.flag = value
      field = value
    }

  @get:Synchronized
  @set:Synchronized
  var password: String = basicReplyInfo.password
    get() = basicReplyInfo.password
    set(value) {
      onReplyUpdated()

      basicReplyInfo.password = value
      field = value
    }

  @get:Synchronized
  val captchaChallenge: String?
    get() = captchaInfo.captchaChallenge

  @get:Synchronized
  val captchaSolution: CaptchaSolution?
    get() = captchaInfo.captchaSolution

  private fun onReplyUpdated() {
    _lastUpdatedAt = System.currentTimeMillis()
    dirty = true
  }

  @Synchronized
  fun toReplyDataJson(): ReplyDataJson? {
    if (basicReplyInfo.isEmpty() || !dirty) {
      return null
    }

    dirty = false

    return ReplyDataJson(
      chanDescriptor = DescriptorParcelable.fromDescriptor(chanDescriptor),
      name = basicReplyInfo.name,
      options = basicReplyInfo.options,
      flag = basicReplyInfo.flag,
      subject = basicReplyInfo.subject,
      comment = basicReplyInfo.comment,
    )
  }

  @Synchronized
  fun threadNo(): Long {
    if (chanDescriptor is ThreadDescriptor) {
      chanDescriptor.threadNo
    }

    return 0
  }

  @Synchronized
  fun firstFileOrNull(): ReplyFile? {
    return filesTakenForThisReply.firstOrNull()
  }

  @Synchronized
  fun iterateFilesOrThrowIfEmpty(iterator: (Int, ReplyFile) -> Unit) {
    check(filesTakenForThisReply.isNotEmpty()) { "filesTakenForThisReply is empty!" }

    filesTakenForThisReply.forEachIndexed { index, replyFile -> iterator(index, replyFile) }
  }

  @Synchronized
  fun putReplyFiles(files: List<ReplyFile>) {
    filesTakenForThisReply.addAll(files)
  }

  @Synchronized
  fun hasFiles(): Boolean = filesTakenForThisReply.isNotEmpty()

  @Synchronized
  fun filesCount(): Int = filesTakenForThisReply.size

  @Synchronized
  fun getAndConsumeFiles(): List<ReplyFile> {
    val files = filesTakenForThisReply.toList()
    filesTakenForThisReply.clear()

    return files
  }

  @Synchronized
  fun cleanupFiles(): ModularResult<List<UUID>> {
    return Try {
      val fileUuids = mutableListOf<UUID>()

      filesTakenForThisReply.forEach { replyFile ->
        fileUuids += replyFile.getReplyFileMeta().unwrap().fileUuid
        replyFile.deleteFromDisk()
      }

      filesTakenForThisReply.clear()
      return@Try fileUuids
    }
  }

  @Synchronized
  fun isCommentEmpty(): Boolean {
    return basicReplyInfo.comment.trim().isEmpty()
  }

  @Synchronized
  fun setCaptcha(challenge: String?, captchaSolution: CaptchaSolution?) {
    captchaInfo.captchaChallenge = challenge
    captchaInfo.captchaSolution = captchaSolution
  }

  @Synchronized
  fun resetCaptcha() {
    captchaInfo.captchaChallenge = null
    captchaInfo.captchaSolution = null
  }

  @Synchronized
  fun resetAfterPosting() {
    basicReplyInfo.resetAfterPosting()
  }

  @Synchronized
  fun handleQuote(selectStart: Int, postNo: Long, textQuote: String?): Int {
    val stringBuilder = StringBuilder()
    val comment = basicReplyInfo.comment
    val selectionStart = selectStart.coerceAtLeast(0)

    if (selectionStart - 1 >= 0
      && comment.isNotEmpty()
      && selectionStart - 1 < comment.length
      && comment[selectionStart - 1] != '\n'
    ) {
      stringBuilder
        .append('\n')
    }

    if (!comment.contains(">>${postNo}")) {
      stringBuilder
        .append(">>")
        .append(postNo)
        .append("\n")
    }

    if (textQuote.isNotNullNorEmpty()) {
      val lines = textQuote.split("\n").toTypedArray()
      for (line in lines) {
        // do not include post no from quoted post
        if (QUOTE_PATTERN_COMPLEX.matcher(line).matches()) {
          continue
        }

        if (!line.startsWith(">>") && !line.startsWith(">")) {
          stringBuilder
            .append(">")
        }

        stringBuilder
          .append(line)
          .append("\n")
      }
    }

    basicReplyInfo.comment = StringBuilder(basicReplyInfo.comment)
      .insert(selectionStart, stringBuilder)
      .toString()

    return stringBuilder.length
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Reply

    if (chanDescriptor != other.chanDescriptor) return false

    return true
  }

  override fun hashCode(): Int {
    return chanDescriptor.hashCode()
  }

  override fun toString(): String {
    return "Reply(chanDescriptor=$chanDescriptor, _lastUpdatedAt=$_lastUpdatedAt, " +
      "basicReplyInfo=$basicReplyInfo, captchaInfo=$captchaInfo)"
  }

  data class BasicReplyInfo(
    @JvmField
    var name: String = "",
    @JvmField
    var options: String = "",
    @JvmField
    var flag: String = "",
    @JvmField
    var subject: String = "",
    @JvmField
    var comment: String = "",
    @JvmField
    var password: String = "",
  ) {

    fun isEmpty(): Boolean {
      return name.isEmpty()
        && options.isEmpty()
        && flag.isEmpty()
        && subject.isEmpty()
        && comment.isEmpty()
        && password.isEmpty()
    }

    @Synchronized
    fun resetAfterPosting() {
      comment = ""
    }

  }

  data class CaptchaInfo(
    /**
     * Optional. `null` when ReCaptcha v2 was used or a 4pass
     */
    @JvmField
    var captchaChallenge: String? = null,

    /**
     * Optional. `null` when a 4pass was used.
     */
    @JvmField
    var captchaSolution: CaptchaSolution? = null
  )

  companion object {
    private val QUOTE_PATTERN_COMPLEX = Pattern.compile("^>>(>/[a-z0-9]+/)?\\d+.*$")
  }
}