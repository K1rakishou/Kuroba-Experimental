package com.github.adamantcheese.database.data

import com.github.adamantcheese.base.loadable.LoadableType


/**
 * For now it's called Loadable2. Rename to normal loadable after refactoring the main Loadable
 * (which is a database entity and should be called accordingly and SHOULDN'T BE used in any place
 * other than DB access. Right now Loadable is being used throughout the whole project which is bad!
 * */
data class Loadable2(
        val threadUid: String,
        val siteName: String,
        val boardCode: String,
        val opId: Long,
        val loadableType: LoadableType
)