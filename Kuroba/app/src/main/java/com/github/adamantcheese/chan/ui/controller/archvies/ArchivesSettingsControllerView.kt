package com.github.adamantcheese.chan.ui.controller.archvies

import com.github.adamantcheese.chan.core.presenter.ArchivesSettingsPresenter

interface ArchivesSettingsControllerView {
    fun onStateChanged(state: ArchivesSettingsPresenter.ArchivesSettingsState)
}