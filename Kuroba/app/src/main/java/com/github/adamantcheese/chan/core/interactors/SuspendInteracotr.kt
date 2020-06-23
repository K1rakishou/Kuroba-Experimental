package com.github.adamantcheese.chan.core.interactors

interface SuspendUseCase<Params, Result> {
  suspend fun execute(params: Params): Result
}