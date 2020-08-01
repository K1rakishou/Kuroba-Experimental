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
package com.github.adamantcheese.chan.core.site.sites.chan4;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.common.CommonReplyHttpCall;
import com.github.adamantcheese.chan.core.site.http.ProgressRequestBody;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor;

import java.io.File;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class Chan4ReplyCall extends CommonReplyHttpCall {
    public Chan4ReplyCall(Site site, Reply reply) {
        super(site, reply);
    }

    @Override
    public void addParameters(
            MultipartBody.Builder formBuilder,
            @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        ChanDescriptor chanDescriptor = Objects.requireNonNull(
                reply.chanDescriptor,
                "reply.chanDescriptor == null"
        );

        formBuilder.addFormDataPart("mode", "regist");
        formBuilder.addFormDataPart("pwd", replyResponse.password);

        if (chanDescriptor instanceof ChanDescriptor.ThreadDescriptor) {
            long threadNo = ((ChanDescriptor.ThreadDescriptor) chanDescriptor).getThreadNo();

            formBuilder.addFormDataPart("resto", String.valueOf(threadNo));
        }

        formBuilder.addFormDataPart("name", reply.name);
        formBuilder.addFormDataPart("email", reply.options);

        if ((chanDescriptor instanceof ChanDescriptor.CatalogDescriptor) && !TextUtils.isEmpty(reply.subject)) {
            formBuilder.addFormDataPart("sub", reply.subject);
        }

        formBuilder.addFormDataPart("com", reply.comment);

        if (reply.captchaResponse != null) {
            if (reply.captchaChallenge != null) {
                formBuilder.addFormDataPart("recaptcha_challenge_field", reply.captchaChallenge);
                formBuilder.addFormDataPart("recaptcha_response_field", reply.captchaResponse);
            } else {
                formBuilder.addFormDataPart("g-recaptcha-response", reply.captchaResponse);
            }
        }

        Site site = getSite();

        if (site instanceof Chan4 && reply.chanDescriptor.boardCode().equals("pol")) {
            if (!reply.flag.isEmpty()) {
                formBuilder.addFormDataPart("flag", reply.flag);
            } else {
                formBuilder.addFormDataPart("flag", ((Chan4) site).getFlagType().get());
            }
        }

        if (reply.file != null) {
            attachFile(formBuilder, progressListener);
        }

        if (reply.spoilerImage) {
            formBuilder.addFormDataPart("spoiler", "on");
        }
    }

    private void attachFile(
            MultipartBody.Builder formBuilder,
            @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        File file = Objects.requireNonNull(reply.file, "reply.file is null");

        RequestBody requestBody;
        if (progressListener == null) {
            requestBody = RequestBody.create(file, MediaType.parse("application/octet-stream"));
        } else {
            requestBody = new ProgressRequestBody(
                    RequestBody.create(
                            file,
                            MediaType.parse("application/octet-stream")
                    ),
                    progressListener
            );
        }

        formBuilder.addFormDataPart("upfile", reply.fileName, requestBody);
    }
}
