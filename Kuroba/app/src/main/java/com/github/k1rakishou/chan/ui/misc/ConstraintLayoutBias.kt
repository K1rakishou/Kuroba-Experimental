package com.github.k1rakishou.chan.ui.misc

class ConstraintLayoutBias(
  val horizontalBias: Float,
  val verticalBias: Float
) {

  companion object {
    fun center(): ConstraintLayoutBias = ConstraintLayoutBias(0.5f, 0.5f)
  }
}