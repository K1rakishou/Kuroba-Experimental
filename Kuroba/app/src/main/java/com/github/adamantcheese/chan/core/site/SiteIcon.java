/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.site;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.github.adamantcheese.chan.core.image.ImageLoaderV2;
import com.github.adamantcheese.chan.utils.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import coil.request.Disposable;
import okhttp3.HttpUrl;

public class SiteIcon {
    private static final String TAG = "SiteIcon";
    private static final int FAVICON_SIZE = 64;

    private ImageLoaderV2 imageLoaderV2;
    private HttpUrl url;
    private Drawable drawable;
    @Nullable
    private Disposable requestDisposable;

    public static SiteIcon fromFavicon(ImageLoaderV2 imageLoaderV2, HttpUrl url) {
        SiteIcon siteIcon = new SiteIcon(imageLoaderV2);
        siteIcon.url = url;
        return siteIcon;
    }

    public static SiteIcon fromDrawable(ImageLoaderV2 imageLoaderV2, Drawable drawable) {
        SiteIcon siteIcon = new SiteIcon(imageLoaderV2);
        siteIcon.drawable = drawable;
        return siteIcon;
    }

    private SiteIcon(ImageLoaderV2 imageLoaderV2) {
        this.imageLoaderV2 = imageLoaderV2;
    }

    public HttpUrl getUrl() {
        return url;
    }

    public void get(Context context, SiteIconResult result) {
        if (drawable != null) {
            result.onSiteIcon(SiteIcon.this, drawable);
            return;
        }

        if (url == null) {
            return;
        }

        if (requestDisposable != null) {
            requestDisposable.dispose();
            requestDisposable = null;
        }

        requestDisposable = imageLoaderV2.loadFromNetwork(
                context,
                url.toString(),
                FAVICON_SIZE,
                FAVICON_SIZE,
                new ImageLoaderV2.ImageListener() {
                    @Override
                    public void onResponse(@NotNull BitmapDrawable drawable, boolean isImmediate) {
                        result.onSiteIcon(SiteIcon.this, drawable);
                    }

                    @Override
                    public void onNotFound() {
                        onResponseError(new IOException("Not found"));
                    }

                    @Override
                    public void onResponseError(@NotNull Throwable error) {
                        Logger.e(TAG, "Error loading favicon", error);
                    }
                });
    }

    public interface SiteIconResult {
        void onSiteIcon(SiteIcon siteIcon, Drawable icon);
    }
}
