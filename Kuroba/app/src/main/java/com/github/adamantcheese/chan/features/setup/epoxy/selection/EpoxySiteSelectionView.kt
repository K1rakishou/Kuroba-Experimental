package com.github.adamantcheese.chan.features.setup.epoxy.selection

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.doOnPreDraw
import coil.request.RequestDisposable
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.airbnb.epoxy.OnViewRecycled
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.image.ImageLoaderV2
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.google.android.material.textview.MaterialTextView
import java.lang.ref.WeakReference
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySiteSelectionView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var themeHelper: ThemeHelper

  private val siteIcon: AppCompatImageView
  private val siteName: MaterialTextView

  private var requestDisposable: RequestDisposable? = null

  init {
    inflate(context, R.layout.epoxy_site_selection_view, this)
    Chan.inject(this)

    siteIcon = findViewById(R.id.site_icon)
    siteName = findViewById(R.id.site_name)
  }

  @ModelProp
  fun bindSiteName(name: String) {
    siteName.text = name
    siteName.setTextColor(themeHelper.theme.textPrimary)
  }

  @ModelProp
  fun bindIcon(iconUrl: String) {
    val siteIconRef = WeakReference(siteIcon)

    siteIcon.doOnPreDraw {
      requestDisposable?.dispose()
      requestDisposable = null

      require(siteIcon.width > 0 && siteIcon.height > 0) { "View (siteIcon) has no size!" }

      requestDisposable = imageLoaderV2.loadFromNetwork(
        context,
        iconUrl,
        siteIcon.width,
        siteIcon.height,
        listOf(),
        { drawable -> siteIconRef.get()?.setImageBitmap(drawable.bitmap) },
        R.drawable.error_icon
      )
    }
  }

  @CallbackProp
  fun bindRowClickCallback(callback: (() -> Unit)?) {
    if (callback == null) {
      setOnClickListener(null)
      return
    }

    setOnClickListener {
      callback.invoke()
    }
  }

  @OnViewRecycled
  fun unbind() {
    this.requestDisposable?.dispose()
    this.requestDisposable = null
  }
}