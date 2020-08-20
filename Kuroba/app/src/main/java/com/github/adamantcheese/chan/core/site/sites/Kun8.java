package com.github.adamantcheese.chan.core.site.sites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.ChunkDownloaderSiteProperties;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.MultipartHttpCall;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanActions;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanApi;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanEndpoints;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.parser.CommentParserType;
import com.github.adamantcheese.common.ModularResult;
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

import kotlin.Unit;
import okhttp3.HttpUrl;

public class Kun8
        extends CommonSite {
    private final ChunkDownloaderSiteProperties chunkDownloaderSiteProperties;
    public static final String SITE_NAME = "8kun";

    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        private final String[] mediaHosts = new String[]{"media.8kun.top"};

        @Override
        public Class<? extends Site> getSiteClass() {
            return Kun8.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("https://8kun.top/");
        }

        @Override
        public String[] getNames() {
            return new String[]{"8kun"};
        }

        @Override
        public String[] getMediaHosts() {
            return mediaHosts;
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

    public Kun8() {
        chunkDownloaderSiteProperties = new ChunkDownloaderSiteProperties(true, true);
    }

    @Override
    public void setup() {
        setEnabled(true);
        setName(SITE_NAME);
        setIcon(SiteIcon.fromFavicon(getImageLoaderV2(), HttpUrl.parse("https://8kun.top/static/favicon.ico")));
        setBoardsType(BoardsType.INFINITE);
        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean siteFeature(SiteFeature siteFeature) {
                return super.siteFeature(siteFeature) || siteFeature == SiteFeature.POSTING
                        || siteFeature == SiteFeature.POST_DELETE;
            }
        });

        setEndpoints(new VichanEndpoints(this, "https://8kun.top", "https://sys.8kun.top") {

            @NonNull
            @Override
            public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
                HttpUrl url =
                        HttpUrl.parse("https://media.8kun.top/file_store/" + (arg.get("tim") + "." + arg.get("ext")));

                return Objects.requireNonNull(url, "image url is null");
            }

            @NonNull
            @Override
            public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, int customSpoilters, Map<String, String> arg) {
                String ext;
                switch (arg.get("ext")) {
                    case "jpeg":
                    case "jpg":
                    case "png":
                    case "gif":
                        ext = arg.get("ext");
                        break;
                    default:
                        ext = "jpg";
                        break;
                }

                HttpUrl url =
                        HttpUrl.parse("https://media.8kun.top/file_store/thumb/" + (arg.get("tim") + "." + ext));

                return Objects.requireNonNull(url, "thumbnail url is null");
            }
        });

        setActions(new VichanActions(this, getOkHttpClient(), getSiteManager()) {
            @Override
            public ModularResult<Unit> setupPost(Reply reply, MultipartHttpCall call) {
                return super.setupPost(reply, call)
                        .mapValue(unit -> {
                            if (reply.chanDescriptor instanceof ChanDescriptor.ThreadDescriptor) {
                                // "thread" is already added in VichanActions.
                                call.parameter("post", "New Reply");
                            } else {
                                call.parameter("post", "New Thread");
                                call.parameter("page", "1");
                            }

                            return Unit.INSTANCE;
                        });
            }

            @Override
            public boolean requirePrepare() {
                // We don't need to check the antispam fields for 8chan.
                return false;
            }

            @Override
            public SiteAuthentication postAuthenticate() {
                return SiteAuthentication.fromUrl(
                        "https://sys.8kun.top/dnsbls_bypass.php",
                        "You failed the CAPTCHA",
                        "You may now go back and make your post"
                );
            }
        });

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