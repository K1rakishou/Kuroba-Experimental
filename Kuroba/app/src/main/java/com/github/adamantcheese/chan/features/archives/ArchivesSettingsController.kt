package com.github.adamantcheese.chan.features.archives

import android.content.Context
import android.text.Html
import androidx.appcompat.app.AlertDialog
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.features.archives.data.ArchiveState
import com.github.adamantcheese.chan.features.archives.epoxy.epoxyArchiveSettingRow
import com.github.adamantcheese.chan.ui.controller.navigation.RequiresNoBottomNavBar
import com.github.adamantcheese.chan.ui.epoxy.epoxyErrorView
import com.github.adamantcheese.chan.ui.epoxy.epoxyLoadingView
import com.github.adamantcheese.chan.ui.view.DividerItemDecoration
import com.github.adamantcheese.chan.ui.widget.CancellableToast
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import com.github.adamantcheese.chan.utils.AndroidUtils.inflate
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.exhaustive
import com.github.adamantcheese.chan.utils.plusAssign
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult

class ArchivesSettingsController(context: Context)
  : Controller(context),
  ArchivesSettingsControllerView,
  ArchiveFetchHistoryController.OnFetchHistoryChanged,
  RequiresNoBottomNavBar {

  lateinit var recyclerView: EpoxyRecyclerView

  private val presenter = ArchivesSettingsPresenter()
  private val cancellableToast = CancellableToast()

  override fun onCreate() {
    super.onCreate()

    navigation.title = getString(R.string.archives_settings_title)

    navigation.buildMenu()
      .withItem(R.drawable.ic_help_outline_white_24dp) { onHelpClicked() }
      .build()

    view = inflate(context, R.layout.controller_archives_settings)
    recyclerView = view.findViewById(R.id.archives_recycler_view)

    recyclerView.addItemDecoration(
      DividerItemDecoration(recyclerView.context, DividerItemDecoration.VERTICAL)
    )

    presenter.onCreate(this)

    compositeDisposable += presenter.listenForStateChanges()
      .subscribe(
        { newState -> onStateChanged(newState) },
        { error -> throw error }
      )
  }

  override fun onDestroy() {
    super.onDestroy()

    presenter.onDestroy()
  }

  private fun onHelpClicked() {
    val dialog = AlertDialog.Builder(context)
      .setTitle(R.string.help)
      .setMessage(Html.fromHtml(getString(R.string.archives_settings_help_message)))
      .setPositiveButton(R.string.close, null)
      .show()

    dialog.setCanceledOnTouchOutside(true)
  }

  override fun showToast(message: ArchivesSettingsPresenterMessage) {
    val messageText = when (message) {
      is ArchivesSettingsPresenterMessage.RepositoryErrorMessage -> {
        context.getString(R.string.archives_settings_repository_error, message.errorMessage)
      }
      ArchivesSettingsPresenterMessage.ArchiveIsDisabled -> {
        context.getString(R.string.archives_settings_archive_is_disabled)
      }
      ArchivesSettingsPresenterMessage.ArchiveIsWorking -> {
        context.getString(R.string.archives_settings_archive_is_working)
      }
      ArchivesSettingsPresenterMessage.ArchiveIsPermanentlyDisabled -> {
        context.getString(R.string.archives_settings_archive_is_disabled_permanently)
      }
    }.exhaustive

    cancellableToast.showToast(context, messageText)
  }

  override fun onHistoryLoaded(history: List<ThirdPartyArchiveFetchResult>) {
    if (history.isEmpty()) {
      return
    }

    val alreadyPresenting = navigationController!!.isAlreadyPresenting { controller ->
      controller is ArchiveFetchHistoryController
    }

    if (alreadyPresenting) {
      return
    }

    navigationController!!.presentController(ArchiveFetchHistoryController(context, history, this))
  }

  override fun onChanged() {
    presenter.loadArchivesAndShowAsync()
  }

  private fun onStateChanged(state: ArchivesSettingsState) {
    BackgroundUtils.ensureMainThread()

    recyclerView.withModels {
      when (state) {
        ArchivesSettingsState.Default -> {
          // no-op
        }
        ArchivesSettingsState.Loading -> {
          epoxyLoadingView {
            id("epoxy_loading_view")
          }
        }
        is ArchivesSettingsState.ArchivesLoaded -> {
          state.archiveInfoList.forEach { archiveInfo ->
            epoxyArchiveSettingRow {
              id("epoxy_archive_setting_row_${archiveInfo.hashCode()}")
              archiveNameWithDomain(archiveInfo.archiveNameWithDomain)
              archiveStatus(archiveInfo.status)
              archiveState(archiveInfo.state)
              supportedBoards(archiveInfo.supportedBoards)
              supportedBoardsMedia(archiveInfo.supportedBoardsMedia)

              if (archiveInfo.state != ArchiveState.PermanentlyDisabled) {
                onRowClickCallback { presenter.onArchiveSettingClicked(archiveInfo) }
              }

              onHelpClickCallback { presenter.onArchiveStatusHelpClicked(archiveInfo) }
            }
          }
        }
        is ArchivesSettingsState.Error -> {
          epoxyErrorView {
            id("epoxy_error_view")
            errorMessage(state.message)
          }
        }
      }.exhaustive
    }
  }


}