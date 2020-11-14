package com.github.k1rakishou.chan.ui.misc

enum class ConstraintLayoutBiasPair(
  val horizontalBias: Float,
  val verticalBias: Float
) {
  Center(.5f, .5f),
  TopLeft(0f, 0f),
  Top(.5f, 0f),
  TopRight(1f, 0f),
  Right(1f, .5f),
  BottomRight(1f, 1f),
  Bottom(.5f, 1f),
  BottomLeft(0f, 1f),
  Left(0f, .5f)
}