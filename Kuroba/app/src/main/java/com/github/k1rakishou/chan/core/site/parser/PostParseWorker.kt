package com.github.k1rakishou.chan.core.site.parser

import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.mapper.ChanPostMapper

internal class PostParseWorker(
  private val postBuilder: ChanPostBuilder,
  private val postParser: PostParser,
  private val internalPostDescriptors: Set<PostDescriptor>,
  private val savedPosts: Set<PostDescriptor>,
  private val hiddenOrRemovedPosts: Map<PostDescriptor, Int>,
  private val isParsingCatalog: Boolean
) {
  private val callback = object : PostParser.Callback {
    override fun isSaved(postDescriptor: PostDescriptor): Boolean {
      return savedPosts.contains(postDescriptor)
    }

    override fun isHiddenOrRemoved(postDescriptor: PostDescriptor): Int {
      return hiddenOrRemovedPosts[postDescriptor] ?: PostParser.NORMAL_POST
    }

    override fun isInternal(postDescriptor: PostDescriptor): Boolean {
      return internalPostDescriptors.contains(postDescriptor)
    }

    override fun isParsingCatalogPosts(): Boolean {
      return isParsingCatalog
    }
  }

  fun parse(): ChanPost {
    return Try {
      return@Try postParser.parseFull(
        builder = postBuilder,
        callback = callback
      )
    }.mapErrorToValue { error ->
      val errorMessage = """
        Failed to parse post ${postBuilder.postDescriptor.userReadableString()}.
        Post comment: '${postBuilder.postCommentBuilder.getComment()}'.
        Error: '${error.errorMessageOrClassName()}'.
      """.trimIndent()

      Logger.error(TAG, error) { errorMessage }

      val chanPost = ChanPostMapper.fromPostBuilder(postBuilder)
      chanPost.postComment.updateComment { errorMessage }

      return@mapErrorToValue chanPost
    }
  }

  companion object {
    private const val TAG = "PostParseWorker"
  }

}