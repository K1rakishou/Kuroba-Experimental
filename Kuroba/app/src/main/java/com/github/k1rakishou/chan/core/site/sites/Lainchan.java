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
package com.github.k1rakishou.chan.core.site.sites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties;
import com.github.k1rakishou.chan.core.site.Site;
import com.github.k1rakishou.chan.core.site.SiteIcon;
import com.github.k1rakishou.chan.core.site.common.CommonSite;
import com.github.k1rakishou.chan.core.site.common.vichan.VichanActions;
import com.github.k1rakishou.chan.core.site.common.vichan.VichanApi;
import com.github.k1rakishou.chan.core.site.common.vichan.VichanCommentParser;
import com.github.k1rakishou.chan.core.site.common.vichan.VichanEndpoints;
import com.github.k1rakishou.chan.core.site.parser.CommentParserType;
import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.model.data.board.ChanBoard;
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor;

import org.jetbrains.annotations.NotNull;

import okhttp3.HttpUrl;

@DoNotStrip
public class Lainchan extends CommonSite {
    private final ChunkDownloaderSiteProperties chunkDownloaderSiteProperties;

    public static final String SITE_NAME = "Lainchan";
    public static final SiteDescriptor SITE_DESCRIPTOR = SiteDescriptor.Companion.create(SITE_NAME);

    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        private static final String ROOT = "https://lainchan.org/";

        @Override
        public Class<? extends Site> getSiteClass() {
            return Lainchan.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse(ROOT);
        }

        @Override
        public HttpUrl[] getMediaHosts() {
            return new HttpUrl[]{getUrl()};
        }

        @Override
        public String[] getNames() {
            return new String[]{"lainchan"};
        }

        @Override
        public String desktopUrl(ChanDescriptor chanDescriptor, @Nullable Long postNo) {
            if (chanDescriptor instanceof ChanDescriptor.CatalogDescriptor) {
                return getUrl().newBuilder().addPathSegment(chanDescriptor.boardCode()).toString();
            } else if (chanDescriptor instanceof ChanDescriptor.ThreadDescriptor) {
                return getUrl().newBuilder()
                        .addPathSegment(chanDescriptor.boardCode())
                        .addPathSegment("res")
                        .addPathSegment(((ChanDescriptor.ThreadDescriptor) chanDescriptor).getThreadNo() + ".html")
                        .toString();
            } else {
                return null;
            }
        }
    };

    public Lainchan() {
        chunkDownloaderSiteProperties = new ChunkDownloaderSiteProperties(true, true);
    }

    @Override
    public void setup() {
        setEnabled(true);
        setName(SITE_NAME);
        setIcon(SiteIcon.fromFavicon(getImageLoaderV2(), HttpUrl.parse("https://lainchan.org/favicon.ico")));

        setBoards(
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "λ"), "Programming"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "Δ"), "Do It Yourself"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "sec"), "Security"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "Ω"), "Technology"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "inter"), "Games and Interactive Media"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "lit"), "Literature"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "music"), "Musical and Audible Media"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "vis"), "Visual Media"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "hum"), "Humanity"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "drug"), "Drugs 3.0"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "zzz"), "Consciousness and Dreams"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "layer"), "layer"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "q"), "Questions and Complaints"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "r"), "Random"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "lain"), "Lain"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "culture"), "Culture 15 freshly bumped threads"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "psy"), "Psychopharmacology 15 freshly bumped threads"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "mega"), "15 freshly bumped threads")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean siteFeature(SiteFeature siteFeature) {
                return super.siteFeature(siteFeature) || siteFeature == SiteFeature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this, "https://lainchan.org", "https://lainchan.org"));
        setActions(new VichanActions(this, getProxiedOkHttpClient(), getSiteManager(), getReplyManager()));
        setApi(new VichanApi(getSiteManager(), getBoardManager(), this));
        setParser(new VichanCommentParser());
    }

    @NotNull
    @Override
    public CommentParserType commentParserType() {
        return CommentParserType.VichanParser;
    }

    @NonNull
    @Override
    public ChunkDownloaderSiteProperties getChunkDownloaderSiteProperties() {
        return chunkDownloaderSiteProperties;
    }
}
