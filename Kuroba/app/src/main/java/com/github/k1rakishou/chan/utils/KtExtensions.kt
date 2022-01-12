package com.github.k1rakishou.chan.utils

import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.text.TextWatcher
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.airbnb.epoxy.AsyncEpoxyController
import com.airbnb.epoxy.DiffResult
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView
import com.airbnb.epoxy.OnModelBuildFinishedListener
import com.github.k1rakishou.chan.activity.SharingActivity
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.compose.viewModelProviderFactoryOf
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerActivity
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.log10


private val TAG = "KotlinExts"

fun Int.countDigits(): Int {
  if (this < 0) {
    return 0
  }

  if (this == 0) {
    return 1
  }

  return (log10(this.toDouble()).toInt() + 1)
}

fun removeExtensionIfPresent(filename: String): String {
  val index = filename.lastIndexOf('.')
  if (index < 0) {
    return filename
  }

  return filename.substring(0, index)
}

fun extractFileNameExtension(filename: String): String? {
  val index = filename.lastIndexOf('.')
  return if (index == -1) {
    null
  } else {
    filename.substring(index + 1)
  }
}


fun EpoxyRecyclerView.withModelsAsync(buildModels: EpoxyController.() -> Unit) {
  val controller = object : AsyncEpoxyController(true) {
    override fun buildModels() {
      buildModels(this)
    }
  }

  setController(controller)
  controller.requestModelBuild()
}

fun EpoxyController.addOneshotModelBuildListener(callback: () -> Unit) {
  addModelBuildListener(object : OnModelBuildFinishedListener {
    override fun onModelBuildFinished(result: DiffResult) {
      callback()

      removeModelBuildListener(this)
    }
  })
}

fun Context.getLifecycleFromContext(): Lifecycle? {
  return when (this) {
    is StartActivity -> this.lifecycle
    is SharingActivity -> this.lifecycle
    is MediaViewerActivity -> this.lifecycle
    is ContextWrapper -> (this.baseContext as? AppCompatActivity)?.getLifecycleFromContext()
    else -> null
  }
}

suspend fun View.awaitUntilGloballyLaidOut(
  waitForWidth: Boolean = false,
  waitForHeight: Boolean = false,
  attempts: Int = 5
) {
  if (!waitForWidth && !waitForHeight) {
    error("At least one of the parameters must be set to true!")
  }

  if (attempts <= 0) {
    Logger.e(TAG, "awaitUntilGloballyLaidOut() exhausted all attempts exiting, viewInfo=${this}")
    return
  }

  val widthOk = (!waitForWidth || width > 0)
  val heightOk = (!waitForHeight || height > 0)

  if (widthOk && heightOk) {
    return
  }

  if (!ViewCompat.isLaidOut(this) && !isLayoutRequested) {
    requestLayout()
  }

  suspendCancellableCoroutine<Unit> { cancellableContinuation ->
    val listener = object : OnGlobalLayoutListener {
      override fun onGlobalLayout() {
        viewTreeObserver.removeOnGlobalLayoutListener(this)
        cancellableContinuation.resumeValueSafe(Unit)
      }
    }

    viewTreeObserver.addOnGlobalLayoutListener(listener)

    cancellableContinuation.invokeOnCancellation { cause ->
      if (cause == null) {
        return@invokeOnCancellation
      }

      viewTreeObserver.removeOnGlobalLayoutListener(listener)
    }
  }

  awaitUntilGloballyLaidOut(waitForWidth, waitForHeight, attempts - 1)
}

fun Controller.findControllerOrNull(predicate: (Controller) -> Boolean): Controller? {
  if (predicate(this)) {
    return this
  }

  for (childController in childControllers) {
    val result = childController.findControllerOrNull(predicate)
    if (result != null) {
      return result
    }
  }

  return null
}

fun View.setAlphaFast(newAlpha: Float) {
  if (alpha != newAlpha) {
    alpha = newAlpha
  }
}

fun View.setEnabledFast(newEnabled: Boolean) {
  if (isEnabled != newEnabled) {
    isEnabled = newEnabled
  }

  if (this is ImageView) {
    if (!newEnabled) {
      setAlphaFast(0.6f)
    } else {
      setAlphaFast(1f)
    }
  }
}

fun View.setVisibilityFast(newVisibility: Int) {
  if (visibility != newVisibility) {
    visibility = newVisibility
  }
}

fun View.setScaleFastXY(newScale: Float) {
  if (scaleX != newScale) {
    scaleX = newScale
  }

  if (scaleY != newScale) {
    scaleY = newScale
  }
}

fun View.setBackgroundColorFast(newBackgroundColor: Int) {
  val prevColor = (background as? ColorDrawable)?.color
  if (prevColor != newBackgroundColor) {
    setBackgroundColor(newBackgroundColor)
  }
}

fun AppCompatEditText.doIgnoringTextWatcher(textWatcher: TextWatcher, func: AppCompatEditText.() -> Unit) {
  removeTextChangedListener(textWatcher)
  func(this)
  addTextChangedListener(textWatcher)
}

fun ByteArray.containsPattern(startFrom: Int, pattern: ByteArray): Boolean {
  if (pattern.size > this.size) {
    return false
  }

  for (offset in startFrom until this.size) {
    if (pattern[0] == this[offset]) {
      if (checkPattern(this, offset, pattern)) {
        return true
      }
    }
  }

  return false
}

private fun checkPattern(input: ByteArray, offset: Int, pattern: ByteArray): Boolean {
  for (index in pattern.indices) {
    if (pattern[index] != input[offset + index]) {
      return false
    }
  }

  return true
}

fun fixImageUrlIfNecessary(imageUrl: String?): String? {
  if (imageUrl == null) {
    return imageUrl
  }

  // arch.b4k.co was caught red-handed sending broken links (without http/https schema but
  // with both forward slashes, e.g. "//arch.b4k.co/..."  instead of "https://arch.b4k.co/...".
  // We gotta fix this by ourselves for now.
  // https://arch.b4k.co/meta/thread/357/
  //
  // UPD: it was fixed, but let's still leave this hack in case it happens again
  // UPD: the same thing happens on warosu.org and apparently it's normal
  if (imageUrl.startsWith("https://") || imageUrl.startsWith("http://")) {
    return imageUrl
  }

  if (imageUrl.startsWith("//")) {
    return "https:$imageUrl"
  }

  Logger.e(TAG, "Unknown kind of broken image url: \"$imageUrl\". If you see this report it to devs!")
  return null
}

inline fun <reified VM : ViewModel> ComponentActivity.viewModelByKey(key: String? = null): VM {
  if (key != null) {
    return ViewModelProvider(this).get(key, VM::class.java)
  } else {
    return ViewModelProvider(this).get(VM::class.java)
  }
}

inline fun <reified VM : ViewModel> ComponentActivity.viewModelByKey(
  key: String? = null,
  crossinline vmFactory: () -> VM
): VM {
  if (key != null) {
    return ViewModelProvider(this, viewModelProviderFactoryOf { vmFactory() }).get(key, VM::class.java)
  } else {
    return ViewModelProvider(this, viewModelProviderFactoryOf { vmFactory() }).get(VM::class.java)
  }
}