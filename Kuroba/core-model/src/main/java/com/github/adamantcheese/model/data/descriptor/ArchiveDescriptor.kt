package com.github.adamantcheese.model.data.descriptor

class ArchiveDescriptor(
  private var databaseId: Long = -1,
  val name: String,
  val domain: String,
  val archiveType: ArchiveType
) {

  @Synchronized
  fun setArchiveDatabaseId(archiveDbId: Long) {
    this.databaseId = archiveDbId
  }

  @Synchronized
  fun getArchiveDatabaseId(): Long {
    require(databaseId != -1L) {
      "Attempt to access ArchiveDescriptor.databaseId before it was fully initialized"
    }

    return databaseId
  }

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
    return "ArchiveDescriptor(databaseId='$databaseId', name='$name', domain='$domain')"
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

  companion object {
    // Default value for archiveId key in the database. If a post has archiveId == 0L that means the
    // post was fetched from the original server and not from an archive.
    const val NO_ARCHIVE_ID = 0L

    @JvmStatic
    fun isActualArchive(archiveId: Long): Boolean {
      return archiveId != NO_ARCHIVE_ID
    }
  }
}