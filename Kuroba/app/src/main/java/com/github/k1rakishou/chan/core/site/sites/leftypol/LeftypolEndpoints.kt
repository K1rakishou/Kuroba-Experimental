package com.github.k1rakishou.chan.core.site.sites.leftypol

import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.vichan.VichanEndpoints
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import okhttp3.HttpUrl

class LeftypolEndpoints(commonSite: CommonSite?, rootUrl: String?, sysUrl: String?) : VichanEndpoints(commonSite, rootUrl, sysUrl) {
    override fun thumbnailUrl(boardDescriptor: BoardDescriptor, spoiler: Boolean, customSpoilers: Int, arg: Map<String, String>): HttpUrl {
        var u = root.builder()
        for (s in arg.getValue("thumb_path").split('/'))
            u = u.s(s)
        return u.url()
    }

    override fun imageUrl(boardDescriptor: BoardDescriptor, arg: Map<String, String>): HttpUrl {
        var u = root.builder()
        for (s in arg.getValue("file_path").split('/'))
            u = u.s(s)
        return u.url()
    }

    override fun boards(): HttpUrl {
        return root.builder().s("status.php").url()
    }
}