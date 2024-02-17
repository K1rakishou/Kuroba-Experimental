package com.github.k1rakishou.chan.core.manager

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ApplicationCrashNotifier {
    private val _applicationCrashedEventFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val applicationCrashedEventFlow: SharedFlow<Unit>
        get() = _applicationCrashedEventFlow.asSharedFlow()

    fun onApplicationCrashed() {
        _applicationCrashedEventFlow.tryEmit(Unit)
    }

}