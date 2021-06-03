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

import com.github.k1rakishou.chan.core.site.sites.Lainchan
import com.github.k1rakishou.chan.core.site.sites.Sushichan
import com.github.k1rakishou.chan.core.site.sites.Wired7
import com.github.k1rakishou.chan.core.site.sites.Chan370
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
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites.RebeccaBlackTech
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites.TokyoChronos
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites.WakarimasenMoe
import com.github.k1rakishou.chan.core.site.sites.fuuka.sites.Warosu
import com.github.k1rakishou.chan.core.site.sites.kun8.Kun8
import com.github.k1rakishou.chan.core.site.sites.yukila.Yukila
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import java.util.*

/**
 * Registry of all sites and url handler we have.
 */
object SiteRegistry {
  @JvmField
  val URL_HANDLERS: MutableList<SiteUrlHandler> = ArrayList()
  @JvmField
  val SITE_CLASSES_MAP = mutableMapOf<SiteDescriptor, Class<out Site>>()

  init {
    URL_HANDLERS.add(Chan4.URL_HANDLER)
    URL_HANDLERS.add(Lainchan.URL_HANDLER)
    URL_HANDLERS.add(Sushichan.URL_HANDLER)
    URL_HANDLERS.add(Dvach.URL_HANDLER)
    URL_HANDLERS.add(Wired7.URL_HANDLER)
    URL_HANDLERS.add(Kun8.URL_HANDLER)
    URL_HANDLERS.add(Chan420.URL_HANDLER)
    URL_HANDLERS.add(ArchivedMoe.URL_HANDLER)
    URL_HANDLERS.add(ForPlebs.URL_HANDLER)
    URL_HANDLERS.add(Nyafuu.URL_HANDLER)
    URL_HANDLERS.add(RebeccaBlackTech.URL_HANDLER)
    URL_HANDLERS.add(DesuArchive.URL_HANDLER)
    URL_HANDLERS.add(Fireden.URL_HANDLER)
    URL_HANDLERS.add(B4k.URL_HANDLER)
    URL_HANDLERS.add(ArchiveOfSins.URL_HANDLER)
    URL_HANDLERS.add(TokyoChronos.URL_HANDLER)
    URL_HANDLERS.add(Warosu.URL_HANDLER)
    URL_HANDLERS.add(WakarimasenMoe.URL_HANDLER)
    URL_HANDLERS.add(Yukila.URL_HANDLER)
    URL_HANDLERS.add(Chan370.URL_HANDLER)

    addSiteToSiteClassesMap(Chan4.SITE_NAME, Chan4::class.java)
    addSiteToSiteClassesMap(Lainchan.SITE_NAME, Lainchan::class.java)
    addSiteToSiteClassesMap(Sushichan.SITE_NAME, Sushichan::class.java)
    addSiteToSiteClassesMap(Dvach.SITE_NAME, Dvach::class.java)
    addSiteToSiteClassesMap(Wired7.SITE_NAME, Wired7::class.java)
    addSiteToSiteClassesMap(Kun8.SITE_NAME, Kun8::class.java)
    addSiteToSiteClassesMap(Chan420.SITE_NAME, Chan420::class.java)
    addSiteToSiteClassesMap(ArchivedMoe.SITE_NAME, ArchivedMoe::class.java)
    addSiteToSiteClassesMap(ForPlebs.SITE_NAME, ForPlebs::class.java)
    addSiteToSiteClassesMap(Nyafuu.SITE_NAME, Nyafuu::class.java)
    addSiteToSiteClassesMap(RebeccaBlackTech.SITE_NAME, RebeccaBlackTech::class.java)
    addSiteToSiteClassesMap(DesuArchive.SITE_NAME, DesuArchive::class.java)
    addSiteToSiteClassesMap(Fireden.SITE_NAME, Fireden::class.java)
    addSiteToSiteClassesMap(B4k.SITE_NAME, B4k::class.java)
    addSiteToSiteClassesMap(ArchiveOfSins.SITE_NAME, ArchiveOfSins::class.java)
    addSiteToSiteClassesMap(TokyoChronos.SITE_NAME, TokyoChronos::class.java)
    addSiteToSiteClassesMap(Warosu.SITE_NAME, Warosu::class.java)
    addSiteToSiteClassesMap(WakarimasenMoe.SITE_NAME, WakarimasenMoe::class.java)
    addSiteToSiteClassesMap(Yukila.SITE_NAME, Yukila::class.java)
    addSiteToSiteClassesMap(Chan370.SITE_NAME, Chan370::class.java)
  }

  private fun addSiteToSiteClassesMap(siteName: String, siteClass: Class<out Site>) {
    val siteDescriptor = SiteDescriptor.create(siteName)

    require(!SITE_CLASSES_MAP.contains(siteDescriptor)) {
      "Site $siteName already added! Make sure that no sites share the same name!"
    }

    SITE_CLASSES_MAP[siteDescriptor] = siteClass
  }
}