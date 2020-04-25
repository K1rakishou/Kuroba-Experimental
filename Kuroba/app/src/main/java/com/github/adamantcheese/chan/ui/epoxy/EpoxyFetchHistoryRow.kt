package com.github.adamantcheese.chan.ui.epoxy

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.adamantcheese.chan.R
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyFetchHistoryRow  @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val fetchHistoryTime: TextView
    private val fetchHistoryResult: TextView
    private val fetchHistoryErrorMessage: TextView

    init {
        inflate(context, R.layout.epoxy_archive_fetch_history_row, this)

        fetchHistoryTime = findViewById(R.id.fetch_history_time)
        fetchHistoryResult = findViewById(R.id.fetch_history_result)
        fetchHistoryErrorMessage = findViewById(R.id.fetch_history_error_message)
    }

    @ModelProp
    fun setTime(time: DateTime) {
        fetchHistoryTime.text = formatter.print(time)
    }

    @ModelProp
    fun setResult(success: Boolean) {
        val resultString = if (success) {
            context.getString(R.string.success)
        } else {
            context.getString(R.string.error)
        }

        fetchHistoryResult.text = context.getString(
                R.string.epoxy_fetch_history_result_template,
                resultString
        )
    }

    @ModelProp
    fun setErrorMessage(errorMessage: String?) {
        if (errorMessage == null) {
            fetchHistoryErrorMessage.visibility = View.GONE
            return
        }

        fetchHistoryErrorMessage.visibility = View.VISIBLE
        fetchHistoryErrorMessage.text = errorMessage
    }

    companion object {
        private val formatter = DateTimeFormatterBuilder()
                .append(ISODateTimeFormat.date())
                .appendLiteral(' ')
                .append(ISODateTimeFormat.hourMinuteSecond())
                .appendLiteral(" UTC")
                .toFormatter()
    }
}