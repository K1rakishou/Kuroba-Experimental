package com.github.k1rakishou.common

interface VoidFunction {
  fun invoke()
}

interface ModularFunction<T> {
  fun invoke(param: T)
}

interface ModularFunction2<T1, T2> {
  fun invoke(param1: T1, param2: T2)
}

interface GenericFunction<R> {
  fun invoke(): R
}

interface GenericModularFunction<T, R> {
  fun invoke(param: T): R
}