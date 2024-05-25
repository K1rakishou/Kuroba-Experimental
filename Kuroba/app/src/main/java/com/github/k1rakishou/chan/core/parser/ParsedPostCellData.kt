package com.github.k1rakishou.chan.core.parser

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.chan.ui.compose.data.PostDescriptorUi
import kotlinx.collections.immutable.ImmutableList

// TODO: compose post cells. Is this used anywhere?
@Immutable
data class ParsedPostCellData(
  val originalPostOrder: Int,
  val postDescriptorUi: PostDescriptorUi,
  val postSubjectUnparsed: String,
  val postCommentUnparsed: String,
  val opMark: Boolean,
  val sage: Boolean,
  val name: String?,
  val tripcode: String?,
  val posterId: String?,
  val countryFlag: PostIcon?,
  val boardFlag: PostIcon?,
  val timeMs: Long?,
  val images: ImmutableList<ParsedPostImageData>?,
  val threadRepliesTotal: Int?,
  val threadImagesTotal: Int?,
  val threadPostersTotal: Int?,
  val lastModified: Long?,
  val archived: Boolean,
  val deleted: Boolean,
  val closed: Boolean,
  val sticky: PostDataSticky?,
  val bumpLimit: Boolean?,
  val imageLimit: Boolean?,
)