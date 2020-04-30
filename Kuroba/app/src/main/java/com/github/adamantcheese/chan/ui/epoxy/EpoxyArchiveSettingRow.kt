package com.github.adamantcheese.chan.ui.epoxy

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.SwitchCompat
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.features.archives.ArchiveState
import com.github.adamantcheese.chan.features.archives.ArchiveStatus
import com.github.adamantcheese.chan.utils.exhaustive

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyArchiveSettingRow @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val archiveNameTextView: TextView
    private val archiveStatusTextView: TextView
    private val archiveStatusHelpImageView: AppCompatImageView
    private val archiveSupportedBoardsTextView: TextView
    private val archiveSupportedBoardsMediaTextView: TextView
    private val archiveState: SwitchCompat

    init {
        inflate(context, R.layout.epoxy_archive_setting_row, this)

        archiveNameTextView = findViewById(R.id.archive_name)
        archiveStatusTextView = findViewById(R.id.archive_status)
        archiveStatusHelpImageView = findViewById(R.id.archive_status_help)
        archiveSupportedBoardsTextView = findViewById(R.id.archive_supported_boards)
        archiveSupportedBoardsMediaTextView = findViewById(R.id.archive_supported_boards_media)
        archiveState = findViewById(R.id.archive_state)
    }

    @ModelProp
    fun setArchiveNameWithDomain(archiveNameWithDomain: String) {
        archiveNameTextView.text = archiveNameWithDomain
    }

    @ModelProp
    fun setArchiveStatus(archiveStatus: ArchiveStatus) {
        when (archiveStatus) {
            ArchiveStatus.Working -> {
                archiveStatusTextView.text = context.getString(R.string.epoxy_archive_setting_row_working)
                archiveStatusTextView.background = ColorDrawable(GREEN_COLOR)
            }
            ArchiveStatus.ExperiencingProblems -> {
                archiveStatusTextView.text = context.getString(R.string.epoxy_archive_setting_row_experiencing_problems)
                archiveStatusTextView.background = ColorDrawable(ORANGE_COLOR)
            }
            ArchiveStatus.NotWorking -> {
                archiveStatusTextView.text = context.getString(R.string.epoxy_archive_setting_row_not_working)
                archiveStatusTextView.background = ColorDrawable(RED_COLOR)
            }
            ArchiveStatus.Disabled -> {
                archiveStatusTextView.text = context.getString(R.string.epoxy_archive_setting_row_disabled)
                archiveStatusTextView.background = ColorDrawable(GRAY_COLOR)
            }
            ArchiveStatus.PermanentlyDisabled -> {
                archiveStatusTextView.text = context.getString(R.string.epoxy_archive_setting_row_disabled)
                archiveStatusTextView.background = ColorDrawable(BLACK_COLOR)
            }
        }.exhaustive
    }

    @ModelProp
    fun setArchiveState(state: ArchiveState) {
        archiveState.isChecked = when (state) {
            ArchiveState.Enabled -> true
            ArchiveState.Disabled,
            ArchiveState.PermanentlyDisabled -> false
        }

        if (state == ArchiveState.PermanentlyDisabled) {
            archiveState.isEnabled = false
            archiveState.isClickable = false
            archiveState.isFocusable = false
        }
    }

    @ModelProp
    fun setSupportedBoards(supportedBoards: String) {
        archiveSupportedBoardsTextView.text = context.getString(
                R.string.epoxy_archive_setting_row_supports_boards,
                supportedBoards
        )
    }

    @ModelProp
    fun setSupportedBoardsMedia(supportedBoardsMedia: String) {
        archiveSupportedBoardsMediaTextView.text = context.getString(
                R.string.epoxy_archive_setting_row_supports_media_on_boards,
                supportedBoardsMedia
        )
    }

    @CallbackProp
    fun setOnRowClickCallback(callback: ((enabled: Boolean) -> Unit)?) {
        if (callback == null) {
            this.setOnClickListener(null)
            return
        }

        setOnClickListener {
            archiveState.isChecked = !archiveState.isChecked

            callback.invoke(archiveState.isChecked)
        }
    }

    @CallbackProp
    fun setOnHelpClickCallback(callback: (() -> Unit)?) {
        if (callback == null) {
            archiveStatusHelpImageView.setOnClickListener(null)
            return
        }

        archiveStatusHelpImageView.setOnClickListener {
            callback.invoke()
        }
    }

    companion object {
        private val GREEN_COLOR = Color.parseColor("#009900")
        private val ORANGE_COLOR = Color.parseColor("#994C00")
        private val RED_COLOR = Color.parseColor("#990000")
        private val GRAY_COLOR = Color.parseColor("#4A4A4A")
        private val BLACK_COLOR = Color.parseColor("#000000")
    }
}