package com.github.adamantcheese.chan.core.mapper;

import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.model.data.serializable.SerializableSite;

public class SiteMapper {

    public static SerializableSite toSerializableSite(Site site) {
        return new SerializableSite(site.id());
    }
}
