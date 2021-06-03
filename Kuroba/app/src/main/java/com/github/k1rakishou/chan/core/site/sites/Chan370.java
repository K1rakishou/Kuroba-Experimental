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

import org.jetbrains.annotations.NotNull;

import okhttp3.HttpUrl;

@DoNotStrip
public class Chan370
        extends CommonSite {
    private final ChunkDownloaderSiteProperties chunkDownloaderSiteProperties;
    public static final String SITE_NAME = "370chan";

    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        private static final String ROOT = "https://370chan.info/";

        @Override
        public Class<? extends Site> getSiteClass() {
            return Chan370.class;
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
            return new String[]{"370chan"};
        }

        @Override
        public String desktopUrl(ChanDescriptor chanDescriptor, @Nullable Long postNo) {
            if (chanDescriptor instanceof ChanDescriptor.CatalogDescriptor) {
                return getUrl().newBuilder()
                        .addPathSegment(chanDescriptor.boardCode())
                        .toString();
            } else if (chanDescriptor instanceof ChanDescriptor.ThreadDescriptor) {
                return getUrl().newBuilder()
                        .addPathSegment(chanDescriptor.boardCode())
                        .addPathSegment("res")
                        .addPathSegment(((ChanDescriptor.ThreadDescriptor) chanDescriptor).getThreadNo() + ".html")
                        .toString();
            } else {
                return getUrl().toString();
            }
        }
    };

    public Chan370() {
        chunkDownloaderSiteProperties = new ChunkDownloaderSiteProperties(true, true);
    }

    @Override
    public void setup() {
        setEnabled(true);
        setName(SITE_NAME);
        setIcon(SiteIcon.fromFavicon(getImageLoaderV2(), HttpUrl.parse("https://370chan.info/favicon.ico")));

        setBoards(
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "a"), "anime"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "b"), "apie viską"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "ž"), "kompiuteriniai žaidimai"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "tv"), "televizija & filmai"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "pol"), "politika"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "g"), "technologijos"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "fa"), "mada & stilius"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "fo"), "fotografija"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "mu"), "muzika"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "int"), "International")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean siteFeature(SiteFeature siteFeature) {
                return super.siteFeature(siteFeature) || siteFeature == SiteFeature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this, "https://370chan.info/", "https://370chan.info/"));
        setActions(new VichanActions(this, getProxiedOkHttpClient(), getSiteManager(), getReplyManager()));
        setApi(new VichanApi(getSiteManager(), getBoardManager(), this));
        setParser(new VichanCommentParser(getMockReplyManager()));
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
