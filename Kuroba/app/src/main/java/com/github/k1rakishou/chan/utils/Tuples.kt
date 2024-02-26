package com.github.k1rakishou.chan.utils

fun <T1, T2> tupleOf(t1: T1, t2: T2): Tuple2<T1, T2> {
  return Tuple2(t1, t2)
}

fun <T1, T2, T3> tupleOf(t1: T1, t2: T2, t3: T3): Tuple3<T1, T2, T3> {
  return Tuple3(t1, t2, t3)
}

data class Tuple2<T1, T2>(
  val t1: T1,
  val t2: T2
)

data class Tuple3<T1, T2, T3>(
  val t1: T1,
  val t2: T2,
  val t3: T3
)