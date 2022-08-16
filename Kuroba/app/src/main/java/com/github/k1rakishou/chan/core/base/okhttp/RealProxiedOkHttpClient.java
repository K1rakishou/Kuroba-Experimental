package com.github.k1rakishou.chan.core.base.okhttp;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.Chan;
import com.github.k1rakishou.chan.core.helper.ProxyStorage;
import com.github.k1rakishou.chan.core.manager.FirewallBypassManager;
import com.github.k1rakishou.chan.core.net.KurobaProxySelector;
import com.github.k1rakishou.chan.core.site.SiteResolver;
import com.github.k1rakishou.common.dns.CompositeDnsSelector;
import com.github.k1rakishou.common.dns.DnsOverHttpsSelectorFactory;
import com.github.k1rakishou.common.dns.NormalDnsSelectorFactory;

import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

// this is basically the same as OkHttpClient, but with a singleton for a proxy instance
public class RealProxiedOkHttpClient implements ProxiedOkHttpClient {
    private OkHttpClient proxiedClient;

    private final NormalDnsSelectorFactory normalDnsSelectorFactory;
    private final DnsOverHttpsSelectorFactory dnsOverHttpsSelectorFactory;
    private final Chan.OkHttpProtocols okHttpProtocols;
    private final ProxyStorage proxyStorage;
    private final HttpLoggingInterceptorLazy httpLoggingInterceptorLazy;
    private final SiteResolver siteResolver;
    private final FirewallBypassManager firewallBypassManager;

    @Inject
    public RealProxiedOkHttpClient(
            NormalDnsSelectorFactory normalDnsSelectorFactory,
            DnsOverHttpsSelectorFactory dnsOverHttpsSelectorFactory,
            Chan.OkHttpProtocols okHttpProtocols,
            ProxyStorage proxyStorage,
            HttpLoggingInterceptorLazy httpLoggingInterceptorLazy,
            SiteResolver siteResolver,
            FirewallBypassManager firewallBypassManager
    ) {
        this.normalDnsSelectorFactory = normalDnsSelectorFactory;
        this.dnsOverHttpsSelectorFactory = dnsOverHttpsSelectorFactory;
        this.okHttpProtocols = okHttpProtocols;
        this.proxyStorage = proxyStorage;
        this.httpLoggingInterceptorLazy = httpLoggingInterceptorLazy;
        this.siteResolver = siteResolver;
        this.firewallBypassManager = firewallBypassManager;
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

                    Interceptor interceptor = new CloudFlareHandlerInterceptor(
                            siteResolver,
                            firewallBypassManager,
                            ChanSettings.verboseLogs.get(),
                            "Generic"
                    );

                    // Proxies are usually slow, so they have increased timeouts
                    OkHttpClient.Builder builder = new OkHttpClient.Builder()
                            .connectTimeout(30, SECONDS)
                            .readTimeout(30, SECONDS)
                            .writeTimeout(30, SECONDS)
                            .protocols(okHttpProtocols.getProtocols())
                            .proxySelector(kurobaProxySelector)
                            .addNetworkInterceptor(interceptor);

                    HttpLoggingInterceptorInstaller.install(builder, httpLoggingInterceptorLazy);
                    OkHttpClient okHttpClient = builder.build();

                    CompositeDnsSelector compositeDnsSelector = new CompositeDnsSelector(
                            okHttpClient,
                            ChanSettings.okHttpUseDnsOverHttps.get(),
                            normalDnsSelectorFactory,
                            dnsOverHttpsSelectorFactory
                    );

                    proxiedClient = okHttpClient.newBuilder()
                            .dns(compositeDnsSelector)
                            .addNetworkInterceptor(new GzipInterceptor())
                            .build();
                }
            }
        }

        return proxiedClient;
    }
}