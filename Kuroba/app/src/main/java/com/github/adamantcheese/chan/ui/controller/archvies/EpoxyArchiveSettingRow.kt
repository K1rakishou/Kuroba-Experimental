package com.github.adamantcheese.chan.ui.controller.archvies

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.presenter.ArchivesSettingsPresenter
import com.github.adamantcheese.chan.utils.exhaustive
import java.util.*

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyArchiveSettingRow @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val archiveNameTextView: TextView
    private val archiveStatusTextView: TextView
    private val archiveSupportedBoardsTextView: TextView
    private val archiveSupportedBoardsMediaTextView: TextView
    private val archiveState: SwitchCompat

    init {
        inflate(context, R.layout.epoxy_archive_setting_row, this)

        archiveNameTextView = findViewById(R.id.archive_name)
        archiveStatusTextView = findViewById(R.id.archive_status)
        archiveSupportedBoardsTextView = findViewById(R.id.archive_supported_boards)
        archiveSupportedBoardsMediaTextView = findViewById(R.id.archive_supported_boards_media)
        archiveState = findViewById(R.id.archive_state)
    }

    @ModelProp
    fun setArchiveNameWithDomain(archiveNameWithDomain: String) {
        archiveNameTextView.text = archiveNameWithDomain
    }

    @ModelProp
    fun setArchiveStatus(archiveStatus: ArchivesSettingsPresenter.ArchiveStatus) {
        when (archiveStatus) {
            ArchivesSettingsPresenter.ArchiveStatus.Working -> {
                archiveStatusTextView.text = context.getString(R.string.epoxy_archive_setting_row_working)
                archiveStatusTextView.background = ColorDrawable(GREEN_COLOR)
            }
            ArchivesSettingsPresenter.ArchiveStatus.ExperiencingProblems -> {
                archiveStatusTextView.text = context.getString(R.string.epoxy_archive_setting_row_experiencing_problems)
                archiveStatusTextView.background = ColorDrawable(ORANGE_COLOR)
            }
            ArchivesSettingsPresenter.ArchiveStatus.NotWorking -> {
                archiveStatusTextView.text = context.getString(R.string.epoxy_archive_setting_row_not_working)
                archiveStatusTextView.background = ColorDrawable(RED_COLOR)
            }
            ArchivesSettingsPresenter.ArchiveStatus.Disabled -> {
                archiveStatusTextView.text = context.getString(R.string.epoxy_archive_setting_row_disabled)
                archiveStatusTextView.background = ColorDrawable(GRAY_COLOR)
            }
        }.exhaustive
    }

    @ModelProp
    fun setArchiveState(enabled: Boolean) {
        archiveState.isChecked = enabled
    }

    @ModelProp
    fun setSupportedBoards(supportedBoards: String) {
        archiveSupportedBoardsTextView.text = String.format(
                Locale.ENGLISH,
                "Supported boards: %s",
                supportedBoards
        )
    }

    @ModelProp
    fun setSupportedBoardsMedia(supportedBoardsMedia: String) {
        archiveSupportedBoardsMediaTextView.text = String.format(
                Locale.ENGLISH,
                "Supported media on boards: %s",
                supportedBoardsMedia
        )
    }

    @CallbackProp
    fun setOnClickCallback(callback: ((enabled: Boolean) -> Unit)?) {
        if (callback == null) {
            this.setOnClickListener(null)
            return
        }

        setOnClickListener {
            archiveState.isChecked = !archiveState.isChecked

            callback.invoke(archiveState.isChecked)
        }
    }

    companion object {
        private val GREEN_COLOR = Color.parseColor("#009900")
        private val ORANGE_COLOR = Color.parseColor("#994C00")
        private val RED_COLOR = Color.parseColor("#990000")
        private val GRAY_COLOR = Color.parseColor("#4A4A4A")
    }
}