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
package com.github.k1rakishou.chan.core.site.sites.chan4;

import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.core.site.Site;
import com.github.k1rakishou.chan.core.site.http.HttpCall;
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody;
import com.github.k1rakishou.chan.core.site.http.login.Chan4LoginRequest;
import com.github.k1rakishou.chan.core.site.http.login.Chan4LoginResponse;
import com.github.k1rakishou.core_logger.Logger;

import java.net.HttpCookie;
import java.util.List;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;

public class Chan4PassHttpCall extends HttpCall {
    private static final String TAG = "Chan4PassHttpCall";

    private final Chan4LoginRequest chan4LoginRequest;
    @Nullable
    public Chan4LoginResponse loginResponse = null;

    public Chan4PassHttpCall(Site site, Chan4LoginRequest chan4LoginRequest) {
        super(site);
        this.chan4LoginRequest = chan4LoginRequest;
    }

    @Override
    public void setup(
            Request.Builder requestBuilder,
            @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        FormBody.Builder formBuilder = new FormBody.Builder();

        formBuilder.add("act", "do_login");
        formBuilder.add("id", chan4LoginRequest.getUser());
        formBuilder.add("pin", chan4LoginRequest.getPass());

        requestBuilder.url(getSite().endpoints().login());
        requestBuilder.post(formBuilder.build());
        getSite().requestModifier().modifyHttpCall(this, requestBuilder);
    }

    @Override
    public void process(Response response, String result) {
        if (result.contains("Success! Your device is now authorized")) {
            List<String> cookies = response.headers("Set-Cookie");
            String passId = null;

            for (String cookie : cookies) {
                try {
                    List<HttpCookie> parsedList = HttpCookie.parse(cookie);
                    for (HttpCookie parsed : parsedList) {
                        if (parsed.getName().equals("pass_id") && !parsed.getValue().equals("0")) {
                            passId = parsed.getValue();
                        }
                    }
                } catch (IllegalArgumentException error) {
                    Logger.e(TAG, "Error while processing cookies", error);
                }
            }

            if (passId != null) {
                loginResponse = new Chan4LoginResponse.Success(
                        "Success! Your device is now authorized.",
                        passId
                );
            } else {
                loginResponse = new Chan4LoginResponse.Failure("Could not get pass id");
            }

            return;
        }

        String message;
        if (result.contains("Your Token must be exactly 10 characters")) {
            message = "Incorrect token";
        } else if (result.contains("You have left one or more fields blank")) {
            message = "You have left one or more fields blank";
        } else if (result.contains("Incorrect Token or PIN")) {
            message = "Incorrect Token or PIN";
        } else {
            message = "Unknown error";
        }

        loginResponse = new Chan4LoginResponse.Failure(message);
    }
}
