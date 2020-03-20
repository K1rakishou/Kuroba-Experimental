package com.github.adamantcheese.base.loadable

enum class LoadableType(val typeValue: Int) {
    CatalogLoadable(0),
    ThreadLoadable(1);

    companion object {
        fun fromTypeValue(typeValue: Int): LoadableType {
            return when (typeValue) {
                0 -> CatalogLoadable
                1 -> ThreadLoadable
                else -> throw IllegalStateException("typeValue: $typeValue is not supported!")
            }
        }
    }
}