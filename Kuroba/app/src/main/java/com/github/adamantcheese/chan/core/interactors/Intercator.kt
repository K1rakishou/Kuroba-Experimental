package com.github.adamantcheese.chan.core.interactors

interface Intercator<Params, Result> {
  fun execute(params: Params): Result
}