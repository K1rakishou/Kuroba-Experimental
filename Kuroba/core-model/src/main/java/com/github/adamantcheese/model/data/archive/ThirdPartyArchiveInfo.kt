package com.github.adamantcheese.model.data.archive

import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor

data class ThirdPartyArchiveInfo(
        val archiveDescriptor: ArchiveDescriptor,
        val enabled: Boolean
)