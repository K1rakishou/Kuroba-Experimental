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

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl

interface SiteUrlHandler {
  fun getSiteClass(): Class<out Site>
  fun matchesName(value: String): Boolean
  fun respondsTo(url: HttpUrl): Boolean
  fun matchesMediaHost(url: HttpUrl): Boolean
  fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?): String?
  fun resolveChanDescriptor(site: Site, url: HttpUrl): ResolvedChanDescriptor?
}