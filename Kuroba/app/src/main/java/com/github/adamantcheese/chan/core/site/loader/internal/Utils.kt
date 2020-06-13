package com.github.adamantcheese.chan.core.site.loader.internal

import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderRequestParams
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor

internal object Utils {

  suspend fun getArchiveDescriptor(
    archivesManager: ArchivesManager,
    descriptor: ChanDescriptor,
    requestParams: ChanLoaderRequestParams,
    forced: Boolean
  ): ArchiveDescriptor? {
    return if (requestParams.retrieveDeletedPostsFromArchives || forced) {
      when (descriptor) {
        is ChanDescriptor.ThreadDescriptor -> {
          archivesManager.getArchiveDescriptor(descriptor, forced)
            .unwrap()
        }
        is ChanDescriptor.CatalogDescriptor -> null
      }
    } else {
      null
    }
  }

}