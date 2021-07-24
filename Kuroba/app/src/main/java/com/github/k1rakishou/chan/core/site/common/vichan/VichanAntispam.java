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
package com.github.k1rakishou.chan.core.site.common.vichan;

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient;
import com.github.k1rakishou.common.ModularResult;
import com.github.k1rakishou.core_logger.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dagger.Lazy;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Vichan applies garbage looking fields to the post form, to combat bots.
 * Load up the normal html, parse the form, and get these fields for our post.
 * <p>
 * {@link #get()} blocks, run it off the main thread.
 */
public class VichanAntispam {
    private static final String TAG = "Antispam";

    private Lazy<RealProxiedOkHttpClient> proxiedOkHttpClient;

    private HttpUrl url;
    private List<String> fieldsToIgnore = new ArrayList<>();

    public VichanAntispam(Lazy<RealProxiedOkHttpClient> proxiedOkHttpClient, HttpUrl url) {
        this.proxiedOkHttpClient = proxiedOkHttpClient;
        this.url = url;
    }

    public void addDefaultIgnoreFields() {
        fieldsToIgnore.addAll(Arrays.asList(
                "board",
                "thread",
                "name",
                "email",
                "subject",
                "body",
                "password",
                "file",
                "spoiler",
                "json_response",
                "file_url1",
                "file_url2",
                "file_url3"
        ));
    }

    public ModularResult<Map<String, String>> get() {
        Map<String, String> res = new HashMap<>();

        try {
            Request request = new Request.Builder().url(url).build();
            Response response = proxiedOkHttpClient.get().okHttpClient().newCall(request).execute();
            if (!response.isSuccessful()) {
                return ModularResult.error(new IOException("(Antispam) Bad response status code: " + response.code()));
            }

            ResponseBody body = response.body();
            if (body == null) {
                Logger.d(TAG, "(Antispam) Response body is null");
                return ModularResult.value(res);
            }

            Document document = Jsoup.parse(body.string());
            Elements form = document.body().getElementsByTag("form");

            for (Element element : form) {
                if (element.attr("name").equals("post")) {
                    // Add all <input> and <textarea> elements.
                    Elements inputs = element.getElementsByTag("input");
                    inputs.addAll(element.getElementsByTag("textarea"));

                    for (Element input : inputs) {
                        String name = input.attr("name");
                        String value = input.val();

                        if (!fieldsToIgnore.contains(name)) {
                            res.put(name, value);
                        }
                    }

                    break;
                }
            }
        } catch (Throwable error) {
            return ModularResult.error(error);
        }

        return ModularResult.value(res);
    }
}
