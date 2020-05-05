package com.github.adamantcheese.chan.features.settings.epoxy

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.adamantcheese.chan.R

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyNoSettingsFoundView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val messageView: TextView

  init {
    View.inflate(context, R.layout.epoxy_no_settings_found, this)

    messageView = findViewById(R.id.message_view)
  }

  @ModelProp
  fun setQuery(query: String) {
    messageView.text = context.getString(R.string.epoxy_no_settings_found_by_query, query)
  }

}