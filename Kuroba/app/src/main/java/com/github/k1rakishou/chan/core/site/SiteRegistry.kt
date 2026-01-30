package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.chan.core.site.sites.Chan370
import com.github.k1rakishou.chan.core.site.sites.CompositeCatalogSite
import com.github.k1rakishou.chan.core.site.sites.Diochan
import com.github.k1rakishou.chan.core.site.sites.Sushichan
import com.github.k1rakishou.chan.core.site.sites.Vhschan
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.chan420.Chan420
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites.ArchiveOfSins
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites.ArchivedMoe
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites.B4k
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites.DesuArchive
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites.Fireden
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites.ForPlebs
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites.Nyafuu
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites.RozenArcana
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites.TokyoChronos
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites.WakarimasenMoe
import com.github.k1rakishou.chan.core.site.sites.fuuka.sites.Warosu
import com.github.k1rakishou.chan.core.site.sites.kun8.Kun8
import com.github.k1rakishou.chan.core.site.sites.lainchan.Lainchan
import com.github.k1rakishou.chan.core.site.sites.leftypol.Leftypol
import com.github.k1rakishou.chan.core.site.sites.lynxchan.Endchan
import com.github.k1rakishou.chan.core.site.sites.lynxchan.Kohlchan
import com.github.k1rakishou.chan.core.site.sites.lynxchan.Krautchan
import com.github.k1rakishou.chan.core.site.sites.lynxchan.YesHoney
import com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8.Chan8Moe
import com.github.k1rakishou.chan.core.site.sites.soyjakparty.SoyjakParty
import com.github.k1rakishou.chan.core.site.sites.wired7.Wired7
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

/**
 * Registry of all sites and url handler we have.
 */
object SiteRegistry {

  val SITE_CLASSES_MAP: Map<SiteDescriptor, Class<out Site>> by lazy {
    val siteClasses = mutableMapOf<SiteDescriptor, Class<out Site>>()

    siteClasses.addSiteToSiteClassesMap(Chan4.SITE_NAME, Chan4::class.java)
    siteClasses.addSiteToSiteClassesMap(Diochan.SITE_NAME, Diochan::class.java)
    siteClasses.addSiteToSiteClassesMap(Lainchan.SITE_NAME, Lainchan::class.java)
    siteClasses.addSiteToSiteClassesMap(Sushichan.SITE_NAME, Sushichan::class.java)
    siteClasses.addSiteToSiteClassesMap(Dvach.SITE_NAME, Dvach::class.java)
    siteClasses.addSiteToSiteClassesMap(Wired7.SITE_NAME, Wired7::class.java)
    siteClasses.addSiteToSiteClassesMap(Kun8.SITE_NAME, Kun8::class.java)
    siteClasses.addSiteToSiteClassesMap(Chan420.SITE_NAME, Chan420::class.java)
    siteClasses.addSiteToSiteClassesMap(Chan370.SITE_NAME, Chan370::class.java)
    siteClasses.addSiteToSiteClassesMap(Vhschan.SITE_NAME, Vhschan::class.java)
    siteClasses.addSiteToSiteClassesMap(Endchan.SITE_NAME, Endchan::class.java)
    siteClasses.addSiteToSiteClassesMap(Kohlchan.SITE_NAME, Kohlchan::class.java)
    siteClasses.addSiteToSiteClassesMap(Krautchan.SITE_NAME, Krautchan::class.java)
    siteClasses.addSiteToSiteClassesMap(Chan8Moe.SITE_NAME, Chan8Moe::class.java)
    siteClasses.addSiteToSiteClassesMap(YesHoney.SITE_NAME, YesHoney::class.java)
    siteClasses.addSiteToSiteClassesMap(Leftypol.SITE_NAME, Leftypol::class.java)
    siteClasses.addSiteToSiteClassesMap(SoyjakParty.SITE_NAME, SoyjakParty::class.java)

    // By default, archives should be placed after the regular sites
    siteClasses.addSiteToSiteClassesMap(ArchivedMoe.SITE_NAME, ArchivedMoe::class.java)
    siteClasses.addSiteToSiteClassesMap(ForPlebs.SITE_NAME, ForPlebs::class.java)
    siteClasses.addSiteToSiteClassesMap(Nyafuu.SITE_NAME, Nyafuu::class.java)
    siteClasses.addSiteToSiteClassesMap(DesuArchive.SITE_NAME, DesuArchive::class.java)
    siteClasses.addSiteToSiteClassesMap(Fireden.SITE_NAME, Fireden::class.java)
    siteClasses.addSiteToSiteClassesMap(B4k.SITE_NAME, B4k::class.java)
    siteClasses.addSiteToSiteClassesMap(ArchiveOfSins.SITE_NAME, ArchiveOfSins::class.java)
    siteClasses.addSiteToSiteClassesMap(TokyoChronos.SITE_NAME, TokyoChronos::class.java)
    siteClasses.addSiteToSiteClassesMap(Warosu.SITE_NAME, Warosu::class.java)
    siteClasses.addSiteToSiteClassesMap(WakarimasenMoe.SITE_NAME, WakarimasenMoe::class.java)
    siteClasses.addSiteToSiteClassesMap(RozenArcana.SITE_NAME, RozenArcana::class.java)

    // A synthetic site which only purpose is to have an order in the global site order for composed
    // catalogs when showing them on the board selection screen.
    siteClasses.addSiteToSiteClassesMap(CompositeCatalogSite.SITE_NAME, CompositeCatalogSite::class.java)

    return@lazy siteClasses
  }

  private fun MutableMap<SiteDescriptor, Class<out Site>>.addSiteToSiteClassesMap(
    siteName: String,
    siteClass: Class<out Site>
  ) {
    val siteDescriptor = SiteDescriptor.create(siteName)

    require(!this.contains(siteDescriptor)) {
      "Site $siteName already added! Make sure that no sites share the same name!"
    }

    this[siteDescriptor] = siteClass
  }
}