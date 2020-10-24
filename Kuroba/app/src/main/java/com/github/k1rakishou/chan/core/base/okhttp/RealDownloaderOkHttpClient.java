package com.github.k1rakishou.chan.core.base.okhttp;

import com.github.k1rakishou.chan.Chan;
import com.github.k1rakishou.chan.core.di.HttpLoggingInterceptorInstaller;
import com.github.k1rakishou.chan.core.manager.ProxyStorage;
import com.github.k1rakishou.chan.core.net.KurobaProxySelector;

import org.jetbrains.annotations.NotNull;

import kotlin.Lazy;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

import static java.util.concurrent.TimeUnit.SECONDS;

public class RealDownloaderOkHttpClient implements DownloaderOkHttpClient {
    private final Dns okHttpDns;
    private final Chan.OkHttpProtocols okHttpProtocols;
    private final Lazy<HttpLoggingInterceptor> loggingInterceptorLazyKt;
    private final ProxyStorage proxyStorage;

    private OkHttpClient downloaderClient;

    public RealDownloaderOkHttpClient(
            Dns okHttpDns,
            Chan.OkHttpProtocols okHttpProtocols,
            ProxyStorage proxyStorage,
            Lazy<HttpLoggingInterceptor> loggingInterceptorLazyKt
    ) {
        this.okHttpDns = okHttpDns;
        this.okHttpProtocols = okHttpProtocols;
        this.proxyStorage = proxyStorage;
        this.loggingInterceptorLazyKt = loggingInterceptorLazyKt;
    }

    @NotNull
    @Override
    public OkHttpClient okHttpClient() {
        if (downloaderClient == null) {
            synchronized (this) {
                if (downloaderClient == null) {
                    KurobaProxySelector kurobaProxySelector = new KurobaProxySelector(
                            proxyStorage,
                            ProxyStorage.ProxyActionType.SiteMediaFull
                    );

                    OkHttpClient.Builder builder = new OkHttpClient.Builder()
                            .readTimeout(5, SECONDS)
                            .writeTimeout(5, SECONDS)
                            .proxySelector(kurobaProxySelector)
                            .protocols(okHttpProtocols.getProtocols())
                            .dns(okHttpDns);

                    HttpLoggingInterceptorInstaller.install(builder, loggingInterceptorLazyKt);

                    downloaderClient = builder.build();
                }
            }
        }

        return downloaderClient;
    }
}
