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
package com.github.adamantcheese.chan.core.site.sites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.manager.SiteManager;
import com.github.adamantcheese.chan.core.site.ChunkDownloaderSiteProperties;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.MultipartHttpCall;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanActions;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanApi;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanEndpoints;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;
import com.github.adamantcheese.chan.core.site.parser.CommentParserType;
import com.github.adamantcheese.common.ModularResult;
import com.github.adamantcheese.model.data.board.ChanBoard;
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor;
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.Unit;
import okhttp3.HttpUrl;
import okhttp3.Response;

import static android.text.TextUtils.isEmpty;

public class Wired7
        extends CommonSite {
    private final ChunkDownloaderSiteProperties chunkDownloaderSiteProperties;
    public static final String SITE_NAME = "Wired-7";

    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSite.CommonSiteUrlHandler() {
        private static final String ROOT = "https://wired-7.org/";

        @Override
        public Class<? extends Site> getSiteClass() {
            return Wired7.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse(ROOT);
        }

        @Override
        public String[] getMediaHosts() {
            return new String[]{ROOT};
        }

        @Override
        public String[] getNames() {
            return new String[]{"Wired-7, wired7, Wired7"};
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
                        .addPathSegment(String.valueOf(((ChanDescriptor.ThreadDescriptor) chanDescriptor).getThreadNo()))
                        .toString();
            } else {
                return getUrl().toString();
            }
        }
    };

    public Wired7() {
        chunkDownloaderSiteProperties = new ChunkDownloaderSiteProperties(
                true,
                true,
                // Wired-7 sends incorrect file md5 hash sometimes
                false
        );
    }

    @Override
    public void setup() {
        setEnabled(true);
        setName(SITE_NAME);
        setIcon(SiteIcon.fromFavicon(getImageLoaderV2(), HttpUrl.parse("https://wired-7.org/favicon.ico")));

        setBoards(
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "b"), "Random"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "h"), "Hentai"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "hum"), "Humanidad"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "i"), "Internacional/Random"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "pol"), "Política"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "meta"), "Wired-7 Metaboard"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "a"), "Anime"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "jp"), "Cultura Japonesa"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "mu"), "Musica & Audio"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "tech"), "Tecnología"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "v"), "Videojuegos y Gaming"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "vis"), "Medios Visuales"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "x"), "Paranormal"),
                ChanBoard.create(BoardDescriptor.create(siteDescriptor().getSiteName(), "lain"), "Lain")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean siteFeature(SiteFeature siteFeature) {
                return super.siteFeature(siteFeature) || siteFeature == SiteFeature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this, "https://wired-7.org", "https://wired-7.org"));
        setActions(new Wired7Actions(this, getOkHttpClient(), getSiteManager()));
        setApi(new VichanApi(getSiteManager(), getBoardManager(), this));
        setParser(new VichanCommentParser(getMockReplyManager()));
    }

    private static class Wired7Actions extends VichanActions {

        Wired7Actions(
                CommonSite commonSite,
                NetModule.ProxiedOkHttpClient okHttpClient,
                SiteManager siteManager
        ) {
            super(commonSite, okHttpClient, siteManager);
        }

        @Override
        public ModularResult<Unit> setupPost(Reply reply, MultipartHttpCall call) {
            return ModularResult.Try(() -> {
                ChanDescriptor chanDescriptor = Objects.requireNonNull(
                        reply.chanDescriptor,
                        "reply.chanDescriptor is null"
                );

                call.parameter("board", chanDescriptor.boardCode());

                if (chanDescriptor instanceof ChanDescriptor.ThreadDescriptor) {
                    long threadNo = ((ChanDescriptor.ThreadDescriptor) chanDescriptor).getThreadNo();

                    call.parameter("thread", String.valueOf(threadNo));
                }

                // Added with VichanAntispam.
                call.parameter("post", "Post");

                call.parameter("password", reply.password);
                call.parameter("name", reply.name);
                call.parameter("email", reply.options);

                if (!isEmpty(reply.subject)) {
                    call.parameter("subject", reply.subject);
                }

                call.parameter("body", reply.comment);

                if (reply.file != null) {
                    call.fileParameter("file", reply.fileName, reply.file);
                }

                if (reply.spoilerImage) {
                    call.parameter("spoiler", "on");
                }

                return Unit.INSTANCE;
            });
        }

        @Override
        public void handlePost(ReplyResponse replyResponse, Response response, String result) {
            Matcher auth = Pattern.compile("\"captcha\": ?true").matcher(result);
            Matcher err = errorPattern().matcher(result);
            if (auth.find()) {
                replyResponse.requireAuthentication = true;
                replyResponse.errorMessage = result;
            } else if (err.find()) {
                replyResponse.errorMessage = Jsoup.parse(err.group(1)).body().text();
            } else {
                HttpUrl url = response.request().url();
                Matcher m = Pattern.compile("/\\w+/\\w+/(\\d+)(.html)?").matcher(url.encodedPath());
                try {
                    if (m.find()) {
                        replyResponse.threadNo = Integer.parseInt(m.group(1));
                        String fragment = url.encodedFragment();
                        if (fragment != null) {
                            replyResponse.postNo = Integer.parseInt(fragment);
                        } else {
                            replyResponse.postNo = replyResponse.threadNo;
                        }
                        replyResponse.posted = true;
                    }
                } catch (NumberFormatException ignored) {
                    replyResponse.errorMessage = "Error posting: could not find posted thread.";
                }
            }
        }
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
