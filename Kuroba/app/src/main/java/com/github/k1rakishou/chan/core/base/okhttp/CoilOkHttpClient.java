package com.github.k1rakishou.chan.core.base.okhttp;

import android.content.Context;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.Chan;
import com.github.k1rakishou.chan.core.helper.ProxyStorage;
import com.github.k1rakishou.chan.core.net.KurobaProxySelector;
import com.github.k1rakishou.chan.core.site.SiteResolver;

import org.jetbrains.annotations.NotNull;

import java.io.File;

import javax.inject.Inject;

import okhttp3.Cache;
import okhttp3.Dns;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;


public class CoilOkHttpClient implements CustomOkHttpClient {
    private static final String IMAGE_CACHE_DIR = "coil_image_cache_dir";
    private static final long ONE_MB = 1024 * 1024;
    private static final long IMAGE_CACHE_MAX_SIZE = 100 * ONE_MB;

    private final Context applicationContext;
    private final Dns okHttpDns;
    private final Chan.OkHttpProtocols okHttpProtocols;
    private final HttpLoggingInterceptorLazy httpLoggingInterceptorLazy;
    private final ProxyStorage proxyStorage;
    private final SiteResolver siteResolver;

    private OkHttpClient coilClient;

    @Inject
    public CoilOkHttpClient(
            Context applicationContext,
            Dns okHttpDns,
            Chan.OkHttpProtocols okHttpProtocols,
            ProxyStorage proxyStorage,
            HttpLoggingInterceptorLazy httpLoggingInterceptorLazy,
            SiteResolver siteResolver
    ) {
        this.applicationContext = applicationContext;
        this.okHttpDns = okHttpDns;
        this.okHttpProtocols = okHttpProtocols;
        this.proxyStorage = proxyStorage;
        this.httpLoggingInterceptorLazy = httpLoggingInterceptorLazy;
        this.siteResolver = siteResolver;
    }

    @NotNull
    @Override
    public OkHttpClient okHttpClient() {
        if (coilClient == null) {
            synchronized (this) {
                if (coilClient == null) {
                    File imageCacheDir = new File(applicationContext.getCacheDir(), IMAGE_CACHE_DIR);
                    if (!imageCacheDir.exists() && !imageCacheDir.mkdirs()) {
                        throw new IllegalStateException("mkdirs failed to create " + imageCacheDir.getAbsolutePath());
                    }

                    Cache cache = new Cache(imageCacheDir, IMAGE_CACHE_MAX_SIZE);

                    KurobaProxySelector kurobaProxySelector = new KurobaProxySelector(
                            proxyStorage,
                            ProxyStorage.ProxyActionType.SiteMediaPreviews
                    );

                    Interceptor interceptor = new CloudFlareHandlerInterceptor(
                            siteResolver,
                            false,
                            ChanSettings.verboseLogs.get(),
                            "Coil"
                    );

                    OkHttpClient.Builder builder = new OkHttpClient.Builder()
                            .protocols(okHttpProtocols.getProtocols())
                            .proxySelector(kurobaProxySelector)
                            .cache(cache)
                            .addNetworkInterceptor(interceptor)
                            .dns(okHttpDns);

                    HttpLoggingInterceptorInstaller.install(builder, httpLoggingInterceptorLazy);
                    coilClient = builder.build();
                }
            }
        }

        return coilClient;
    }
}
