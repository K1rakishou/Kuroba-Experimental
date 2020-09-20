package com.github.adamantcheese.chan.core.base.okhttp;

import androidx.annotation.NonNull;

import okhttp3.OkHttpClient;

public interface ProxiedOkHttpClient {
    @NonNull
    OkHttpClient getProxiedClient();
}