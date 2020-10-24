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

// this is basically the same as OkHttpClient, but with a singleton for a proxy instance
public class RealProxiedOkHttpClient implements ProxiedOkHttpClient {
    private OkHttpClient proxiedClient;

    private final Dns okHttpDns;
    private final Chan.OkHttpProtocols okHttpProtocols;
    private final ProxyStorage proxyStorage;
    private final Lazy<HttpLoggingInterceptor> loggingInterceptorLazyKt;

    public RealProxiedOkHttpClient(
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
        if (proxiedClient == null) {
            synchronized (this) {
                if (proxiedClient == null) {
                    KurobaProxySelector kurobaProxySelector = new KurobaProxySelector(
                            proxyStorage,
                            ProxyStorage.ProxyActionType.SiteRequests
                    );

                    // Proxies are usually slow, so they have increased timeouts
                    OkHttpClient.Builder builder = new OkHttpClient.Builder()
                            .connectTimeout(30, SECONDS)
                            .readTimeout(30, SECONDS)
                            .writeTimeout(30, SECONDS)
                            .protocols(okHttpProtocols.getProtocols())
                            .proxySelector(kurobaProxySelector)
                            .dns(okHttpDns);

                    HttpLoggingInterceptorInstaller.install(builder, loggingInterceptorLazyKt);
                    proxiedClient = builder.build();
                }
            }
        }

        return proxiedClient;
    }
}