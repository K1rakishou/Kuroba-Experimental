package com.github.k1rakishou.chan.ui.captcha;

import androidx.annotation.NonNull;

import com.github.k1rakishou.chan.core.site.SiteAuthentication;
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor;

public interface AuthenticationLayoutInterface {
    void initialize(
            @NonNull SiteDescriptor siteDescriptor,
            @NonNull SiteAuthentication authentication,
            @NonNull AuthenticationLayoutCallback callback
    );

    void reset();
    void hardReset();
    void onDestroy();
}
