package com.github.adamantcheese.chan.features.archives

import android.content.Context
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.features.archives.epoxy.epoxyFetchHistoryRow
import com.github.adamantcheese.chan.ui.view.DividerItemDecoration
import com.github.adamantcheese.chan.utils.AndroidUtils.inflate
import com.github.adamantcheese.chan.utils.AndroidUtils.showToast
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult

class ArchiveFetchHistoryController(
  context: Context,
  fetchResults: List<ThirdPartyArchiveFetchResult>,
  private val callback: OnFetchHistoryChanged
) : Controller(context), ArchiveFetchHistoryControllerView {
  lateinit var recyclerView: EpoxyRecyclerView

  private val presenter = ArchiveFetchHistoryPresenter(ArrayList(fetchResults))

  private var presenting = false
  private var fetchHistoryChanged = false

  override fun onCreate() {
    super.onCreate()
    presenting = true

    view = inflate(context, R.layout.controller_archive_fetch_history)
    recyclerView = view.findViewById(R.id.archive_fetch_history_recycler_view)

    recyclerView.addItemDecoration(
      DividerItemDecoration(recyclerView.context, DividerItemDecoration.VERTICAL)
    )

    presenter.onCreate(this)
  }

  override fun rebuildFetchResultsList(fetchResultList: List<ThirdPartyArchiveFetchResult>) {
    BackgroundUtils.ensureMainThread()

    recyclerView.withModels {
      fetchResultList.forEach { fetchResult ->
        epoxyFetchHistoryRow {
          id("epoxy_fetch_history_row_${fetchResult.databaseId}")
          time(fetchResult.insertedOn)
          result(fetchResult.success)
          errorMessage(fetchResult.errorText)

          deleteButtonCallback {
            presenter.deleteFetchResult(fetchResult)
          }
        }
      }
    }
  }

  override fun onFetchResultListChanged() {
    BackgroundUtils.ensureMainThread()

    fetchHistoryChanged = true
  }

  override fun popController() {
    BackgroundUtils.ensureMainThread()

    if (presenting) {
      presenting = false
      stopPresenting()
    }
  }

  override fun showDeleteErrorToast() {
    BackgroundUtils.ensureMainThread()

    showToast(
      context,
      context.getString(R.string.archive_fetch_history_delete_error_message)
    )
  }

  override fun showUnknownErrorToast() {
    BackgroundUtils.ensureMainThread()

    showToast(
      context,
      context.getString(R.string.unknown_error)
    )
  }

  override fun onDestroy() {
    super.onDestroy()

    if (fetchHistoryChanged) {
      callback.onChanged()
    }

    presenter.onDestroy()
    presenting = false
  }

  override fun onBack(): Boolean {
    if (presenting) {
      presenting = false
      stopPresenting()
      return true
    }

    return super.onBack()
  }

  interface OnFetchHistoryChanged {
    fun onChanged()
  }
}