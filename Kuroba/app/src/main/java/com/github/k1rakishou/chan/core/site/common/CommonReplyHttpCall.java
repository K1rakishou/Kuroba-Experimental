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
package com.github.k1rakishou.chan.core.site.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.core.site.Site;
import com.github.k1rakishou.chan.core.site.http.HttpCall;
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody;
import com.github.k1rakishou.chan.core.site.http.ReplyResponse;
import com.github.k1rakishou.common.StringUtils;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;

import java.io.IOException;
import java.util.Objects;

import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.Response;

public abstract class CommonReplyHttpCall extends HttpCall {
    private static final String TAG = "CommonReplyHttpCall";

    public final ChanDescriptor replyChanDescriptor;
    public final ReplyResponse replyResponse = new ReplyResponse();

    public CommonReplyHttpCall(@NonNull Site site, @NonNull ChanDescriptor replyChanDescriptor) {
        super(site);

        ChanDescriptor chanDescriptor = Objects.requireNonNull(
                replyChanDescriptor,
                "reply.chanDescriptor == null"
        );

        this.replyChanDescriptor = replyChanDescriptor;
        this.replyResponse.siteDescriptor = chanDescriptor.siteDescriptor();
        this.replyResponse.boardCode = chanDescriptor.boardCode();
    }

    @Override
    public void setup(
            Request.Builder requestBuilder,
            @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) throws IOException {
        replyResponse.password = StringUtils.generatePassword();

        MultipartBody.Builder formBuilder = new MultipartBody.Builder();
        formBuilder.setType(MultipartBody.FORM);
        addParameters(formBuilder, progressListener);

        HttpUrl replyUrl = getSite().endpoints().reply(this.replyChanDescriptor);
        requestBuilder.url(replyUrl);
        requestBuilder.addHeader("Referer", replyUrl.toString());

        requestBuilder.post(formBuilder.build());
    }

    @Override
    public abstract void process(Response response, String result);

    public abstract void addParameters(
            @NonNull MultipartBody.Builder builder,
            @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) throws IOException;
}
