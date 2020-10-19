package com.github.k1rakishou.chan.core.base.okhttp;

import com.github.k1rakishou.chan.Chan;
import com.github.k1rakishou.chan.core.di.HttpLoggingInterceptorInstaller;
import com.github.k1rakishou.chan.core.settings.ChanSettings;

import kotlin.Lazy;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

import static java.util.concurrent.TimeUnit.SECONDS;

// this is basically the same as OkHttpClient, but with a singleton for a proxy instance
public class RealProxiedOkHttpClient implements ProxiedOkHttpClient {
    private OkHttpClient proxiedClient;
    private Dns okHttpDns;
    private Chan.OkHttpProtocols okHttpProtocols;
    private Lazy<HttpLoggingInterceptor> loggingInterceptorLazyKt;

    public RealProxiedOkHttpClient(
            Dns okHttpDns,
            Chan.OkHttpProtocols okHttpProtocols,
            Lazy<HttpLoggingInterceptor> loggingInterceptorLazyKt
    ) {
        this.okHttpDns = okHttpDns;
        this.okHttpProtocols = okHttpProtocols;
        this.loggingInterceptorLazyKt = loggingInterceptorLazyKt;
    }

    @Override
    public synchronized OkHttpClient getProxiedClient() {
        if (proxiedClient == null) {
            // Proxies are usually slow, so they have increased timeouts
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .proxy(ChanSettings.getProxy())
                    .connectTimeout(30, SECONDS)
                    .readTimeout(30, SECONDS)
                    .writeTimeout(30, SECONDS)
                    .protocols(okHttpProtocols.getProtocols())
                    .dns(okHttpDns);

            HttpLoggingInterceptorInstaller.install(builder, loggingInterceptorLazyKt);
            proxiedClient = builder.build();
        }

        return proxiedClient;
    }
}