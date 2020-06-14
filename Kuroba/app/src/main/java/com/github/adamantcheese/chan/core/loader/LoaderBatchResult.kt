package com.github.adamantcheese.chan.core.loader

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Loadable

data class LoaderBatchResult(
  val loadable: Loadable,
  val post: Post,
  val results: List<LoaderResult>
)