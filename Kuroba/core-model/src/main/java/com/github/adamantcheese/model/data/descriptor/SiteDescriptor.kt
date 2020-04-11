package com.github.adamantcheese.model.data.descriptor

data class SiteDescriptor(
        val siteName: String
) {
    fun is4chan(): Boolean {
        // Kinda bad, but Chan4 file is located in another module so for now it's impossible to use
        // it
        return siteName.equals("4chan", ignoreCase = true)
    }
}