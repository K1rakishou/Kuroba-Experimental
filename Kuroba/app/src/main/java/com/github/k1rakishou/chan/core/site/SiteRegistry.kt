/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.chan.core.site.sites.Chan370
import com.github.k1rakishou.chan.core.site.sites.Lainchan
import com.github.k1rakishou.chan.core.site.sites.Sushichan
import com.github.k1rakishou.chan.core.site.sites.Wired7
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
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites.TokyoChronos
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites.WakarimasenMoe
import com.github.k1rakishou.chan.core.site.sites.fuuka.sites.Warosu
import com.github.k1rakishou.chan.core.site.sites.kun8.Kun8
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import java.util.*

/**
 * Registry of all sites and url handler we have.
 */
object SiteRegistry {

  val URL_HANDLERS: List<SiteUrlHandler> by lazy {
    val handlers = mutableListOf<SiteUrlHandler>()

    handlers.add(Chan4.URL_HANDLER)
    handlers.add(Lainchan.URL_HANDLER)
    handlers.add(Sushichan.URL_HANDLER)
    handlers.add(Dvach.URL_HANDLER)
    handlers.add(Wired7.URL_HANDLER)
    handlers.add(Kun8.URL_HANDLER)
    handlers.add(Chan420.URL_HANDLER)
    handlers.add(ArchivedMoe.URL_HANDLER)
    handlers.add(ForPlebs.URL_HANDLER)
    handlers.add(Nyafuu.URL_HANDLER)
    handlers.add(DesuArchive.URL_HANDLER)
    handlers.add(Fireden.URL_HANDLER)
    handlers.add(B4k.URL_HANDLER)
    handlers.add(ArchiveOfSins.URL_HANDLER)
    handlers.add(TokyoChronos.URL_HANDLER)
    handlers.add(Warosu.URL_HANDLER)
    handlers.add(WakarimasenMoe.URL_HANDLER)
    handlers.add(Chan370.URL_HANDLER)

    return@lazy handlers
  }

  val SITE_CLASSES_MAP: Map<SiteDescriptor, Class<out Site>> by lazy {
    val siteClasses = mutableMapOf<SiteDescriptor, Class<out Site>>()

    siteClasses.addSiteToSiteClassesMap(Chan4.SITE_NAME, Chan4::class.java)
    siteClasses.addSiteToSiteClassesMap(Lainchan.SITE_NAME, Lainchan::class.java)
    siteClasses.addSiteToSiteClassesMap(Sushichan.SITE_NAME, Sushichan::class.java)
    siteClasses.addSiteToSiteClassesMap(Dvach.SITE_NAME, Dvach::class.java)
    siteClasses.addSiteToSiteClassesMap(Wired7.SITE_NAME, Wired7::class.java)
    siteClasses.addSiteToSiteClassesMap(Kun8.SITE_NAME, Kun8::class.java)
    siteClasses.addSiteToSiteClassesMap(Chan420.SITE_NAME, Chan420::class.java)
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
    siteClasses.addSiteToSiteClassesMap(Chan370.SITE_NAME, Chan370::class.java)

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