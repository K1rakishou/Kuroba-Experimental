package com.github.k1rakishou.chan.core.base.okhttp;

import com.github.k1rakishou.chan.Chan;
import com.github.k1rakishou.chan.core.settings.ChanSettings;

import okhttp3.Dns;
import okhttp3.OkHttpClient;

import static java.util.concurrent.TimeUnit.SECONDS;

// this is basically the same as OkHttpClient, but with a singleton for a proxy instance
public class RealProxiedOkHttpClient implements ProxiedOkHttpClient {
    private OkHttpClient proxiedClient;
    private Dns okHttpDns;
    private Chan.OkHttpProtocols okHttpProtocols;

    public RealProxiedOkHttpClient(Dns okHttpDns, Chan.OkHttpProtocols okHttpProtocols) {
        this.okHttpDns = okHttpDns;
        this.okHttpProtocols = okHttpProtocols;
    }

    @Override
    public synchronized OkHttpClient getProxiedClient() {
        if (proxiedClient == null) {
            // Proxies are usually slow, so they have increased timeouts
            proxiedClient = new OkHttpClient.Builder()
                    .proxy(ChanSettings.getProxy())
                    .connectTimeout(30, SECONDS)
                    .readTimeout(30, SECONDS)
                    .writeTimeout(30, SECONDS)
                    .protocols(okHttpProtocols.getProtocols())
                    .dns(okHttpDns)
                    .build();
        }

        return proxiedClient;
    }
}