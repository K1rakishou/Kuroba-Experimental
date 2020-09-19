package com.github.adamantcheese.chan.features.search.epoxy

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatSpinner
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.features.search.data.SitesWithSearch
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
internal class EpoxySiteSelectorSpinner @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val siteSelectorSpinner: AppCompatSpinner
  private val sites = mutableListOf<SiteDescriptor>()

  init {
    inflate(context, R.layout.epoxy_site_selector_spinner, this)

    siteSelectorSpinner = findViewById(R.id.site_selector_spinner)
  }

  @ModelProp
  fun setSites(sitesWithSearch: SitesWithSearch?) {
    sites.clear()

    if (sitesWithSearch == null) {
      siteSelectorSpinner.adapter = null
      return
    }

    sites.addAll(sitesWithSearch.sites)

    val arrayAdapter = ArrayAdapter<String>(
      context,
      android.R.layout.simple_spinner_item,
      sitesWithSearch.sites.map { siteDescriptor -> siteDescriptor.siteName }
    )

    arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    siteSelectorSpinner.adapter = arrayAdapter

    sitesWithSearch.selectedItemIndex()?.let { selectedItemIndex ->
      siteSelectorSpinner.setSelection(selectedItemIndex, false)
    }
  }

  @CallbackProp
  fun setOnSiteSelectedListener(listener: ((SiteDescriptor) -> Unit)?) {
    if (listener == null) {
      siteSelectorSpinner.onItemSelectedListener = null
      return
    }

    siteSelectorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        sites.getOrNull(position)?.let { selectedSite -> listener.invoke(selectedSite) }
      }

      override fun onNothingSelected(parent: AdapterView<*>?) {
        // no-op
      }
    }
  }

}