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
package com.github.k1rakishou.model.data.filter;

import java.util.ArrayList;
import java.util.List;

public enum FilterType {
    TRIPCODE(0x1),
    NAME(0x2),
    COMMENT(0x4),
    ID(0x8),
    SUBJECT(0x10),
    FILENAME(0x20),
    COUNTRY_CODE(0x40),
    IMAGE(0x80);

    public final int flag;

    FilterType(int flag) {
        this.flag = flag;
    }

    public static List<FilterType> forFlags(int flag) {
        List<FilterType> enabledTypes = new ArrayList<>();
        for (FilterType filterType : values()) {
            if ((filterType.flag & flag) != 0) {
                enabledTypes.add(filterType);
            }
        }
        return enabledTypes;
    }

    public static String filterTypeName(FilterType type) {
        switch (type) {
            case TRIPCODE:
                return "Tripcode";
            case NAME:
                return "Name";
            case COMMENT:
                return "Comment";
            case ID:
                return "Poster ID";
            case SUBJECT:
                return "Subject";
            case FILENAME:
                return "Filename";
            case COUNTRY_CODE:
                return "Country code";
            case IMAGE:
                return "Image hash";
        }
        return null;
    }
}
