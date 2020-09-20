package com.github.adamantcheese.chan.core.base.okhttp;

import androidx.annotation.NonNull;

import com.github.adamantcheese.common.DoNotStrip;

import okhttp3.OkHttpClient;

@DoNotStrip
public interface ProxiedOkHttpClient {
    @NonNull
    OkHttpClient getProxiedClient();
}