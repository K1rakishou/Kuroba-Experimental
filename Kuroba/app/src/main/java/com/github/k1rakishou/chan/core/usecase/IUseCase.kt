package com.github.k1rakishou.chan.core.usecase

interface IUseCase<Parameter, Result> {
  fun execute(parameter: Parameter): Result
}