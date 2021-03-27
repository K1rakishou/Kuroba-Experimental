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
package com.github.k1rakishou.chan.core.site.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.model.data.descriptor.PostDescriptor;
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor;

public class ReplyResponse {
    /**
     * {@code true} if the post when through, {@code false} otherwise.
     */
    public boolean posted = false;

    /**
     * Error message used to show to the user if {@link #posted} is {@code false}.
     * <p>Optional
     */
    @Nullable
    public String errorMessage;

    @Nullable
    public SiteDescriptor siteDescriptor = null;
    public String boardCode = "";
    public long threadNo = 0L;
    public long postNo = 0L;
    public String password = "";
    public boolean probablyBanned = false;
    public boolean requireAuthentication = false;

    @Nullable
    public PostDescriptor getPostDescriptorOrNull() {
        if (probablyBanned || requireAuthentication || postNo <= 0L || threadNo <= 0) {
            return null;
        }

        return PostDescriptor.create(siteDescriptor.getSiteName(), boardCode, threadNo, postNo);
    }

    @NonNull
    @Override
    public String toString() {
        return "ReplyResponse{" +
                "posted=" + posted +
                ", errorMessage='" + errorMessage + '\'' +
                ", siteDescriptor=" + siteDescriptor +
                ", boardCode='" + boardCode + '\'' +
                ", threadNo=" + threadNo +
                ", postNo=" + postNo +
                ", password='" + password + '\'' +
                ", probablyBanned=" + probablyBanned +
                ", requireAuthentication=" + requireAuthentication +
                '}';
    }
}
