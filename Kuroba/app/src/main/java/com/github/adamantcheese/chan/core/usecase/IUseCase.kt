package com.github.adamantcheese.chan.core.usecase

interface IUseCase<Parameter, Result> {
  fun execute(parameter: Parameter): Result
}