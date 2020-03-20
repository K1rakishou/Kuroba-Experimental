package com.github.adamantcheese.common

interface VoidFunction {
    fun invoke()
}

interface ModularFunction<T> {
    fun invoke(param: T)
}

interface GenericFunction<R> {
    fun invoke(): R
}

interface GenericModularFunction<T, R> {
    fun invoke(param: T): R
}