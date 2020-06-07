package com.github.adamantcheese.model.source.cache

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor

class LastUsedArchiveForThreadCache : GenericCacheSource<ChanDescriptor.ThreadDescriptor, Long>()