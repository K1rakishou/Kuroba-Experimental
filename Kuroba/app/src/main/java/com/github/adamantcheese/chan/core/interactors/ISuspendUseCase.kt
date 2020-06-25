package com.github.adamantcheese.chan.core.interactors

interface ISuspendUseCase<Parameter, Result> {
  suspend fun execute(parameter: Parameter): Result
}