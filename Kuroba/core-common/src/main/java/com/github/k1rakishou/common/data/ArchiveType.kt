package com.github.k1rakishou.common.data

enum class ArchiveType(
  val domain: String
) {
    ForPlebs("archive.4plebs.org"),
    Nyafuu("archive.nyafuu.org"),
    Warosu("warosu.org"),
    DesuArchive("desuarchive.org"),
    Fireden("boards.fireden.net"),
    B4k("arch.b4k.co"),
    Bstats("archive.b-stats.org"),
    ArchivedMoe("archived.moe"),
    TheBarchive("thebarchive.com"),
    ArchiveOfSins("archiveofsins.com"),
    TokyoChronos("tokyochronos.net"),
    WakarimasenMoe("archive.wakarimasen.moe"),
    RozenArcana("archive.alice.al");

    fun isFoolFuukaArchive(): Boolean {
        return when (this) {
            Warosu -> false
            ForPlebs,
            Nyafuu,
            DesuArchive,
            Fireden,
            B4k,
            Bstats,
            ArchivedMoe,
            TheBarchive,
            ArchiveOfSins,
            TokyoChronos,
            WakarimasenMoe,
            RozenArcana -> true
        }
    }

    fun isFuukaArchive(): Boolean {
        return when (this) {
            Warosu -> true
            ForPlebs,
            Nyafuu,
            DesuArchive,
            Fireden,
            B4k,
            Bstats,
            ArchivedMoe,
            TheBarchive,
            ArchiveOfSins,
            TokyoChronos,
            WakarimasenMoe,
            RozenArcana -> false
        }
    }

    companion object {
        private val map = hashMapOf(
            ForPlebs.domain to ForPlebs,
            Nyafuu.domain to Nyafuu,
            Warosu.domain to Warosu,
            DesuArchive.domain to DesuArchive,
            Fireden.domain to Fireden,
            B4k.domain to B4k,
            Bstats.domain to Bstats,
            ArchivedMoe.domain to ArchivedMoe,
            TheBarchive.domain to TheBarchive,
            ArchiveOfSins.domain to ArchiveOfSins,
            TokyoChronos.domain to TokyoChronos,
            WakarimasenMoe.domain to WakarimasenMoe,
            RozenArcana.domain to RozenArcana,
        )

        fun hasDomain(domain: String): Boolean {
            return map.containsKey(domain)
        }

        fun byDomain(domain: String): ArchiveType? {
            return map[domain]
        }
    }
}