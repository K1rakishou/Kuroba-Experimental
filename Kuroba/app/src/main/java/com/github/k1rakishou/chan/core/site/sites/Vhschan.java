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

import android.text.TextUtils;
import android.webkit.MimeTypeMap;

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

import java.util.Map;

import okhttp3.HttpUrl;

@DoNotStrip
public class Vhschan extends CommonSite {
    private final ChunkDownloaderSiteProperties chunkDownloaderSiteProperties;
    public static final String SITE_NAME = "vhschan";
    public static final SiteDescriptor SITE_DESCRIPTOR = SiteDescriptor.Companion.create(SITE_NAME);

    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        private static final String ROOT = "https://vhschan.org/";

        @Override
        public Class<? extends Site> getSiteClass() {
            return Vhschan.class;
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
            return new String[]{"vhschan"};
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
                //return getUrl().toString();
                return null;
            }
        }
    };

    private class VhschanEndpoints extends VichanEndpoints {

        public VhschanEndpoints(CommonSite commonSite, String rootUrl, String sysUrl) {
            super(commonSite, rootUrl, sysUrl);
        }

        @NonNull
        @Override
        public HttpUrl thumbnailUrl(BoardDescriptor boardDescriptor, boolean spoiler, int customSpoilers, Map<String, String> arg) {
            String tim = arg.get("tim");
            String ext = arg.get("ext");

            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (!TextUtils.isEmpty(mimeType) && !mimeType.startsWith("image/")) {
                ext = "jpg";
            }

            if (!ext.startsWith(".")) {
                ext = "." + ext;
            }

            return root.builder()
                    .s(boardDescriptor.getBoardCode())
                    .s("thumb")
                    .s(tim + ext)
                    .url();
        }
    }

    public Vhschan() {
        chunkDownloaderSiteProperties = new ChunkDownloaderSiteProperties(true, true);
    }

    @Override
    public void setup() {
        setEnabled(true);
        setName(SITE_NAME);
        setIcon(SiteIcon.fromFavicon(getImageLoaderV2(), HttpUrl.parse("https://vhschan.org/stylesheets/favicon.ico")));

        setBoards(
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "b"), "Betamax"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "n64"), "Jogos"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "k7"), "Musicas"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "warhol"), "Artes"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "sebo"), "Cafe, livros e Londres"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "uhf"), "TV, Filmes e series"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "ego"), "how to dress well"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "meth"), "The krystal ship"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "oprah"), "baw"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "toth"), "pineal gland"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "win95"), "CyberTech"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "loverboy"), "Good Old-Fashioned Lover Boy"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "sac"), "Serviço de atendimento ao channer"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "Recentes"), "Recentes")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean siteFeature(SiteFeature siteFeature) {
                return super.siteFeature(siteFeature) || siteFeature == SiteFeature.POSTING;
            }
        });

        setEndpoints(new VhschanEndpoints(this, "https://vhschan.org", "https://vhschan.org"));
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
