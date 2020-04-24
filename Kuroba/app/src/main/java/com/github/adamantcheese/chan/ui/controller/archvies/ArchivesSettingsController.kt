package com.github.adamantcheese.chan.ui.controller.archvies

import android.content.Context
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.presenter.ArchivesSettingsPresenter
import com.github.adamantcheese.chan.ui.view.DividerItemDecoration
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import com.github.adamantcheese.chan.utils.AndroidUtils.inflate
import java.util.*

class ArchivesSettingsController(context: Context)
    : Controller(context), ArchivesSettingsControllerView {

    lateinit var recyclerView: EpoxyRecyclerView

    private val presenter = ArchivesSettingsPresenter()

    override fun onCreate() {
        super.onCreate()

        navigation.title = getString(R.string.archives_settings_title)

        view = inflate(context, R.layout.controller_archives_settings)
        recyclerView = view.findViewById(R.id.archives_recycler_view)

        recyclerView.addItemDecoration(
                DividerItemDecoration(recyclerView.context, DividerItemDecoration.VERTICAL)
        )

        presenter.onCreate(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        presenter.onDestroy()
    }

    override fun onStateChanged(state: ArchivesSettingsPresenter.ArchivesSettingsState) {
        recyclerView.withModels {
            when (state) {
                ArchivesSettingsPresenter.ArchivesSettingsState.Loading -> {
                    epoxyLoadingView {
                        id("epoxy_loading_view")
                    }
                }
                is ArchivesSettingsPresenter.ArchivesSettingsState.ArchivesLoaded -> {
                    state.archiveInfoList.forEach { archiveInfo ->
                        val archiveNameWithDomain = String.format(
                                Locale.ENGLISH,
                                "%s (%s)",
                                archiveInfo.archiveDescriptor.name,
                                archiveInfo.archiveDescriptor.domain
                        )

                        val enabled = archiveInfo.state == ArchivesSettingsPresenter.ArchiveState.Enabled

                        epoxyArchiveSettingRow {
                            id("epoxy_archive_setting_row_${archiveInfo.hashCode()}")
                            archiveNameWithDomain(archiveNameWithDomain)
                            archiveStatus(archiveInfo.status)
                            archiveState(enabled)
                            supportedBoards(archiveInfo.supportedBoards)
                            supportedBoardsMedia(archiveInfo.supportedBoardsMedia)

                            onClickCallback {
                                println("TTTAAA clicked")
                            }
                        }
                    }
                }
            }
        }
    }
}