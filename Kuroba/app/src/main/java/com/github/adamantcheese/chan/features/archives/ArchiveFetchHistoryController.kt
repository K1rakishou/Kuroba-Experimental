package com.github.adamantcheese.chan.features.archives

import android.content.Context
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.ui.epoxy.epoxyFetchHistoryRow
import com.github.adamantcheese.chan.ui.view.DividerItemDecoration
import com.github.adamantcheese.chan.utils.AndroidUtils.inflate
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult

class ArchiveFetchHistoryController(
        context: Context,
        private val historyList: List<ThirdPartyArchiveFetchResult>
) : Controller(context) {
    lateinit var recyclerView: EpoxyRecyclerView

    private var presenting = false


    override fun onCreate() {
        super.onCreate()
        presenting = true

        view = inflate(context, R.layout.controller_archive_fetch_history)
        recyclerView = view.findViewById(R.id.archive_fetch_history_recycler_view)

        recyclerView.addItemDecoration(
                DividerItemDecoration(recyclerView.context, DividerItemDecoration.VERTICAL)
        )

        recyclerView.withModels {
            historyList.forEach { history ->
                epoxyFetchHistoryRow {
                    id("epoxy_fetch_history_row_${history.hashCode()}")
                    time(history.insertedOn)
                    result(history.success)
                    errorMessage(history.errorText)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

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
}