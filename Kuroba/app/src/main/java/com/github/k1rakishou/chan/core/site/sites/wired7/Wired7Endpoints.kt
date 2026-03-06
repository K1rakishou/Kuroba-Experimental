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
package com.github.k1rakishou.chan.core.site.sites.wired7;

import com.github.k1rakishou.chan.core.site.common.CommonSite;
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor;
import com.github.k1rakishou.chan.core.site.common.vichan.VichanEndpoints;

import java.util.Map;

import okhttp3.HttpUrl;

public class Wired7Endpoints extends VichanEndpoints {

    public Wired7Endpoints(CommonSite commonSite, String rootUrl, String sysUrl) {
        super(commonSite, rootUrl, sysUrl);
    }

    @Override
    public HttpUrl thumbnailUrl(
            BoardDescriptor boardDescriptor,
            boolean spoiler,
            int customSpoilter,
            Map<String, String> arg
    ) {
        return root.builder()
                .s(boardDescriptor.getBoardCode())
                .s("thumb")
                .s(arg.get("tn"))
                .url();
    }
}
