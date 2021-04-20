package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.features.posting.solver.TwoCaptchaSolver
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger

class TwoCaptchaCheckBalanceUseCase(
  private val twoCaptchaSolver: TwoCaptchaSolver
) : ISuspendUseCase<Unit, String> {

  override suspend fun execute(parameter: Unit): String {
    return ModularResult.Try { checkBalance() }
      .peekError { error -> Logger.e(TAG, "checkBalance() error", error) }
      .mapErrorToValue { error -> error.errorMessageOrClassName() }
  }

  private suspend fun checkBalance(): String {
    val balanceResponse = twoCaptchaSolver.getAccountBalance(forced = true).unwrap()

    if (balanceResponse == null) {
      Logger.d(TAG, "enqueueSolution() getAccountBalance() -> null")
      return "Failed to check account balance, see logs for more info"
    }

    if (!balanceResponse.isOk()) {
      Logger.d(TAG, "enqueueSolution() balanceResponse is not ok, balanceResponse=$balanceResponse")

      val errorCode = balanceResponse.response.requestRaw
      val errorText = balanceResponse.response.errorTextOrDefault()

      return "Failed to get balance. ErrorCode=${errorCode}, ErrorText=\'${errorText}\'"
    }

    val balance = balanceResponse.balance
    if (balance == null) {
      Logger.d(TAG, "enqueueSolution() bad balance: $balance")
      return "Failed to check account balance (balance is null), see logs for more info"
    }

    return "Balance: $balance"
  }

  companion object {
    private const val TAG = "TwoCaptchaCheckBalanceUseCase"
  }

}