package com.github.k1rakishou.chan.core.helper

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SitesSetupControllerOpenNotifier {
    private val _siteSelectionControllerOpenEventsFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val siteSelectionControllerOpenEventsFlow: SharedFlow<Unit>
        get() = _siteSelectionControllerOpenEventsFlow.asSharedFlow()

    fun onSitesSetupControllerOpened() {
        _siteSelectionControllerOpenEventsFlow.tryEmit(Unit)
    }

}