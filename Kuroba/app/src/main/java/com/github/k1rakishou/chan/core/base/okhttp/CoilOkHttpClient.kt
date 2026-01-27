package com.github.k1rakishou.chan.core.base.okhttp;

import android.content.Context;

import com.github.k1rakishou.ChanSettings;
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


public class CoilOkHttpClient implements CustomOkHttpClient {
    private final Context applicationContext;
    private final NormalDnsSelectorFactory normalDnsSelectorFactory;
    private final DnsOverHttpsSelectorFactory dnsOverHttpsSelectorFactory;
    private final HttpLoggingInterceptorLazy httpLoggingInterceptorLazy;
    private final ProxyStorage proxyStorage;
    private final SiteResolver siteResolver;
    private final FirewallBypassManager firewallBypassManager;

    private volatile OkHttpClient coilClient;

    @Inject
    public CoilOkHttpClient(
            Context applicationContext,
            NormalDnsSelectorFactory normalDnsSelectorFactory,
            DnsOverHttpsSelectorFactory dnsOverHttpsSelectorFactory,
            ProxyStorage proxyStorage,
            HttpLoggingInterceptorLazy httpLoggingInterceptorLazy,
            SiteResolver siteResolver,
            FirewallBypassManager firewallBypassManager
    ) {
        this.applicationContext = applicationContext;
        this.normalDnsSelectorFactory = normalDnsSelectorFactory;
        this.dnsOverHttpsSelectorFactory = dnsOverHttpsSelectorFactory;
        this.proxyStorage = proxyStorage;
        this.httpLoggingInterceptorLazy = httpLoggingInterceptorLazy;
        this.siteResolver = siteResolver;
        this.firewallBypassManager = firewallBypassManager;
    }

    @NotNull
    @Override
    public OkHttpClient okHttpClient() {
        if (coilClient == null) {
            synchronized (this) {
                if (coilClient == null) {
                    KurobaProxySelector kurobaProxySelector = new KurobaProxySelector(
                            proxyStorage,
                            ProxyStorage.ProxyActionType.SiteMediaPreviews
                    );

                    Interceptor interceptor = new CloudFlareHandlerInterceptor(
                            siteResolver,
                            firewallBypassManager,
                            "Coil"
                    );

                    OkHttpClient.Builder builder = new OkHttpClient.Builder()
                            .proxySelector(kurobaProxySelector)
                            .addInterceptor(interceptor);

                    HttpLoggingInterceptorInstaller.install(builder, httpLoggingInterceptorLazy);
                    OkHttpClient okHttpClient = builder.build();

                    CompositeDnsSelector compositeDnsSelector = new CompositeDnsSelector(
                            okHttpClient,
                            ChanSettings.okHttpUseDnsOverHttps.get(),
                            normalDnsSelectorFactory,
                            dnsOverHttpsSelectorFactory
                    );

                    coilClient = okHttpClient.newBuilder()
                            .dns(compositeDnsSelector)
                            .addNetworkInterceptor(new GzipInterceptor())
                            .build();
                }
            }
        }

        return coilClient;
    }
}
