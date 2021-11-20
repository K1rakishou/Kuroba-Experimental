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
package com.github.k1rakishou.chan.core.site.sites.lainchan;

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
 * Lainchan uses some additional (custom?) logic that effectively restricts which fields are allowed
 * to be POSTed to post.php.
 * <p>
 * {@link #get()} blocks, run it off the main thread.
 *
 * @see "https://github.com/lainchan/lainchan/blob/master/inc/anti-bot.php#checkSpam"
 */
public class LainchanAntispam {
    private static final String TAG = "Antispam";
    private final List<String> allowedFields = new ArrayList<>();
    private final List<String> fakeFields = new ArrayList<>();
    private final List<String> binFields = new ArrayList<>();
    private final Lazy<RealProxiedOkHttpClient> proxiedOkHttpClient;
    private final HttpUrl url;

    public LainchanAntispam(Lazy<RealProxiedOkHttpClient> proxiedOkHttpClient, HttpUrl url) {
        this.proxiedOkHttpClient = proxiedOkHttpClient;
        this.url = url;

        allowedFields.addAll(Arrays.asList(
                "hash",
                "board",
                "thread",
                "mod",
                "name",
                "email",
                "subject",
                "post",
                "body",
                "password",
                "sticky",
                "lock",
                "raw",
                "embed",
                "g-recaptcha-response",
                "spoiler",
                "page",
                "file_url",
                "file_url1",
                "file_url2",
                "file_url3",
                "file_url4",
                "file_url5",
                "file_url6",
                "file_url7",
                "file_url8",
                "file_url9",
                "json_response",
                "user_flag",
                "no_country",
                "tag"));

        fakeFields.addAll(Arrays.asList(
                "user",
                "username",
                "login",
                "search",
                "q",
                "url",
                "firstname",
                "lastname",
                "text",
                "message"
        ));

        binFields.addAll(Arrays.asList(
                "file1",
                "file2",
                "file3"
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

                        if (allowedFields.contains(name)) {
                            res.put(name, value);
                        } else {
                            if (fakeFields.contains(name)) {
                                res.put(name, value);
                            } else {
                                if (!binFields.contains(name)) {
                                    res.put(name, value);
                                }
                            }
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
