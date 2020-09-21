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
package com.github.k1rakishou.chan.core.site.sites.dvach;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.core.site.Site;
import com.github.k1rakishou.chan.core.site.common.CommonReplyHttpCall;
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody;
import com.github.k1rakishou.chan.core.site.http.Reply;
import com.github.k1rakishou.chan.utils.Logger;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;

import org.jsoup.Jsoup;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DvachReplyCall extends CommonReplyHttpCall {
    private static final String TAG = "DvachReplyCall";

    private static final Pattern ERROR_MESSAGE =
            Pattern.compile("^\\{\"Error\":-\\d+,\"Reason\":\"(.*)\"");
    private static final Pattern POST_MESSAGE =
            Pattern.compile("^\\{\"Error\":null,\"Status\":\"OK\",\"Num\":(\\d+)");
    private static final Pattern THREAD_MESSAGE =
            Pattern.compile("^\\{\"Error\":null,\"Status\":\"Redirect\",\"Target\":(\\d+)");

    private static final String PROBABLY_BANNED_TEXT = "banned";

    DvachReplyCall(Site site, Reply reply) {
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

        long threadNo = -1L;

        if (chanDescriptor instanceof ChanDescriptor.ThreadDescriptor) {
            threadNo = ((ChanDescriptor.ThreadDescriptor) chanDescriptor).getThreadNo();
        }

        formBuilder.addFormDataPart("task", "post");
        formBuilder.addFormDataPart("board", chanDescriptor.boardCode());
        formBuilder.addFormDataPart("comment", reply.comment);
        formBuilder.addFormDataPart("thread", String.valueOf(threadNo));

        formBuilder.addFormDataPart("name", reply.name);
        formBuilder.addFormDataPart("email", reply.options);

        if ((chanDescriptor instanceof ChanDescriptor.CatalogDescriptor) && !TextUtils.isEmpty(reply.subject)) {
            formBuilder.addFormDataPart("subject", reply.subject);
        }

        if (reply.captchaResponse != null) {
            formBuilder.addFormDataPart("captcha_type", "recaptcha");
            formBuilder.addFormDataPart("captcha_key", Dvach.CAPTCHA_KEY);

            if (reply.captchaChallenge != null) {
                formBuilder.addFormDataPart("recaptcha_challenge_field", reply.captchaChallenge);
                formBuilder.addFormDataPart("recaptcha_response_field", reply.captchaResponse);
            } else {
                formBuilder.addFormDataPart("g-recaptcha-response", reply.captchaResponse);
            }
        }

        if (reply.file != null) {
            attachFile(formBuilder, progressListener);
        }
    }

    private void attachFile(
            MultipartBody.Builder formBuilder,
            @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        RequestBody requestBody;

        if (progressListener == null) {
            requestBody = RequestBody.create(reply.file, MediaType.parse("application/octet-stream"));
        } else {
            requestBody = new ProgressRequestBody(
                    RequestBody.create(reply.file, MediaType.parse("application/octet-stream")),
                    progressListener
            );
        }

        formBuilder.addFormDataPart("image", reply.fileName, requestBody);
    }

    @Override
    public void process(Response response, String result) {
        Matcher errorMessageMatcher = ERROR_MESSAGE.matcher(result);
        if (errorMessageMatcher.find()) {
            replyResponse.errorMessage = Jsoup.parse(errorMessageMatcher.group(1)).body().text();
            replyResponse.probablyBanned = replyResponse.errorMessage.contains(PROBABLY_BANNED_TEXT);
            return;
        }

        if (!response.isSuccessful()) {
            replyResponse.errorMessage = "Failed to post, bad response status code" + response.code();
            return;
        }

        Matcher postMessageMatcher = POST_MESSAGE.matcher(result);
        if (postMessageMatcher.find()) {
            if (reply.chanDescriptor instanceof ChanDescriptor.ThreadDescriptor) {
                replyResponse.threadNo = ((ChanDescriptor.ThreadDescriptor) reply.chanDescriptor).getThreadNo();
            }

            replyResponse.postNo = Integer.parseInt(postMessageMatcher.group(1));
            replyResponse.posted = true;
            return;
        }

        Matcher threadMessageMatcher = THREAD_MESSAGE.matcher(result);
        if (threadMessageMatcher.find()) {
            int threadNo = Integer.parseInt(threadMessageMatcher.group(1));

            replyResponse.threadNo = threadNo;
            replyResponse.postNo = threadNo;
            replyResponse.posted = true;
            return;
        }

        Logger.e(TAG, "Couldn't handle server response! response = \"" + result + "\"");
        replyResponse.errorMessage = "Failed to post, see the logs for more info";
    }
}
