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
package com.github.k1rakishou.chan.core.site.sites.soyjakparty;

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

import java.util.Map;

import okhttp3.HttpUrl;

@DoNotStrip
public class SoyjakParty
        extends CommonSite {
    private final ChunkDownloaderSiteProperties chunkDownloaderSiteProperties;
    public static final String SITE_NAME = "Soyjak.party";

    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        private static final String ROOT = "https://soyjak.party/";

        @Override
        public Class<? extends Site> getSiteClass() {
            return SoyjakParty.class;
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
            return new String[]{"Soyjak.party"};
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
                        .addPathSegment("thread")
                        .addPathSegment(((ChanDescriptor.ThreadDescriptor) chanDescriptor).getThreadNo() + ".html")
                        .toString();
            } else {
                return null;
            }
        }
    };

    public SoyjakParty() {
        chunkDownloaderSiteProperties = new ChunkDownloaderSiteProperties(true, true);
    }

    @Override
    public void setup() {
        setEnabled(true);
        setName(SITE_NAME);
        setIcon(SiteIcon.fromFavicon(getImageLoaderV2(), HttpUrl.parse("https://soyjak.party/favicon.ico")));

        setBoards(
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "q"), "the 'party"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "soy"), "soyjaks"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "jak"), "jaks"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "qa"), "question & answer"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "r"), "requests and soy art"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "caca"), "cacaborea"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "a"), "tranime"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "raid"), "raid: shadow legends"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "int"), "international"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "mtv"), "music, television, video games"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "pol"), "international politics"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "sci"), "soyence and technology"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "craft"), "minecraft"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "fnac"), "five nights at cobson's"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "nate"), "coals")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean siteFeature(SiteFeature siteFeature) {
                return super.siteFeature(siteFeature); //features are not implemented.
            }
        });

        setEndpoints(new VichanEndpoints(this, "https://soyjak.party/", "https://soyjak.party/")
        {
            @Override
            public HttpUrl thumbnailUrl(BoardDescriptor boardDescriptor, boolean spoiler, int customSpoilers, Map<String, String> arg) {
                String extension = switch (arg.get("ext")){
                    case "jpg", "jpeg", "gif", "webp" -> "." + arg.get("ext");
                    case "webm", "mp4" -> ".jpg";
                    default -> ".png";
                };
                return root.builder()
                        .s(boardDescriptor.getBoardCode())
                        .s("thumb")
                        .s(arg.get("tim") + extension)
                        .url();
            }
            @Override
            public HttpUrl thread(ChanDescriptor.ThreadDescriptor threadDescriptor) {
                return root.builder()
                        .s(threadDescriptor.boardCode())
                        .s("thread")
                        .s(threadDescriptor.getThreadNo() + ".json")
                        .url();
            }
        });
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
