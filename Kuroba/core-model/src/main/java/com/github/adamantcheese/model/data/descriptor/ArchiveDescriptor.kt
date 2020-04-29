package com.github.adamantcheese.model.data.descriptor

class ArchiveDescriptor(
        val name: String,
        val domain: String,
        val archiveType: ArchiveType
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

    enum class ArchiveType(val domain: String) {
        ForPlebs("archive.4plebs.org"),
        Nyafuu("archive.nyafuu.org"),
        RebeccaBlackTech("archive.rebeccablacktech.com"),
        Warosu("warosu.org"),
        DesuArchive("desuarchive.org"),
        Fireden("boards.fireden.net"),
        B4k("arch.b4k.co"),
        Bstats("archive.b-stats.org"),
        ArchivedMoe("archived.moe"),
        TheBarchive("thebarchive.com"),
        ArchiveOfSins("archiveofsins.com");

        companion object {
            private val map = hashMapOf(
                    ForPlebs.domain to ForPlebs,
                    Nyafuu.domain to Nyafuu,
                    RebeccaBlackTech.domain to RebeccaBlackTech,
                    Warosu.domain to Warosu,
                    DesuArchive.domain to DesuArchive,
                    Fireden.domain to Fireden,
                    B4k.domain to B4k,
                    Bstats.domain to Bstats,
                    ArchivedMoe.domain to ArchivedMoe,
                    TheBarchive.domain to TheBarchive,
                    ArchiveOfSins.domain to ArchiveOfSins
            )

            fun byDomain(domain: String): ArchiveType {
                return map[domain]
                        ?: throw IllegalArgumentException("Unsupported archive: ${domain}")
            }
        }
    }
}