package com.github.adamantcheese.chan.core.site.loader.internal

import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderRequestParams
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor

internal object Utils {

  suspend fun getArchiveDescriptor(
    archivesManager: ArchivesManager,
    requestParams: ChanLoaderRequestParams,
    forced: Boolean
  ): ArchiveDescriptor? {
    return if (requestParams.retrieveDeletedPostsFromArchives || forced) {
      when (requestParams.chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> {
          archivesManager.getArchiveDescriptor(requestParams.chanDescriptor, forced)
            .unwrap()
        }
        is ChanDescriptor.CatalogDescriptor -> null
      }
    } else {
      null
    }
  }

}