package com.github.adamantcheese.chan.core.interactors

interface SuspendIntercator<Params, Result> {
  suspend fun execute(params: Params): Result
}