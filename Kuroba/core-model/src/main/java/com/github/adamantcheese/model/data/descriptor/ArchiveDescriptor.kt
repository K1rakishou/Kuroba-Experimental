package com.github.adamantcheese.model.data.descriptor

class ArchiveDescriptor(
        val name: String,
        val domain: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArchiveDescriptor) return false

        if (domain != other.domain) return false

        return true
    }

    override fun hashCode(): Int {
        return domain.hashCode()
    }

    override fun toString(): String {
        return "ArchiveDescriptor(name='$name', domain='$domain')"
    }
}