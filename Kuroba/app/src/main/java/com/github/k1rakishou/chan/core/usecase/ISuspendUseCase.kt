package com.github.k1rakishou.chan.core.usecase

interface ISuspendUseCase<Parameter, Result> {
  suspend fun execute(parameter: Parameter): Result
}