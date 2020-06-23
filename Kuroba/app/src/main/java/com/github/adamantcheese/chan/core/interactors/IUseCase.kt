package com.github.adamantcheese.chan.core.interactors

interface IUseCase<Parameter, Result> {
  fun execute(parameter: Parameter): Result
}