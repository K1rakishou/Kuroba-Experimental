package com.github.k1rakishou.chan.features.posting

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlin.coroutines.cancellation.CancellationException

internal class PostingException(message: String) : Exception(message)

internal class PostingCancellationException(chanDescriptor: ChanDescriptor) :
    CancellationException("Posting in ${chanDescriptor.userReadableString()} was canceled")