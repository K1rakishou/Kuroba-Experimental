package com.github.k1rakishou.chan.features.reencoding

import android.content.Context
import android.graphics.Bitmap.CompressFormat
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.util.Pair
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.features.reencoding.ImageReencodingPresenter.ReencodeSettings
import com.github.k1rakishou.chan.features.reencoding.ImageReencodingPresenter.ReencodeType
import com.github.k1rakishou.chan.features.reencoding.ImageReencodingPresenter.ReencodeType.Companion.fromInt
import com.github.k1rakishou.chan.ui.controller.base.BaseFloatingController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableRadioButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableSlider
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.google.android.material.slider.Slider

class ImageReencodeOptionsController(
  context: Context,
  private val imageReencodingHelper: ImageOptionsHelper,
  private val callbacks: ImageReencodeOptionsCallbacks,
  private val imageFormat: CompressFormat,
  private val dims: Pair<Int, Int>,
  private val lastSettings: ReencodeSettings?
) : BaseFloatingController(context), View.OnClickListener, RadioGroup.OnCheckedChangeListener {
  private lateinit var viewHolder: ConstraintLayout
  private lateinit var radioGroup: RadioGroup
  private lateinit var quality: ColorizableSlider
  private lateinit var reduce: ColorizableSlider
  private lateinit var currentImageQuality: TextView
  private lateinit var currentImageReduce: TextView
  private lateinit var cancel: ColorizableBarButton
  private lateinit var ok: ColorizableBarButton
  private lateinit var reencodeImageAsIs: ColorizableRadioButton

  private var ignoreSetup = false

  private val listener: Slider.OnChangeListener = Slider.OnChangeListener { slider, value, fromUser ->
    var value = value
    if (ignoreSetup) {
      return@OnChangeListener
    }

    // this variable is to ignore any side effects of setting progress while loading last options
    if (slider === quality) {
      if (value < 1) {
        // for API <26; the quality can't be lower than 1
        slider.value = 1f
        value = 1f
      }

      currentImageQuality.text = getString(R.string.image_quality, value.toInt())
    } else if (slider === reduce) {
      currentImageReduce.text = getString(
        R.string.scale_reduce,
        dims.first,
        dims.second,
        (dims.first!! * ((100f - value) / 100f)).toInt(),
        (dims.second!! * ((100f - value) / 100f)).toInt(),
        100 - value.toInt()
      )
    }
  }

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun getLayoutId(): Int {
    return R.layout.layout_image_reencoding
  }

  public override fun onCreate() {
    super.onCreate()

    viewHolder = view.findViewById<ConstraintLayout>(R.id.reencode_image_view_holder)
    radioGroup = view.findViewById<RadioGroup>(R.id.reencode_image_radio_group)
    quality = view.findViewById<ColorizableSlider>(R.id.reecode_image_quality)
    reduce = view.findViewById<ColorizableSlider>(R.id.reecode_image_reduce)
    currentImageQuality = view.findViewById<TextView>(R.id.reecode_image_current_quality)
    currentImageReduce = view.findViewById<TextView>(R.id.reecode_image_current_reduce)
    reencodeImageAsIs = view.findViewById<ColorizableRadioButton>(R.id.reencode_image_as_is)
    cancel = view.findViewById<ColorizableBarButton>(R.id.reencode_image_cancel)
    ok = view.findViewById<ColorizableBarButton>(R.id.reencode_image_ok)

    val reencodeImageAsJpeg = view.findViewById<ColorizableRadioButton>(R.id.reencode_image_as_jpeg)
    val reencodeImageAsPng = view.findViewById<ColorizableRadioButton>(R.id.reencode_image_as_png)

    viewHolder.setOnClickListener(this)
    cancel.setOnClickListener(this)
    ok.setOnClickListener(this)
    radioGroup.setOnCheckedChangeListener(this)

    quality.addOnChangeListener(listener)
    reduce.addOnChangeListener(listener)

    setReencodeImageAsIsText()

    if (imageFormat == CompressFormat.PNG) {
      quality.isEnabled = false
      reencodeImageAsPng.setEnabled(false)
    } else if (imageFormat == CompressFormat.JPEG) {
      reencodeImageAsJpeg.setEnabled(false)
    } else if (imageFormat == CompressFormat.WEBP) {
      reencodeImageAsIs.setEnabled(false)
    }

    currentImageReduce.setText(
      getString(
        R.string.scale_reduce,
        dims.first,
        dims.second,
        dims.first,
        dims.second,
        100 - reduce.value.toInt()
      )
    )

    if (lastSettings != null) {
      //this variable is to ignore any side effects of checking/setting progress on these views
      ignoreSetup = true
      quality.value = lastSettings.reencodeQuality.toFloat()
      reduce.value = lastSettings.reducePercent.toFloat()

      when (lastSettings.reencodeType) {
        ReencodeType.AS_JPEG -> reencodeImageAsJpeg.setChecked(true)
        ReencodeType.AS_PNG -> reencodeImageAsPng.setChecked(true)
        ReencodeType.AS_IS -> reencodeImageAsIs.setChecked(true)
      }

      ignoreSetup = false
    }
  }

  public override fun onDestroy() {
    super.onDestroy()

    quality.removeOnChangeListener(listener)
    reduce.removeOnChangeListener(listener)
  }

  private fun setReencodeImageAsIsText() {
    val format = when (imageFormat) {
      CompressFormat.PNG -> "PNG"
      CompressFormat.JPEG -> "JPEG"
      else -> "Unknown"
    }

    reencodeImageAsIs.text = getString(R.string.reencode_image_as_is_text, format)
  }

  override fun onBack(): Boolean {
    imageReencodingHelper.pop()
    return true
  }

  override fun onClick(v: View?) {
    if (v === ok) {
      callbacks.onOk(this.reencode)
    } else if (v === cancel || v === viewHolder) {
      callbacks.onCanceled()
    }
  }

  override fun onCheckedChanged(group: RadioGroup, checkedId: Int) {
    if (ignoreSetup) {
      return
    }

    // this variable is to ignore any side effects of checking during last options load
    val index = group.indexOfChild(group.findViewById<View?>(group.checkedRadioButtonId))

    // 0 - AS IS
    // 1 - AS JPEG
    // 2 - AS PNG

    // when re-encoding image as png it ignores the compress quality option so we can just
    // disable the quality seekbar
    if (index == 2 || (index == 0 && imageFormat == CompressFormat.PNG)) {
      quality.value = 100f
      quality.isEnabled = false
    } else {
      quality.isEnabled = true
    }
  }

  private val reencode: ReencodeSettings
    get() {
      val index = radioGroup.indexOfChild(radioGroup.findViewById<View?>(radioGroup.checkedRadioButtonId))
      val reencodeType = fromInt(index)

      return ReencodeSettings(
        reencodeType = reencodeType,
        reencodeQuality = quality.value.toInt(),
        reducePercent = reduce.value.toInt()
      )
    }

  interface ImageReencodeOptionsCallbacks {
    fun onCanceled()
    fun onOk(reencodeSettings: ReencodeSettings)
  }

  companion object {
    private const val TAG = "ImageReencodeOptionsController"
  }
}
